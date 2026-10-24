package com.zimky.coregateUS.page

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zimky.coregateUS.core.CoreConfig
import com.zimky.coregateUS.core.ModuleBridge
import com.zimky.coregateUS.core.RootShell
import com.zimky.coregateUS.core.SysProbe
import com.zimky.coregateUS.core.serviceExists
import com.zimky.coregateUS.ui.BottomSpacer
import com.zimky.coregateUS.ui.CoreCell
import com.zimky.coregateUS.ui.Gap
import com.zimky.coregateUS.ui.HairLine
import com.zimky.coregateUS.ui.Semantic
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 第 1 页：锁核运行状态 + 核心状态 + 锁核建议 + 手动封锁。
 *
 * 行为契约与旧 LockPage.java 完全一致：
 *  - 状态一律由传入的 [snapshot] 单向推导，本页绝不主动发 root；
 *  - 唯一的写操作是「封锁高频核心」，且失败后不会自动重试，避免反复惊动安全机制。
 *
 * 视觉：卡片统一用 Miuix [Card] + [BasicComponent]，状态用 [Badge]，
 * 与 KernelSU 管理器首页保持同一套组件语言。
 */
@Composable
fun LockPage(
    cfg: CoreConfig,
    snapshot: SysProbe.Snapshot?,
    packagesRaw: String,
    sh: RootShell,
    onRefresh: () -> Unit,
    save: (CoreConfig, String?, ((Boolean, String) -> Unit)?) -> Unit
) {
    val cs = MiuixTheme.colorScheme
    val s = snapshot
    val daemonAlive = s?.daemonAlive == true
    val total = s?.cores?.size ?: 0
    val onlineCount = s?.onlineCount ?: 0
    val locked = total > 0 && onlineCount > 0 && onlineCount < total
    val bigOnline = s?.cores?.count { it.online && it.cluster == "big" } ?: 0
    val littleOnline = s?.cores?.count { it.online && it.cluster == "little" } ?: 0

    var busy by remember { mutableStateOf(false) }
    var lockMsg by remember { mutableStateOf("") }
    var lockMsgColor by remember { mutableStateOf(Semantic.ERR) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
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

        // ── 卡片二：核心状态 ─────────────────────────────────────────
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
                                CoreCell(
                                    id = core.id,
                                    online = core.online,
                                    freqText = if (core.online) SysProbe.fmtMhz(core.curFreq) else "离线",
                                    color = col,
                                    modifier = Modifier.weight(1f)
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

        // ── 卡片三：锁核建议 ─────────────────────────────────────────
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

        // ── 卡片四：手动操作 ─────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(text = "手动操作", color = cs.onSurface, fontSize = 16.sp)
                Gap(10.dp)
                Text(
                    text = "立即对当前在线的高频核心执行一次封锁。这是一次性动作，不会改变配置；" +
                        "日常自动锁核仍由守护进程负责。",
                    color = cs.onSurfaceVariantSummary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                Gap(14.dp)

                Button(
                    text = if (busy) "封锁中…" else "封锁高频核心",
                    onClick = {
                        if (busy) return@Button
                        if (!serviceExists()) {
                            lockMsg = "未找到模块 service.sh，请确认 CoreGate 已正确安装并重启过。"
                            lockMsgColor = Semantic.ERR
                            return@Button
                        }
                        busy = true
                        lockMsg = ""
                        ModuleBridge.lockOnce(sh, -1) { ok, msg ->
                            busy = false
                            if (ok) {
                                lockMsg = msg
                                lockMsgColor = Semantic.OK
                                onRefresh()
                            } else {
                                lockMsg = msg
                                lockMsgColor = Semantic.ERR
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )

                if (lockMsg.isNotEmpty()) {
                    Gap(10.dp)
                    Text(
                        text = lockMsg,
                        color = lockMsgColor,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )
                }
            }
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