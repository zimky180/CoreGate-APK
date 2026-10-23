package com.zimky.coregateUS.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zimky.coregateUS.core.CoreConfig
import com.zimky.coregateUS.core.ModuleBridge
import com.zimky.coregateUS.core.RootShell
import com.zimky.coregateUS.core.SysProbe
import com.zimky.coregateUS.core.serviceExists
import com.zimky.coregateUS.ui.BottomSpacer
import com.zimky.coregateUS.ui.C
import com.zimky.coregateUS.ui.CardDivider
import com.zimky.coregateUS.ui.CoreCell
import com.zimky.coregateUS.ui.GlassCard
import com.zimky.coregateUS.ui.HintText
import com.zimky.coregateUS.ui.PrimaryButton
import com.zimky.coregateUS.ui.StatusPill
import com.zimky.coregateUS.ui.SubText

/**
 * 第 1 页：锁核运行状态 + 核心状态 + 锁核建议 + 手动封锁。
 *
 * 行为契约与旧 LockPage.java 完全一致：
 *  - 状态一律由传入的 [snapshot] 单向推导，本页绝不主动发 root；
 *  - 唯一的写操作是「封锁高频核心」，且失败后不会自动重试，避免反复惊动安全机制。
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
    val s = snapshot
    val daemonAlive = s?.daemonAlive == true
    val total = s?.cores?.size ?: 0
    val onlineCount = s?.onlineCount ?: 0
    val locked = total > 0 && onlineCount > 0 && onlineCount < total
    val bigOnline = s?.cores?.count { it.online && it.cluster == "big" } ?: 0
    val littleOnline = s?.cores?.count { it.online && it.cluster == "little" } ?: 0

    var busy by remember { mutableStateOf(false) }
    var lockMsg by remember { mutableStateOf("") }
    var lockMsgColor by remember { mutableStateOf(C.SUB) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

        // ── 卡片一：锁核运行状态 ─────────────────────────────────────
        GlassCard(title = "锁核运行状态") {
            when {
                !daemonAlive -> {
                    StatusPill("未运行", C.ERR)
                    Spacer(Modifier.height(10.dp))
                    SubText("守护进程未启动", color = C.TXT)
                    Spacer(Modifier.height(6.dp))
                    SubText("模块可能已被禁用，或安装后尚未重启。可尝试重新刷入模块并重启设备。")
                }
                locked -> {
                    StatusPill("锁核中", C.OK, breathing = true)
                    Spacer(Modifier.height(10.dp))
                    SubText("已封锁 $bigOnline 个核心", color = C.TXT)
                    Spacer(Modifier.height(6.dp))
                    SubText("当前前台应用未被识别为游戏/录屏，空闲大核已离线以节省功耗。")
                }
                else -> {
                    StatusPill("全核在线", C.ACC)
                    Spacer(Modifier.height(10.dp))
                    SubText("全部核心在线", color = C.TXT)
                    Spacer(Modifier.height(6.dp))
                    SubText("当前处于游戏或录屏/相机场景，或模块已禁用锁核，性能已全部放开。")
                }
            }

            CardDivider()

            val pidText = if (s != null && s.daemonPid > 0) s.daemonPid.toString() else "—"
            val pkgText = s?.statePkg?.takeIf { it.isNotBlank() } ?: "—"
            HintText("守护进程：${if (daemonAlive) "运行中" else "未运行"} · PID $pidText · 前台 $pkgText")
        }

        // ── 卡片二：核心状态 ─────────────────────────────────────────
        GlassCard(title = "核心状态") {
            val littleMax = s?.littleMaxCur ?: 0L
            val bigMax = s?.bigMaxCur ?: 0L
            SubText(
                "在线 $onlineCount / $total · 小核簇上限 ${SysProbe.fmtMhz(littleMax)} · 大核簇上限 ${SysProbe.fmtMhz(bigMax)}"
            )
            Spacer(Modifier.height(12.dp))

            val cores = s?.cores ?: emptyList()
            if (cores.isEmpty()) {
                SubText("暂时读不到核心信息，下拉或稍候自动刷新。")
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

            Spacer(Modifier.height(12.dp))
            HintText("绿色 = 低频省电 · 蓝色 = 中频 · 橙色 = 高频大核（优先封锁对象）")
        }

        // ── 卡片三：锁核建议 ─────────────────────────────────────────
        GlassCard(title = "锁核建议") {
            when {
                !daemonAlive -> {
                    StatusPill("不可用", C.ERR)
                    Spacer(Modifier.height(10.dp))
                    SubText("守护进程未运行，锁核不会自动执行。请先确认模块已启用并重启。")
                }
                bigOnline == 0 -> {
                    StatusPill("无需封锁", C.OK)
                    Spacer(Modifier.height(10.dp))
                    SubText("当前在线核心均为小核（$littleOnline 个），已经处于低功耗状态，没有可封锁的高频大核。")
                }
                else -> {
                    StatusPill("建议封锁 $bigOnline 个", C.WARN)
                    Spacer(Modifier.height(10.dp))
                    SubText(
                        "检测到 $bigOnline 个大核仍在线（共 $littleOnline 个小核在线）。大核是功耗大头，" +
                            "日常场景下封掉它们能明显降低待机与轻度使用的功耗；进入游戏或录屏/相机时模块会自动恢复全部核心。"
                    )
                }
            }
        }

        // ── 卡片四：手动操作 ─────────────────────────────────────────
        GlassCard(title = "手动操作") {
            SubText(
                "立即对当前在线的高频核心执行一次封锁。这是一次性动作，不会改变配置；" +
                    "日常自动锁核仍由守护进程负责。"
            )
            Spacer(Modifier.height(14.dp))

            PrimaryButton(
                text = if (busy) "封锁中…" else "封锁高频核心",
                enabled = !busy,
                onClick = {
                    if (busy) return@PrimaryButton
                    if (!serviceExists()) {
                        lockMsg = "未找到模块 service.sh，请确认 CoreGate 已正确安装并重启过。"
                        lockMsgColor = C.ERR
                        return@PrimaryButton
                    }
                    busy = true
                    lockMsg = ""
                    ModuleBridge.lockOnce(sh, -1) { ok, msg ->
                        busy = false
                        if (ok) {
                            lockMsg = msg
                            lockMsgColor = C.OK
                            onRefresh()
                        } else {
                            lockMsg = msg
                            lockMsgColor = C.ERR
                        }
                    }
                }
            )

            if (lockMsg.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                HintText(lockMsg, color = lockMsgColor)
            }
        }

        BottomSpacer()
    }
}

/** 核心方块配色：离线灰 / 低频绿 / 中频蓝 / 高频大核橙。 */
private fun coreColor(core: SysProbe.Core): Color {
    if (!core.online) return C.OFF
    val max = if (core.availMax > 0) core.availMax else core.maxFreq
    if (core.cluster == "big") {
        return if (max > 0 && core.curFreq >= max * 7 / 10) C.WARN else C.ACC
    }
    val ratio = if (max > 0) core.curFreq.toFloat() / max.toFloat() else 0f
    return if (ratio < 0.5f) C.OK else C.ACC
}