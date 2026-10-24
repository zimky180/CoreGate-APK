package com.zimky.coregateUS.page

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zimky.coregateUS.core.CoreConfig
import com.zimky.coregateUS.core.LogReader
import com.zimky.coregateUS.core.Paths
import com.zimky.coregateUS.core.RootShell
import com.zimky.coregateUS.core.SysProbe
import com.zimky.coregateUS.core.serviceExists
import com.zimky.coregateUS.ui.BottomSpacer
import com.zimky.coregateUS.ui.CardDivider
import com.zimky.coregateUS.ui.Gap
import com.zimky.coregateUS.ui.HairLine
import com.zimky.coregateUS.ui.HintText
import com.zimky.coregateUS.ui.KeyValueRow
import com.zimky.coregateUS.ui.MonoText
import com.zimky.coregateUS.ui.Semantic
import com.zimky.coregateUS.ui.SubText
import com.zimky.coregateUS.ui.SwitchRow
import com.zimky.coregateUS.ui.Toaster
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 第 4 页：设置 / 名单 / 日志 / 诊断 / 关于。
 *
 * 顺序刻意如此：日常会碰的（总开关、名单）在上，排查才用的（日志、诊断）在下，
 * 关于页压底。诊断区折叠式堆在一起，不占日常视野。
 *
 * 视觉：Miuix [Card] + [Badge] + [TextButton]，与其余三页同一套组件语言。
 */
