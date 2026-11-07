package com.zimky.coregateUS.page

import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zimky.coregateUS.core.AppLabels
import com.zimky.coregateUS.core.CoreConfig
import com.zimky.coregateUS.core.FreezeData
import com.zimky.coregateUS.core.RootShell
import com.zimky.coregateUS.ui.HintText
import com.zimky.coregateUS.ui.PAGE_BOTTOM_INSET
import com.zimky.coregateUS.ui.Toaster
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip

import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

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
    val ctx = LocalContext.current
    var labels by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var snap by remember { mutableStateOf(FreezeData.Snapshot()) }
    var loading by remember { mutableStateOf(true) }
    var pending by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var groupPending by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var pendingRiskyKey by remember { mutableStateOf<String?>(null) }
    val running = remember { mutableSetOf<String>() }
    val confirmed = remember { mutableMapOf<String, Boolean>() }
    var reloadJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var reading by remember { mutableStateOf(false) }
    var readAgain by remember { mutableStateOf(false) }
    val readCallbacks = remember { mutableListOf<() -> Unit>() }

    fun overlay(base: Map<String, String>): Map<String, String> = base.toMutableMap().apply {
        confirmed.forEach { (pkg, want) -> if (want) put(pkg, "suspended") else remove(pkg) }
        pending.forEach { (pkg, want) -> if (want) put(pkg, "suspended") else remove(pkg) }
    }

    fun reload(silent: Boolean = false, onDone: (() -> Unit)? = null) {
        if (reading) {
            readAgain = true
            if (onDone != null) readCallbacks.add(onDone)
            return
        }
        reading = true
        if (!silent) loading = true
        val readConfirmed = confirmed.toMap()
        com.zimky.coregateUS.core.FreezeStatus.read { code, out, _ ->
            if (code == 0 && out.contains("[actual]") && out.contains("[groups]")) {
                snap = FreezeData.parse(out)
                readConfirmed.forEach { (pkg, want) ->
                    if (confirmed[pkg] == want) confirmed.remove(pkg)
                }
            } else {
                Toaster.show("读取失败，已保留上次状态", true)
            }
            loading = false
            reading = false
            onDone?.invoke()
            if (readAgain) {
                readAgain = false
                val callbacks = readCallbacks.toList()
                readCallbacks.clear()
                reload(silent = true) { callbacks.forEach { it() } }
            }
        }
    }

    fun scheduleRead() {
        reloadJob?.cancel()
        reloadJob = scope.launch {
            delay(180)
            reload(silent = true) { onChanged() }
        }
    }

    fun sendPkg(pkg: String) {
        if (pkg in running) return
        val want = pending[pkg] ?: return
        running.add(pkg)
        val arg = if (want) "--freeze-add" else "--freeze-del"
        sh.exec("sh /data/adb/modules/CoreGate/service.sh $arg $pkg") { code, out, _ ->
            running.remove(pkg)
            val mark = if (want) "FREEZE_ADD_OK: $pkg" else "FREEZE_DEL_OK: $pkg"
            val ok = code == 0 && out.lineSequence().any { it.trim() == mark }
            if (ok) confirmed[pkg] = want
            if (pending[pkg] != want) {
                sendPkg(pkg)
            } else {
                pending = pending - pkg
                if (!ok) Toaster.show("操作未完成：$pkg，请查看实际状态", true)
                scheduleRead()
            }
        }
    }

    fun applyPkg(pkg: String, want: Boolean) {
        pending = pending + (pkg to want)
        sendPkg(pkg)
    }

    fun applyGroup(key: String, want: Boolean) {
        if (groupPending.isNotEmpty() || pending.isNotEmpty()) return
        groupPending = groupPending + (key to want)
        sh.exec("sh /data/adb/modules/CoreGate/service.sh --set-freeze $key $want") { code, out, _ ->
            if (code != 0 || !out.contains("SET_FREEZE_OK:")) {
                Toaster.show("部分组件未完成，请查看明细", true)
            }
            reload(silent = true) {
                groupPending = groupPending - key
                onChanged()
            }
        }
    }

    LaunchedEffect(Unit) { reload() }

    LaunchedEffect(ctx) {
        labels = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            AppLabels.resolve(ctx.applicationContext, AppLabels.allFreezePkgs())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = PAGE_BOTTOM_INSET),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── 顶部：返回 + 标题 ──────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                text = "‹ 返回",
                cornerRadius = 100.dp,
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
                // 记录与本次系统读取结果不一致时显式告警；差异本身不能确定原因。
                if (!loading && snap.staleList.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(cs.error.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "有 ${snap.staleList.size} 项有冻结记录，但本次未检测到系统冻结状态。\n" +
                                snap.staleList.joinToString("\n") { pkg -> "${labels[pkg] ?: pkg}（$pkg）" } +
                                "\n请重新读取确认；仅凭记录差异无法判断是状态被恢复、执行失败还是读取遗漏。",
                            color = cs.error,
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
                actual = overlay(snap.actual),
                pending = pending,
                labels = labels,
                groupBusy = groupPending.containsKey(g.key),
                enabled = groupPending[g.key] ?: cfg.let { c ->
                    // 组开关值优先取引擎回显的真实值；引擎没回显时退回本地配置
                    snap.groups[g.key] ?: c.let {
                        when (g.key) {
                            "log" -> it.freezeGrpLog
                            "media" -> it.freezeGrpMedia
                            "cloud" -> it.freezeGrpCloud
                            "diag" -> it.freezeGrpDiag
                            "ad" -> it.freezeGrpAd
                            "self" -> it.freezeGrpSelf
                            else -> c.rawExtra?.get("freeze_grp_${g.key}") == true
                        }
                    }
                },
                onToggleGroup = { wantOn ->
                    // 高风险组要先过二次确认，其余组直接生效
                    if (wantOn && g.risky) {
                        pendingRiskyKey = g.key
                    } else {
                        applyGroup(g.key, wantOn)
                    }
                },
                onTogglePkg = { pkg, freeze ->
                    if (groupPending.isEmpty()) applyPkg(pkg, freeze)
                }
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
                applyGroup(key, true)
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
                        "解除冻结请回到本页关闭该分组；重启不会自动清除已保存的停用状态。",
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
    actual: Map<String, String>,
    pending: Map<String, Boolean>,
    labels: Map<String, String>,
    enabled: Boolean,
    groupBusy: Boolean,
    onToggleGroup: (Boolean) -> Unit,
    onTogglePkg: (String, Boolean) -> Unit
) {
    val cs = MiuixTheme.colorScheme
    val (frozenPkgs, normalPkgs) = FreezeData.splitByActual(group, actual)
    // 本组与其它组重叠的包：作者自冻清单里有 5 个包同时出现在日志/影音/广告组，
    // 引擎侧有「占用保护」——只要还有别的组开着，关掉本组也不会恢复这些重叠包。
    // 用户曾困惑「前面的包怎么这么少」，其实就是重叠部分在视觉上被别的组占了。
    // 这里把这些包明确标出来，用户一眼就知道它们确实在清单里，只是被共享着。
    val overlapPkgs = remember(group.key) {
        val others = FreezeData.GROUPS.filter { it.key != group.key }
            .flatMap { it.pkgs }.toSet()
        group.pkgs.filter { it in others }
    }
    var expanded by androidx.compose.runtime.saveable.rememberSaveable(group.key) { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.animateContentSize().padding(horizontal = 16.dp, vertical = 14.dp)) {
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
                    // 重叠说明：本组列表是完整的，不做任何去重，
                    // 但其中这些包同时也属于别的组，若别的组开着，
                    // 关掉本组它们不会被解冻（占用权归开着的组）。
                    if (overlapPkgs.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "含 ${overlapPkgs.size} 个与其它组重叠的包，重复列出不算少",
                            color = cs.onSurfaceVariantSummary.copy(alpha = 0.7f),
                            fontSize = 9.5.sp
                        )
                    }
                }
                SwitchCompact(checked = enabled, busy = groupBusy, enabled = !groupBusy, onCheckedChange = onToggleGroup)
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
                    val isFrozen = actual.containsKey(pkg)
                    val mode = actual[pkg]
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
                            // 第一行：中文应用名（查不到就退回包名占位，绝不留空行）
                            Text(
                                text = labels[pkg] ?: pkg,
                                color = if (isFrozen) cs.onSurface else cs.onSurfaceVariantSummary,
                                fontSize = 12.5.sp
                            )
                            // 第二行：无论如何都显示包名。
                            // 用户明确要求「既显示中文名，又显示包名」，
                            // 所以这里不做二选一回退 —— 两行永远同时在场，
                            // 查不到中文名时第一行是包名、第二行还是包名也没关系，
                            // 一致性比省一行更重要。
                            Text(
                                text = if (isFrozen && !mode.isNullOrBlank())
                                    "$pkg · " + (if (mode == "suspend") "挂起" else "停用")
                                else pkg,
                                color = if (isFrozen && !mode.isNullOrBlank())
                                    cs.primary
                                else cs.onSurfaceVariantSummary,
                                fontSize = 9.5.sp
                            )
                        }

                        SwitchCompact(
                            checked = isFrozen,
                            busy = pending.containsKey(pkg),
                            onCheckedChange = { want -> onTogglePkg(pkg, want) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 紧凑开关（带动画版）。
 *
 * 【为什么不用标准 Switch】
 *   本页包列表一次可能出现 52 行，标准尺寸会撑得卡片极高、滚动很长；
 *   这里用 38×22dp 的胶囊表示状态，视觉更克制。
 *
 * 【为什么自己画动画】(v2.2.2)
 *   旧版 `background(if (checked) a else b)` + contentAlignment 换边，
 *   是两帧硬切：颜色瞬变、滑块瞬移，加上点击后要等 root 命令回读才翻，
 *   整体手感又僵又钝。现在：
 *     · 颜色用 animateColorAsState 渐变（轨道 180ms、滑块 180ms）
 *     · 滑块位置用 animateFloatAsState 在 0f..1f 间插值，再换算成像素偏移
 *     · 按下时整块轻微缩放（0.94）并降低不透明度，给手指即时反馈
 *     · busy 时滑块变细并在轨道上循环滑动，表示「正在和引擎确认」
 *   所有动画都跟随 checked 变化，所以即便是乐观更新也能立刻动起来。
 */
@Composable
private fun SwitchCompact(
    checked: Boolean,
    busy: Boolean = false,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val cs = MiuixTheme.colorScheme

    // 轨道颜色：开=主色，关=容器高亮色
    val trackColor by animateColorAsState(
        targetValue = if (checked) cs.primary else cs.surfaceContainerHigh,
        animationSpec = tween(durationMillis = 180),
        label = "track"
    )
    // 滑块颜色：开=白，关=中灰；按下时也提亮一点，反馈更明显
    val knobColor by animateColorAsState(
        targetValue = if (checked) Color.White else cs.onSurfaceVariantSummary,
        animationSpec = tween(durationMillis = 180),
        label = "knob"
    )
    // 滑块位置 0f(左) → 1f(右)
    val pos by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "pos"
    )
    // 按下缩放
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = tween(durationMillis = 90),
        label = "scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (pressed) 0.75f else 1f,
        animationSpec = tween(durationMillis = 90),
        label = "alpha"
    )

    // busy 时滑块的呼吸透明度：0.45 ↔ 1.0 无限循环，
    // 让用户看出「正在等引擎确认」，而不是误以为又点不动了。
    //
    val busyAlpha = if (busy) {
        val transition = rememberInfiniteTransition(label = "busy")
        val pulse by transition.animateFloat(
            initialValue = 0.45f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse), label = "busyAlpha"
        )
        pulse
    } else 1f

    // 轨道内宽 = 38dp，滑块 18dp，两侧各留 2dp → 可移动距离 38-18-4 = 16dp
    // busy 时滑块收窄到 14dp，可移动距离要相应加宽 4dp，否则滑块会缩在左半边不靠右
    val knobWidthDp = if (busy) 14f else 18f
    val travelDp = 38f - knobWidthDp - 4f
    val knobOffsetDp = travelDp * pos

    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 40.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = androidx.compose.ui.semantics.Role.Switch,
                interactionSource = press,
                indication = null,
                onValueChange = onCheckedChange
            )
            .padding(horizontal = 5.dp, vertical = 9.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
            .clip(RoundedCornerShape(11.dp))
            .background(trackColor),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 2.dp)
                .offset(x = knobOffsetDp.dp)
                .size(width = if (busy) 14.dp else 18.dp, height = 18.dp)
                .graphicsLayer { this.alpha = if (busy) busyAlpha else 1f }
                .clip(CircleShape)
                .background(knobColor)
        )
    }
}