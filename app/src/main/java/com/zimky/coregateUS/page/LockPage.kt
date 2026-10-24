package com.zimky.coregateUS.page

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.zimky.coregateUS.core.CoreConfig
import com.zimky.coregateUS.core.ModuleBridge
import com.zimky.coregateUS.core.RootShell
import com.zimky.coregateUS.core.SysProbe
import com.zimky.coregateUS.ui.BottomSpacer
import com.zimky.coregateUS.ui.CoreCell
import com.zimky.coregateUS.ui.Gap
import com.zimky.coregateUS.ui.HairLine
import com.zimky.coregateUS.ui.PAGE_BOTTOM_INSET
import com.zimky.coregateUS.ui.Semantic
import com.zimky.coregateUS.ui.Toaster
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 第 1 页：锁核运行状态 + 冻结摘要 + 核心状态 + 锁核建议。
 *
 * 行为契约与旧 LockPage.java 完全一致：
 *  - 状态一律由传入的 [snapshot] 单向推导，本页绝不主动发 root；
 *  - 核心开关与冻结入口都是用户显式点击，且失败后不会自动重试，避免反复惊动安全机制。
 *
 * 关于 [frozenCount]：这个值由 MainActivity 的 probeFreeze() 统一探测后
 * 下传，本页只负责显示，绝不自己发命令 —— 否则每次切回首页都多一次 root，
 * 用户连续操作时容易被安全机制判为入侵。
 *
 * 三态语义（务必不要用 false 兜底 null）：
 *  - null      = 还没探到（首次进入或探测进行中）
 *  - 0         = 探到了，结果是「没有」
 * 两者在界面上必须显示成完全不同的文字，否则用户会把「不知道」当成「没有」。
 *
 * 视觉：卡片统一用 Miuix [Card] + [BasicComponent]，状态用 [Badge]，
 * 与 KernelSU 管理器首页保持同一套组件语言。
 *
 * @param frozenCount  本模块冻结中且系统确实已冻结的项数；null 表示尚未探明
 * @param onOpenFreeze 跳转冻结管理二级页
 */