@Composable
fun SettingsPage(
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

    // ── 名单编辑缓冲：父级刷新回来时同步，用户改过则不覆盖 ──
    var listText by remember { mutableStateOf(packagesRaw) }
    var listDirty by remember { mutableStateOf(false) }
    if (!listDirty && listText != packagesRaw) listText = packagesRaw

    // ── 日志状态 ──
    var logText by remember { mutableStateOf("") }
    var logFrom by remember { mutableStateOf("") }
    var logBusy by remember { mutableStateOf(false) }

    // ── 诊断刷新计数，点一次变一次 ──
    var diagTick by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

        // ── 卡片一：模块总开关 ───────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(text = "模块总开关", color = cs.onSurface, fontSize = 16.sp)
                Gap(12.dp)

                SwitchRow(
                    title = "启用 CoreGate",
                    desc = if (cfg.enabled) {
                        "守护进程会按策略自动锁核。"
                    } else {
                        "已停用：模块不再做任何锁核动作，但不会卸载。"
                    }
                ) {
                    Switch(
                        checked = cfg.enabled,
                        onCheckedChange = { on ->
                            save(cfg.copy(enabled = on), null) { ok, msg ->
                                if (ok) {
                                    Toaster.show(if (on) "CoreGate 已启用" else "CoreGate 已停用")
                                    onRefresh()
                                } else {
                                    Toaster.show("保存失败：$msg", true)
                                }
                            }
                        },
                        colors = switchColors()
                    )
                }

                Gap(12.dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SubText("运行状态")
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                        when {
                            !cfg.enabled -> StateBadge("已停用", Semantic.OFF)
                            !daemonAlive -> StateBadge("未运行", Semantic.ERR)
                            else -> BreathingBadge("运行中", Semantic.OK)
                        }
                    }
                }

                if (!serviceExists()) {
                    Gap(10.dp)
                    HintText("未找到 ${Paths.SERVICE}，模块可能没刷入或已损坏。", color = Semantic.WARN)
                }
            }
        }

        // ── 卡片二：游戏名单 ─────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(text = "游戏名单", color = cs.onSurface, fontSize = 16.sp)
                Gap(10.dp)
                SubText("每行一个包名。命中名单时模块会自动放开全部核心，退出后恢复锁核。")
                Gap(10.dp)
                OutlinedTextField(
                    value = listText,
                    onValueChange = {
                        listText = it
                        listDirty = it != packagesRaw
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 132.dp),
                    textStyle = TextStyle(
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        fontFamily = FontFamily.Monospace,
                        color = cs.onSurface
                    ),
                    placeholder = { HintText("com.example.game") },
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors()
                )
                Gap(8.dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val count = listText.lineSequence().count { it.isNotBlank() }
                    HintText("共 $count 个包名" + if (listDirty) "（有未保存改动）" else "")
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                        TextButton(
                            text = "保存名单",
                            onClick = {
                                save(cfg, listText) { ok, msg ->
                                    if (ok) {
                                        listDirty = false
                                        Toaster.show("名单已保存")
                                        onRefresh()
                                    } else {
                                        Toaster.show("保存失败：$msg", true)
                                    }
                                }
                            },
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                    }
                }
            }
        }

        // ── 卡片三：模块日志 ─────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(text = "模块日志", color = cs.onSurface, fontSize = 16.sp)
                Gap(10.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        text = if (logBusy) "读取中…" else "读取日志",
                        onClick = {
                            if (logBusy) return@TextButton
                            logBusy = true
                            LogReader.tail(sh, 160) { text, from, found ->
                                logBusy = false
                                if (!found) {
                                    logText = ""
                                    logFrom = ""
                                    Toaster.show("四个日志路径都没有文件", true)
                                } else {
                                    logText = text
                                    logFrom = from
                                }
                            }
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                    TextButton(
                        text = "清空日志",
                        onClick = {
                            LogReader.clear(sh) { code, _, _ ->
                                if (code == 0) {
                                    logText = ""
                                    Toaster.show("日志已清空")
                                } else {
                                    Toaster.show("清空失败，退出码 $code", true)
                                }
                            }
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }

                Gap(10.dp)
                if (logFrom.isNotEmpty()) {
                    HintText(logFrom)
                    Gap(6.dp)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(cs.surfaceContainerHigh, RoundedCornerShape(12.dp))
                        .border(1.dp, cs.outline, RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    if (logText.isEmpty()) {
                        HintText("还没读取日志。点上方「读取日志」拉取最近 160 行。")
                    } else {
                        SelectionContainer {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                MonoText(logText)
                            }
                        }
                    }
                }

                Gap(8.dp)
                HintText(
                    "按顺序回退查找：modules/…/log、modules_update/…/log、CoreGate/log、CoreGate/ 根目录。"
                )
            }
        }

        // ── 卡片四：诊断 ─────────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(text = "诊断", color = cs.onSurface, fontSize = 16.sp)
                Gap(10.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HintText("点一次采集一次，不发自动请求")
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                        TextButton(
                            text = "刷新诊断",
                            onClick = {
                                diagTick++
                                Toaster.show("已采集")
                            },
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                    }
                }
                // diagTick 参与读取，保证刷新按钮能触发重组
                @Suppress("UNUSED_EXPRESSION") diagTick

                CardDivider()

                // ① 运行环境
                KeyValueRow("模块目录", if (serviceExists()) "已找到" else "缺失")
                KeyValueRow("service.sh", Paths.SERVICE)
                KeyValueRow("配置", Paths.FILE)
                KeyValueRow("配置已读", if (cfg.loaded) "是" else "否（读取失败）")

                Gap(8.dp)
                HairLine()
                Gap(8.dp)

                // ② 守护进程
                KeyValueRow("守护进程", if (daemonAlive) "存活" else "未运行")
                KeyValueRow("PID", if ((s?.daemonPid ?: -1) > 0) s!!.daemonPid.toString() else "—")

                Gap(8.dp)
                HairLine()
                Gap(8.dp)

                // ③ 频率上限
                KeyValueRow("小核上限", SysProbe.fmtMhz(s?.littleMaxCur ?: 0))
                KeyValueRow("大核上限", SysProbe.fmtMhz(s?.bigMaxCur ?: 0))
                KeyValueRow("GPU 上限", SysProbe.fmtGpu(s?.gpuMax ?: 0))

                val dbg = s?.dbg?.trim().orEmpty()
                if (dbg.isNotEmpty()) {
                    Gap(8.dp)
                    HairLine()
                    Gap(8.dp)
                    SubText("探测输出")
                    Gap(6.dp)
                    SelectionContainer {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            MonoText(dbg.take(600))
                        }
                    }
                }

                val hist = RootShell.history().take(8)
                if (hist.isNotEmpty()) {
                    Gap(8.dp)
                    HairLine()
                    Gap(8.dp)
                    SubText("最近 ${hist.size} 条命令")
                    Gap(6.dp)
                    SelectionContainer {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            hist.forEach { r ->
                                val cmd = if (r.cmd.length > 70) r.cmd.take(70) + "…" else r.cmd
                                val color = if (r.exit == 0) cs.onSurfaceVariantSummary else Semantic.ERR
                                MonoText(
                                    "[${r.exit}] ${r.costMs}ms  $cmd",
                                    color = color,
                                    modifier = Modifier.padding(vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── 卡片五：关于 ─────────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(text = "关于", color = cs.onSurface, fontSize = 16.sp)
                Gap(10.dp)

                KeyValueRow("名称", "CoreGate")
                KeyValueRow("作者", "zimky")
                KeyValueRow("版本", "2.2.0")
                KeyValueRow("模块目录", Paths.MODULE_DIR)
                KeyValueRow("数据目录", Paths.DIR)

                CardDivider()
                SubText(
                    "设计原则：只在运行时改 sysfs，配置存于 /data 用户数据区，" +
                        "不写任何系统分区，重启即还原，完全可逆、零残留。"
                )
                Gap(8.dp)
                HintText("APK 与模块的 WebUI 读写同一份 config.json 与 packages.txt，两边互不影响。")
            }
        }

        BottomSpacer()
    }
}

/** 状态徽标：静态，白色文字，与 KernelSU 徽标一致。 */
@Composable
private fun StateBadge(text: String, color: Color) {
    Badge(containerColor = color, contentColor = Color.White) {
        Text(text = text, fontSize = 12.sp, color = Color.White)
    }
}

/** 呼吸徽标：守护进程运行中用轻微明暗变化提示「在跑」（0.55~1.0，周期 1.4 秒）。 */
@Composable
private fun BreathingBadge(text: String, color: Color) {
    val transition = rememberInfiniteTransition(label = "settingsBadgeBreath")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "settingsBadgeAlpha"
    )
    Box(modifier = Modifier.alpha(alpha)) {
        Badge(containerColor = color, contentColor = Color.White) {
            Text(text = text, fontSize = 12.sp, color = Color.White)
        }
    }
}

/** 输入框配色：跟随卡片底色与 Miuix 主题，避免默认 Material 灰底。 */
@Composable
private fun fieldColors(): androidx.compose.material3.TextFieldColors {
    val cs = MiuixTheme.colorScheme
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = cs.onSurface,
        unfocusedTextColor = cs.onSurface,
        focusedContainerColor = cs.surfaceContainerHigh,
        unfocusedContainerColor = cs.surfaceContainerHigh,
        cursorColor = cs.primary,
        focusedBorderColor = cs.primary,
        unfocusedBorderColor = cs.outline,
        focusedPlaceholderColor = cs.onSurfaceVariantSummary,
        unfocusedPlaceholderColor = cs.onSurfaceVariantSummary
    )
}

/** 开关配色：统一走 Miuix 主题色，与其余三页保持一致。 */
@Composable
private fun switchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = MiuixTheme.colorScheme.primary,
    checkedBorderColor = Color.Transparent,
    uncheckedThumbColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    uncheckedTrackColor = MiuixTheme.colorScheme.surfaceContainerHigh,
    uncheckedBorderColor = MiuixTheme.colorScheme.outline
)