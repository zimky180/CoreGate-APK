package com.zimky.coregateUS.page

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zimky.coregateUS.core.CoreConfig
import com.zimky.coregateUS.core.FreezeData
import com.zimky.coregateUS.core.RootShell
import com.zimky.coregateUS.ui.HintText
import com.zimky.coregateUS.ui.PAGE_BOTTOM_INSET
import com.zimky.coregateUS.ui.Toaster
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 冻结管理二级页（从「策略」页点进来）。
 *
 * 交互设计（对应用户要求）：
 *   - 每个组可以整体开关（对应 config.json 的 freeze_<key>）
 *   - 组内每个包可以单独勾选冻结 / 解冻
 *   - 也可以「全选」一次冻结整组
 *   - 高风险组（作者自冻清单）开启前必须过二次确认弹窗
 *
 * 状态真相：本页显示的「已冻结」标记全部来自 `--freeze-status` 的 [actual] 段，
 * 即系统当前真实状态，而不是「用户勾了就算」。所以用户能在这里亲眼看到
 * 勾选之后包是否真的冻结成功。
 *
 * @param onBack 返回上一页
 * @param onChanged 冻结状态发生变化时通知外层刷新主页统计
 */
@Composable
fun FreezePage(
    cfg: CoreConfig,
    sh: RootShell,
    onBack: () -> Unit,
    onChanged: () -> Unit
) {
    val cs = MiuixTheme.colorScheme
    val scope = rememberCoroutineScope()
    var snap by remember { mutableStateOf(FreezeData.Snapshot()) }
    var loading by remember { mutableStateOf(true) }
    // 正在处理的包（防重复点击，等引擎真正返回后清空）
    var busyPkg by remember { mutableStateOf<String?>(null) }


    // 待确认的高风险组（非空时弹出确认框）
    var pendingRiskyKey by remember { mutableStateOf<String?>(null) }

    // 读取真实冻结状态。这个函数会被多处调用（进页、操作后、手动刷新）。
    //
    // 【为什么改成回调驱动】(v2.2.1)
    //   旧实现是 exec 回调里给 out 赋值，然后 delay(350) 后直接读 out。
    //   RootShell.exec 是异步回调，su + pm list + dumpsys 在 ColorOS 16 上
    //   经常要 300~900ms，350ms 时回调多半还没到 —— 读到的是空串，
    //   于是 snap 被解析成「全部未冻结」，界面看上去就是「点了没反应」。
    //   现在改成：回调里解析 + 更新状态，不再靠 sleep 猜时间。
    fun reload(onDone: (() -> Unit)? = null) {
        scope.launch {
            loading = true
            sh.exec("sh /data/adb/modules/CoreGate/service.sh --freeze-status") { _, o, _ ->
                scope.launch {
                    snap = FreezeData.parse(o)
                    loading = false
                    onDone?.invoke()
                }
            }
        }
    }

    LaunchedEffect(Unit) { reload() }

    // 单包冻结 / 解冻：直接调引擎的 --freeze-add / --freeze-del，不靠改 config
    // 因为单包选择不等于整组开关，两者语义必须分开。
    //
    // 【为什么不在这里提示「已请求冻结」】(v2.2.1)
    //   旧版是先弹 Toaster 再 reload，无论引擎成功还是失败都提示「已请求冻结」，
    //   用户看到提示却看不到开关变化，就成了「按钮点不动」的错觉。
    //   现在改成：等引擎真正返回，按输出里的 OK / FAIL 决定提示内容。
    fun applyPkg(pkg: String, freeze: Boolean) {
        if (busyPkg == pkg) return
        busyPkg = pkg
        scope.launch {
            val arg = if (freeze) "--freeze-add" else "--freeze-del"
            sh.exec("sh /data/adb/modules/CoreGate/service.sh $arg $pkg") { _, o, _ ->
                scope.launch {
                    val ok = o.contains("_OK")
                    // 引擎成功后立刻回读真实状态，按钮显示以实际状态为准
                    reload {
                        busyPkg = null
                        Toaster.show(
                            when {
                                !ok -> if (freeze) "冻结失败：$pkg" else "解冻失败：$pkg"
                                freeze -> "已冻结 $pkg"
                                else -> "已解冻 $pkg"
                            },
                            !ok
                        )
                        onChanged()
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = PAGE_BOTTOM_INSET),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── 顶部：返回 + 标题 ──────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                text = "‹ 返回",
                onClick = onBack,
                colors = ButtonDefaults.textButtonColors(
                    textColor = cs.primary
                )
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "冻结管理", color = cs.onSurface, fontSize = 17.sp)
        }

        // ── 概览卡：一眼看出到底冻上了没 ─────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "当前已冻结", color = cs.onSurface, fontSize = 15.sp)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${snap.scopedFrozenCount} 个",
                        color = if (snap.actual.isEmpty()) cs.onSurfaceVariantSummary else cs.primary,
                        fontSize = 15.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (loading) "正在读取真实冻结状态…"
                    else if (snap.actual.isEmpty()) "系统当前没有任何被冻结的应用。"
                    else "本模块冻结 ${snap.scopedFrozenCount} 个，系统另有 ${snap.systemDisabledCount} 个应用被停用（含你手动停用的），不计入上数。",
                    color = cs.onSurfaceVariantSummary,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )
                // 记录与真实状态不一致时，必须显式告警 —— 这是「引擎没生效」的证据
                if (!loading && snap.staleList.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(cs.primary.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "有 ${snap.staleList.size} 项记录了但未生效，守护进程可能未运行或权限被拒。",
                            color = cs.primary,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                TextButton(
                    text = if (loading) "读取中…" else "重新读取",
                    onClick = { reload() },
                    colors = ButtonDefaults.textButtonColors(textColor = cs.primary)
                )
            }
        }

        // ── 各组卡片 ──────────────────────────────────────────────
        FreezeData.GROUPS.forEach { g ->
            FreezeGroupCard(
                group = g,
                snapshot = snap,
                enabled = cfg.let { c ->
                    // 组开关值优先取引擎回显的真实值；引擎没回显时退回本地配置
                    snap.groups[g.key] ?: c.let {
                        when (g.key) {
                            "log" -> it.freezeGrpLog
                            "media" -> it.freezeGrpMedia
                            "cloud" -> it.freezeGrpCloud
                            "diag" -> it.freezeGrpDiag
                            "ad" -> it.freezeGrpAd
                            "self" -> it.freezeGrpSelf
                            else -> false
                        }
                    }
                },
                onToggleGroup = { wantOn ->
                    // 高风险组要先过二次确认，其余组直接生效
                    if (wantOn && g.risky) {
                        pendingRiskyKey = g.key
                    } else {
                        // 等 --set-freeze 真正返回再回读，不靠 delay 猜时间
                        sh.exec(
                            "sh /data/adb/modules/CoreGate/service.sh --set-freeze ${g.key} ${if (wantOn) "true" else "false"}"
                        ) { _, _, _ ->
                            scope.launch { reload { onChanged() } }
                        }
                    }
                },
                onTogglePkg = { pkg, freeze -> applyPkg(pkg, freeze) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }

    // ── 高风险二次确认弹窗 ────────────────────────────────────────
    pendingRiskyKey?.let { key ->
        val g = FreezeData.GROUPS.firstOrNull { it.key == key }
        RiskyConfirmDialog(
            groupName = g?.name ?: key,
            onCancel = { pendingRiskyKey = null },
            onConfirm = {
                pendingRiskyKey = null
                sh.exec(
                    "sh /data/adb/modules/CoreGate/service.sh --set-freeze $key true"
                ) { _, _, _ ->
                    scope.launch { reload { onChanged() } }
                }
            }
        )
    }
}

/**
 * 高风险组二次确认弹窗。
 *
 * 文案严格使用作者定稿的原话，不添加任何额外劝退说明 ——
 * 用户明确要求「别写会出现在系统卸载列表里，就说冻结了就行」。
 */
@Composable
private fun RiskyConfirmDialog(
    groupName: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    val cs = MiuixTheme.colorScheme
    // 半透明遮罩盖住整页，拦截点击
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 26.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
                Text(
                    text = "再次确认",
                    color = cs.primary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "此栏仅在 16.0.1 版本实验通过。冻结作用在应用上，不涉及系统服务，" +
                        "解除冻结只需回到本页关闭该分组，或直接重启设备。",
                    color = cs.onSurfaceVariantSummary,
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "开启后将对「$groupName」执行冻结。",
                    color = cs.onSurface,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        text = "取消",
                        onClick = onCancel,
                        colors = ButtonDefaults.textButtonColors(
                            textColor = cs.onSurfaceVariantSummary
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        text = "我已知晓，继续",
                        onClick = onConfirm,
                        colors = ButtonDefaults.textButtonColors(textColor = cs.primary)
                    )
                }
            }
        }
    }
}