@Composable
fun LockPage(
    cfg: CoreConfig,
    snapshot: SysProbe.Snapshot?,
    packagesRaw: String,
    sh: RootShell,
    onRefresh: () -> Unit,
    save: (CoreConfig, String?, ((Boolean, String) -> Unit)?) -> Unit,
    frozenCount: Int? = null,
    onOpenFreeze: () -> Unit = {}
) {
    val cs = MiuixTheme.colorScheme
    val s = snapshot
    val daemonAlive = s?.daemonAlive == true
    val total = s?.cores?.size ?: 0
    val onlineCount = s?.onlineCount ?: 0
    val locked = total > 0 && onlineCount > 0 && onlineCount < total
    val bigOnline = s?.cores?.count { it.online && it.cluster == "big" } ?: 0
    val littleOnline = s?.cores?.count { it.online && it.cluster == "little" } ?: 0

    // 正在执行的核操作：null = 空闲。存在时所有方块都不可点，防止并发。
    var busy by remember { mutableStateOf(false) }
    // 待确认的核操作：点了方块先落这里，弹框确认后才真正发命令。
    var pendingCore by remember { mutableStateOf<SysProbe.Core?>(null) }

    Column(
        modifier = Modifier
            // 一级页必须自己铺满整屏：
            // 这四页是常驻 composition 的，靠 PageSlot 的 zIndex 叠在一起。
            // 根 Column 若只写 fillMaxWidth，竖向高度会随内容收缩，
            // 内容不足一屏时下面那块就不属于本页 —— 既会透出下层页，
            // 点击也会漏到别的页去（真机反馈的「按钮点不着」）。
            // fillMaxSize 同时给 verticalScroll 一个有界高度的视口。
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = PAGE_BOTTOM_INSET),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

        // ── 卡片一：锁核运行状态 ─────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                when {
                    !daemonAlive -> {
                        StateBadge("未运行", cs.error)
                        Gap(10.dp)
                        Text(text = "守护进程未启动", color = cs.onSurface, fontSize = 15.sp)
                        Gap(6.dp)
                        Text(
                            text = "模块可能已被禁用，或安装后尚未重启。可尝试重新刷入模块并重启设备。",
                            color = cs.onSurfaceVariantSummary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                    locked -> {
                        BreathingBadge("锁核中", cs.primary)
                        Gap(10.dp)
                        Text(text = "已封锁 $bigOnline 个核心", color = cs.onSurface, fontSize = 15.sp)
                        Gap(6.dp)
                        Text(
                            text = "当前前台应用未被识别为游戏/录屏，空闲大核已离线以节省功耗。",
                            color = cs.onSurfaceVariantSummary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                    else -> {
                        StateBadge("全核在线", Semantic.ACC)
                        Gap(10.dp)
                        Text(text = "全部核心在线", color = cs.onSurface, fontSize = 15.sp)
                        Gap(6.dp)
                        Text(
                            text = "当前处于游戏或录屏/相机场景，或模块已禁用锁核，性能已全部放开。",
                            color = cs.onSurfaceVariantSummary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }

                Gap(12.dp)
                HairLine()
                Gap(10.dp)

                val pidText = if (s != null && s.daemonPid > 0) s.daemonPid.toString() else "—"
                val pkgText = s?.statePkg?.takeIf { it.isNotBlank() } ?: "—"
                Text(
                    text = "守护进程：${if (daemonAlive) "运行中" else "未运行"} · PID $pidText · 前台 $pkgText",
                    color = cs.onSurfaceVariantSummary,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )
            }
        }

        // ── 卡片二：冻结状态（独立一框）───────────────────────────────
        // 数字取 FreezeData.Snapshot.scopedFrozenCount —— 「本模块作用域内、且系统里确实冻上了」
        // 的交集，不是「打算冻几个」。用户要看的是真冻没冻上，所以必须是前者。
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "冻结",
                        color = cs.onSurface,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                    when {
                        frozenCount == null -> StateBadge("还没探到", cs.onSurfaceVariantSummary)
                        frozenCount == 0 -> StateBadge("没有冻结", cs.onSurfaceVariantSummary)
                        else -> StateBadge("已冻结 $frozenCount 个", Semantic.OK)
                    }
                }
                Gap(8.dp)
                Text(
                    text = when {
                        frozenCount == null -> "正在读取冻结状态，稍等一下"
                        frozenCount == 0 -> "当前没有冻结任何服务"
                        else -> "已冻结 $frozenCount 个服务"
                    },
                    color = cs.onSurface,
                    fontSize = 14.sp,
                    lineHeight = 19.sp
                )

                // 逐项明细本页拿不到（那需要 --freeze-status 的 actual 段，只有二级页真正解析），
                // 所以这里只给入口，不臆造列表。
                Gap(6.dp)
                Text(
                    text = "每组每个包是否真的冻结成功，点进去逐项看。",
                    color = cs.onSurfaceVariantSummary,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )
                Gap(10.dp)
                HairLine()
                Gap(10.dp)
                TextButton(
                    text = "查看冻结明细",
                    onClick = onOpenFreeze,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }

        // ── 卡片三：核心状态 ─────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(
                    text = "核心状态",
                    color = cs.onSurface,
                    fontSize = 16.sp
                )
                Gap(10.dp)

                val littleMax = s?.littleMaxCur ?: 0L
                val bigMax = s?.bigMaxCur ?: 0L
                Text(
                    text = "在线 $onlineCount / $total · 小核簇上限 ${SysProbe.fmtMhz(littleMax)} · 大核簇上限 ${SysProbe.fmtMhz(bigMax)}",
                    color = cs.onSurfaceVariantSummary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                Gap(12.dp)

                val cores = s?.cores ?: emptyList()
                if (cores.isEmpty()) {
                    Text(
                        text = "暂时读不到核心信息，下拉或稍候自动刷新。",
                        color = cs.onSurfaceVariantSummary,
                        fontSize = 12.sp
                    )
                } else {
                    // 每行 4 个，与旧版 CoreGridView 一致
                    val rows = cores.chunked(4)
                    rows.forEachIndexed { index, rowCores ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowCores.forEach { core ->
                                val col = coreColor(core)
                                // 点方块 = 切换这个核的「手动封锁」状态：
                                //   在线 → 封锁（写进模块的 manual_off 清单，主循环会一直按着它）
                                //   离线 → 解封（移出清单并立刻拉回在线）
                                //
                                // 为什么在线状态以 snapshot 为准、而不是本地点一下改本地状态：
                                //   封锁可能被内核/厂商策略拒绝，真正结果只有重新探测才知道。
                                //   所以统一走「发命令 → 用回调结果提示 → onRefresh() 重读真机状态」，
                                //   界面永远显示设备真实的样子，不做乐观更新。
                                //
                                // cpu0 不给点：它永不下线，点了也只会失败，反而让人以为模块坏了。
                                val clickable = core.id != 0 && !busy
                                CoreCell(
                                    id = core.id,
                                    online = core.online,
                                    freqText = if (core.online) SysProbe.fmtMhz(core.curFreq) else "离线",
                                    color = col,
                                    modifier = Modifier.weight(1f),
                                    onClick = if (clickable) {
                                        // 点方块只弹确认框，不直接写。封锁/解封会真的把核下线上线，
                                        // 误触会让手机瞬间卡顿，所以必须二次确认。
                                        {
                                            pendingCore = core
                                        }
                                    } else null
                                )
                            }
                            // 补齐空格，保持四列对齐
                            repeat(4 - rowCores.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                        if (index != rows.lastIndex) Spacer(Modifier.height(8.dp))
                    }
                }

                Gap(12.dp)
                Text(
                    text = "绿色 = 低频省电 · 蓝色 = 中频 · 橙色 = 高频大核（优先封锁对象）",
                    color = cs.onSurfaceVariantSummary,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )
            }
        }

        // ── 卡片四：锁核建议 ─────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(text = "锁核建议", color = cs.onSurface, fontSize = 16.sp)
                Gap(10.dp)

                when {
                    !daemonAlive -> {
                        StateBadge("不可用", cs.error)
                        Gap(10.dp)
                        Text(
                            text = "守护进程未运行，锁核不会自动执行。请先确认模块已启用并重启。",
                            color = cs.onSurfaceVariantSummary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                    bigOnline == 0 -> {
                        StateBadge("无需封锁", Semantic.OK)
                        Gap(10.dp)
                        Text(
                            text = "当前在线核心均为小核（$littleOnline 个），已经处于低功耗状态，没有可封锁的高频大核。",
                            color = cs.onSurfaceVariantSummary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                    else -> {
                        StateBadge("建议封锁 $bigOnline 个", Semantic.WARN)
                        Gap(10.dp)
                        Text(
                            text = "检测到 $bigOnline 个大核仍在线（共 $littleOnline 个小核在线）。大核是功耗大头，" +
                                "日常场景下封掉它们能明显降低待机与轻度使用的功耗；进入游戏或录屏/相机时模块会自动恢复全部核心。",
                            color = cs.onSurfaceVariantSummary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }

        // ── 核心封锁/解封二次确认 ────────────────────────────────────
        // 放在内容末尾、与卡片同级：用 fillMaxSize 遮罩盖住整页，不参与卡片排版。
        pendingCore?.let { core ->
            CoreConfirmDialog(
                core = core,
                onCancel = { pendingCore = null },
                onConfirm = {
                    pendingCore = null
                    busy = true
                    val target = core
                    val action = if (target.online) {
                        { cb: (Boolean, String) -> Unit -> ModuleBridge.coreOff(sh, target.id, cb) }
                    } else {
                        { cb: (Boolean, String) -> Unit -> ModuleBridge.coreOn(sh, target.id, cb) }
                    }
                    action { ok, msg ->
                        busy = false
                        if (ok) {
                            Toaster.show(
                                if (target.online) "已封锁 CPU${target.id}，切到游戏时会自动恢复全部核心。"
                                else "已解除 CPU${target.id} 的封锁。"
                            )
                        } else {
                            Toaster.show(msg, true)
                        }
                        // 无论成败都重读真机状态：成功要看到变化，失败要看到「其实没变」
                        onRefresh()
                    }
                }
            )
        }

        BottomSpacer()
    }
}

/** 状态徽标：静态，颜色由调用方按语义给出。文字统一白色，与 KernelSU 徽标一致。 */
@Composable
private fun StateBadge(text: String, color: Color) {
    Badge(containerColor = color, contentColor = Color.White) {
        Text(text = text, fontSize = 12.sp, color = Color.White)
    }
}

/**
 * 呼吸徽标：锁核进行中时用轻微明暗变化提示「正在工作」。
 *
 * Miuix 的 Badge 没有内置动画参数，这里在它外层套 [Modifier.alpha] 实现，
 * 取值范围 0.55~1.0，周期 1.4 秒，与旧版 StatusPill(breathing = true) 观感一致。
 */
@Composable
private fun BreathingBadge(text: String, color: Color) {
    val transition = rememberInfiniteTransition(label = "badgeBreath")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "badgeAlpha"
    )
    Box(modifier = Modifier.alpha(alpha)) {
        Badge(containerColor = color, contentColor = Color.White) {
            Text(text = text, fontSize = 12.sp, color = Color.White)
        }
    }
}

/** 核心方块配色：离线灰 / 低频绿 / 中频蓝 / 高频大核橙。 */
private fun coreColor(core: SysProbe.Core): Color {
    if (!core.online) return Semantic.OFF
    val max = if (core.availMax > 0) core.availMax else core.maxFreq
    if (core.cluster == "big") {
        return if (max > 0 && core.curFreq >= max * 7 / 10) Semantic.WARN else Semantic.ACC
    }
    val ratio = if (max > 0) core.curFreq.toFloat() / max.toFloat() else 0f
    return if (ratio < 0.5f) Semantic.OK else Semantic.ACC
}

/**
 * 核心封锁 / 解封的二次确认弹窗。
 *
 * 为什么需要确认：
 *   封锁核心是真正把 CPU 核下线的操作，误触会让手机瞬间卡顿甚至短暂掉帧；
 *   解封同理会把已锁的核重新拉回在线、功耗立刻上去。两者都不该「点一下就发生」。
 *
 * 文案直白说明后果，不写技术术语（用户不关心 sysfs 路径）。
 */
@Composable
private fun CoreConfirmDialog(
    core: SysProbe.Core,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    val cs = MiuixTheme.colorScheme
    val locking = core.online
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
                    text = if (locking) "封锁 CPU${core.id}？" else "解封 CPU${core.id}？",
                    color = cs.primary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (locking) {
                        "封锁后这个核心会下线，功耗下降，但这个核心的算力暂时用不到。" +
                            "进入游戏或录屏 / 相机时模块会自动恢复全部核心，重启也会还原。"
                    } else {
                        "解封后这个核心会重新上线，算力恢复，功耗也会跟着上去。"
                    },
                    color = cs.onSurfaceVariantSummary,
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "当前频率 ${SysProbe.fmtMhz(core.curFreq)}",
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
                        text = if (locking) "确认封锁" else "确认解封",
                        onClick = onConfirm,
                        colors = ButtonDefaults.textButtonColors(textColor = cs.primary)
                    )
                }
            }
        }
    }
}