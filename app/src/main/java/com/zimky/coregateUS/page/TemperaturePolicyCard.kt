package com.zimky.coregateUS.page

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Switch
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zimky.coregateUS.core.CoreConfig
import com.zimky.coregateUS.core.SysProbe
import com.zimky.coregateUS.ui.*
import com.zimky.coregateUS.ui.component.FloatingBottomBar
import com.zimky.coregateUS.ui.component.FloatingBottomBarItem
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

/** 电池温度策略；高温额外锁核跳过游戏，配置交由模块执行。 */
@Composable
internal fun TemperaturePolicyCard(
    cfg: CoreConfig,
    snapshot: SysProbe.Snapshot?,
    save: (CoreConfig, String?, ((Boolean, String) -> Unit)?) -> Unit
) {
    val cs = MiuixTheme.colorScheme
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    var thresholdText by remember { mutableStateOf(cfg.temperatureThreshold.toString()) }
    var focused by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var extraDraft by remember(cfg.temperatureLockCores) {
        mutableStateOf(CoreConfig.normalizeTemperatureCores(cfg.temperatureLockCores).split(',').mapNotNull { it.toIntOrNull() }.toSet())
    }
    LaunchedEffect(cfg.temperatureThreshold, focused) {
        if (!focused) thresholdText = cfg.temperatureThreshold.toString()
    }
    fun persist(next: CoreConfig) {
        if (busy || !cfg.loaded) return
        busy = true
        save(next, null) { ok, msg ->
            busy = false
            Toaster.show(if (ok) "温度策略已保存" else "保存失败：$msg", !ok)
        }
    }
    fun commitThreshold() {
        if (busy || !cfg.loaded) return
        val threshold = CoreConfig.clampTemperature(thresholdText.toIntOrNull() ?: 40)
        thresholdText = threshold.toString()
        if (threshold != cfg.temperatureThreshold) persist(cfg.copy(temperatureThreshold = threshold))
        keyboard?.hide()
        focus.clearFocus()
    }
    val parts = snapshot?.temperatureRaw.orEmpty().split('|')
    val stamp = parts.getOrNull(4)?.toLongOrNull() ?: 0L
    val age = System.currentTimeMillis() / 1000 - stamp
    val fresh = snapshot?.daemonAlive == true && stamp > 0 && age in 0..(cfg.pollInterval + 60).toLong()
    val sourceMatches = parts.getOrNull(5) == "battery"
    val matches = parts.getOrNull(2) == cfg.temperatureMode &&
        parts.getOrNull(3)?.toIntOrNull() == cfg.temperatureThreshold
    val status = when {
        !cfg.temperatureEnabled -> "已关闭"
        !cfg.enabled -> "模块已停用"
        snapshot?.daemonAlive != true -> "等待模块运行"
        !fresh -> "等待模块更新"
        !sourceMatches -> "请更新配套模块"
        !matches || parts.getOrNull(6) != cfg.temperatureLockCores -> "等待策略同步"
        parts[0] == "game_skipped" -> "游戏中，跳过高温锁核"
        parts[0] == "scene_skipped" -> "自动恢复中，暂停额外锁核"
        parts[0] == "no_extra" -> "尚未选择额外核心"
        parts[0] == "active" -> "已触发"
        parts[0] == "watching" -> "监测中"
        parts[0] == "unavailable" -> "传感器不可用"
        else -> "等待模块执行"
    }
    val temp = if (fresh && sourceMatches && cfg.temperatureEnabled) parts.getOrNull(1)?.toIntOrNull() else null
    val surface by rememberUpdatedState(cs.surfaceContainer)
    val backdrop = rememberLayerBackdrop { drawRect(surface) }
    val available = cfg.loaded && !busy

    SectionHeading("温度策略", "按电池温度调整核心")
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SwitchRow(
                title = "启用温度策略",
                desc = "达到阈值时执行所选动作，降温后恢复日常策略。",
                trailing = {
                    Switch(checked = cfg.temperatureEnabled, enabled = available,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                            checkedTrackColor = cs.primary,
                            checkedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                            uncheckedThumbColor = cs.onSurfaceVariantSummary,
                            uncheckedTrackColor = cs.surfaceContainerHigh,
                            uncheckedBorderColor = cs.outline),
                        onCheckedChange = { persist(cfg.copy(temperatureEnabled = it)) })
                }
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(if (temp != null) String.format(Locale.ROOT, "%.1f ℃", temp / 1000.0) else "— ℃",
                        color = cs.primary, fontSize = 30.sp)
                    Text("电池温度", color = cs.onSurfaceVariantSummary, fontSize = 12.sp)
                }
                Badge(containerColor = cs.primary.copy(alpha = 0.12f), contentColor = cs.primary) {
                    AnimatedGradientText(status, cs.primary)
                }
            }
            HairLine()
            Text("高温时的动作", color = cs.onSurface, fontSize = 14.sp)
            Box(Modifier.fillMaxWidth()) {
                
                FloatingBottomBar(
                    modifier = Modifier.fillMaxWidth(),
                    selectedIndex = if (cfg.temperatureMode == "unlock") 1 else 0,
                    onSelected = { index ->
                        val mode = if (index == 1) "unlock" else "lock"
                        if (available && mode != cfg.temperatureMode) persist(cfg.copy(temperatureMode = mode))
                    },
                    backdrop = backdrop, tabsCount = 2, compact = true
                ) { activate ->
                    listOf("高温锁核", "高温解封").forEachIndexed { index, label ->
                        val selected = (cfg.temperatureMode == "unlock") == (index == 1)
                        FloatingBottomBarItem(selected = selected, onClick = { if (available) activate(index) }) {
                            Text(label, fontSize = 13.sp, maxLines = 1,
                                color = if (selected) cs.primary else cs.onSurfaceVariantSummary)
                        }
                    }
                }
            }
            SubText(if (cfg.temperatureMode == "unlock")
                "达到阈值后恢复全部核心并撤销 CoreGate 限频；这项选择不保证降低温度。"
                else "达到阈值后，在日常封锁之外额外封锁下方选中的核心；降温后仅撤销额外封锁。名单内游戏运行时跳过，避免高温打断游戏性能。")
            HintText("使用 Android 电池温度口径，与 CPU / SoC 热点温度不同；不同工具的刷新时间可能造成少量差异。")
            if (cfg.temperatureMode == "lock") {
                Text("高温额外锁核", color = cs.onSurface, fontSize = 14.sp)
                SubText("日常封锁：${if (cfg.lockCores == "auto") "自动选择或手动核心名单" else cfg.lockCores}。以下选择独立保存；重复选择已封锁的核心不会增加封锁数量。CPU0 始终保留。")
                val cores = snapshot?.cores.orEmpty().sortedBy { it.id }.filter { it.id > 0 }
                if (cores.isEmpty()) HintText("等待读取核心列表后可自定义。")
                cores.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { core ->
                            val selected = core.id in extraDraft
                            TextButton(
                                text = "CPU${core.id}${if (selected) " ✓" else ""}",
                                enabled = available,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.textButtonColors(textColor = if (selected) cs.primary else cs.onSurfaceVariantSummary),
                                onClick = { extraDraft = if (selected) extraDraft - core.id else extraDraft + core.id }
                            )
                        }
                    }
                }
                SubText("已保存的额外核心：${cfg.temperatureLockCores.ifBlank { "未选择（高温时不会额外锁核）" }}")
                TextButton(text = "保存额外核心", enabled = available && extraDraft.sorted().joinToString(",") != cfg.temperatureLockCores,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = { persist(cfg.copy(temperatureLockCores = extraDraft.sorted().joinToString(","))) })
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = thresholdText,
                    onValueChange = { thresholdText = it.filter(Char::isDigit).take(3) },
                    label = { Text("触发阈值", fontSize = 12.sp, color = cs.onSurfaceVariantSummary) },
                    shape = CircleShape,
                    singleLine = true, enabled = available,
                    modifier = Modifier.width(96.dp).onFocusChanged { focused = it.isFocused },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commitThreshold() }),
                    textStyle = TextStyle(fontSize = 14.sp, color = cs.onSurface),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = cs.primary, unfocusedBorderColor = cs.outline,
                        focusedContainerColor = cs.surfaceContainerHigh,
                        unfocusedContainerColor = cs.surfaceContainerHigh, cursorColor = cs.primary)
                )
                Text("℃", color = cs.onSurfaceVariantSummary, fontSize = 16.sp)
                TextButton(text = if (busy) "保存中" else "保存阈值", enabled = available,
                    cornerRadius = 100.dp,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = { commitThreshold() })
            }
            HintText("35～60℃，默认 40℃。降至阈值以下 3℃时退出温度策略，按轮询间隔检测。")
            HintText(if (cfg.temperatureMode == "lock")
                "高温额外锁核仅在日常锁核场景执行，游戏、已开启的录屏/相机恢复及充电恢复优先。游戏须加入游戏名单且被识别；前台识别失败时无法保证跳过。未选额外核心时不新增封锁。"
                else "高温解封恢复全部核心并撤销限频；总开关与安全模式优先。")
            HintText("读取不到有效电池温度时撤销温度策略；系统自身温控继续生效。配置由守护进程执行，关闭 App 后仍生效。")
        }
    }
}