/**
 * 单个分组卡片：组开关 + 组内逐包勾选 + 全选/全取消。
 *
 * 包的「已冻结」状态取 snapshot.actual（系统真实状态），
 * 勾选操作不是本地 UI 状态，而是真的调引擎去冻结/解冻。
 */
@Composable
private fun FreezeGroupCard(
    group: FreezeData.Group,
    snapshot: FreezeData.Snapshot,
    enabled: Boolean,
    onToggleGroup: (Boolean) -> Unit,
    onTogglePkg: (String, Boolean) -> Unit
) {
    val cs = MiuixTheme.colorScheme
    val (frozenPkgs, normalPkgs) = FreezeData.splitByActual(group, snapshot.actual)
    var expanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            // 组标题行：名称 + 冻结计数 + 开关
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = group.name, color = cs.onSurface, fontSize = 15.sp)
                        if (group.risky) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(cs.primary.copy(alpha = 0.15f))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(text = "高风险", color = cs.primary, fontSize = 9.5.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "已冻结 ${frozenPkgs.size} / ${group.pkgs.size}",
                        color = if (frozenPkgs.isEmpty()) cs.onSurfaceVariantSummary else cs.primary,
                        fontSize = 11.5.sp
                    )
                }
                SwitchCompact(checked = enabled, onCheckedChange = onToggleGroup)
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = group.desc,
                color = cs.onSurfaceVariantSummary,
                fontSize = 11.5.sp,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    text = if (expanded) "收起" else "展开 ${group.pkgs.size} 个包",
                    onClick = { expanded = !expanded },
                    colors = ButtonDefaults.textButtonColors(textColor = cs.primary)
                )
                Spacer(modifier = Modifier.weight(1f))
                // 全选 / 全取消：整组一次性冻结或解冻
                if (frozenPkgs.size < group.pkgs.size) {
                    TextButton(
                        text = "全部冻结",
                        onClick = {
                            normalPkgs.forEach { p -> onTogglePkg(p, true) }
                        },
                        colors = ButtonDefaults.textButtonColors(textColor = cs.primary)
                    )
                } else {
                    TextButton(
                        text = "全部解冻",
                        onClick = {
                            frozenPkgs.forEach { p -> onTogglePkg(p, false) }
                        },
                        colors = ButtonDefaults.textButtonColors(
                            textColor = cs.onSurfaceVariantSummary
                        )
                    )
                }
            }

            // 逐包列表
            if (expanded) {
                Spacer(modifier = Modifier.height(4.dp))
                group.pkgs.forEach { pkg ->
                    val isFrozen = snapshot.actual.containsKey(pkg)
                    val mode = snapshot.actual[pkg]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 左侧状态点：绿色=已冻结，灰色=正常
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isFrozen) cs.primary
                                    else cs.onSurfaceVariantSummary.copy(alpha = 0.35f)
                                )
                        )
                        Spacer(modifier = Modifier.width(9.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pkg,
                                color = if (isFrozen) cs.onSurface else cs.onSurfaceVariantSummary,
                                fontSize = 11.5.sp
                            )
                            if (isFrozen && !mode.isNullOrBlank()) {
                                Text(
                                    text = if (mode == "suspend") "已冻结（挂起）" else "已冻结（停用）",
                                    color = cs.primary,
                                    fontSize = 9.5.sp
                                )
                            }
                        }
                        SwitchCompact(
                            checked = isFrozen,
                            onCheckedChange = { want -> onTogglePkg(pkg, want) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 紧凑开关。
 *
 * 本页包列表可能一次出现 52 行，用标准尺寸的 Switch 会让卡片极高、
 * 滚动很长；这里用一个圆角方块表示开关状态，点击即切换，视觉上更克制。
 */
@Composable
private fun SwitchCompact(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val cs = MiuixTheme.colorScheme
    Box(
        modifier = Modifier
            .size(width = 38.dp, height = 22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(
                if (checked) cs.primary else cs.surfaceContainerHigh
            )
            .clickable { onCheckedChange(!checked) },
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 2.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(if (checked) Color.White else cs.onSurfaceVariantSummary)
        )
    }
}