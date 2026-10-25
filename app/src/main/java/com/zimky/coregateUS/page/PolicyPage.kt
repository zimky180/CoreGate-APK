package com.zimky.coregateUS.page

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zimky.coregateUS.core.CoreConfig
import com.zimky.coregateUS.core.RootShell
import com.zimky.coregateUS.core.SysProbe
import com.zimky.coregateUS.ui.BottomSpacer
import com.zimky.coregateUS.ui.Gap
import com.zimky.coregateUS.ui.HGap
import com.zimky.coregateUS.ui.HairLine
import com.zimky.coregateUS.ui.HintText
import com.zimky.coregateUS.ui.NavRow
import com.zimky.coregateUS.ui.PAGE_BOTTOM_INSET
import com.zimky.coregateUS.ui.Semantic
import com.zimky.coregateUS.ui.SubText
import com.zimky.coregateUS.ui.SwitchRow
import com.zimky.coregateUS.ui.Toaster
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 第 2 页：录屏/相机恢复、充电策略、轮询间隔、当前场景判断。
 *
 * 与旧 PolicyPage 对齐的三条关键行为（一条都不能动）：
 *  1. 开关是「由 cfg 单向驱动」，onCheckedChange 里只做 save，不需要 syncing 标记；
 *  2. 轮询间隔只在失焦时提交，且与当前值相同则不写盘（避免无意义 root）；
 *  3. 输入框获得焦点期间，外部刷新回来的数字不覆盖用户正在编辑的内容。
 *
 * 视觉：Miuix [Card] + [Badge]，与 KernelSU 管理器首页同一套组件语言。
 */
@Composable
fun PolicyPage(
    cfg: CoreConfig,
    snapshot: SysProbe.Snapshot?,
    packagesRaw: String,
    sh: RootShell,
    onRefresh: () -> Unit,
    save: (CoreConfig, String?, ((Boolean, String) -> Unit)?) -> Unit,
    /** 冻结管理二级页里的「已冻结」数量快照，null 表示还没读到 */
    frozenCount: Int?,
    onOpenFreeze: () -> Unit,
    onOpenSpoof: () -> Unit
) {
    val cs = MiuixTheme.colorScheme
    val s = snapshot
    val daemonAlive = s?.daemonAlive == true
    val total = s?.cores?.size ?: 0
    val onlineCount = s?.onlineCount ?: 0
    val allOnline = total > 0 && onlineCount == total
    val lockedCount = (total - onlineCount).coerceAtLeast(0)
    val pkg = s?.statePkg?.takeIf { it.isNotBlank() }

    // 冻结摘要：与主页卡片三严格同一套措辞，用户在两页看到同一件事必须读到同一句话
    val freezeSummary = when {
        frozenCount == null -> "还没探到"
        frozenCount == 0 -> "当前没有冻结任何服务"
        else -> "已冻结 $frozenCount 个服务"
    }
    // 输入框文本与焦点状态
    var intervalText by remember { mutableStateOf(cfg.pollInterval.toString()) }
    var inputFocused by remember { mutableStateOf(false) }

    // 外部刷新回来的配置变化：仅在未聚焦时同步显示值
    if (!inputFocused && intervalText != cfg.pollInterval.toString()) {
        intervalText = cfg.pollInterval.toString()
    }

    Column(
        modifier = Modifier
            // 一级页必须自己铺满整屏：
            // 这四页是常驻 composition 的，靠 PageSlot 的 zIndex 叠在一起。
            // 根 Column 若只写 fillMaxWidth，竖向高度会随内容收缩，
            // 内容不足一屏时下面那块就不属于本页 —— 既会透出下层页，
            // 点击也会漏到别的页去（真机反馈的「按钮点不着」）。
            // fillMaxSize 同时给 verticalScroll 一个有界高度的视口。
            .fillMaxSize()
            // 白底：不依赖主题色，保证四页观感一致
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = PAGE_BOTTOM_INSET),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

        // ── 卡片一：录屏 / 相机自动恢复 ──────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(text = "录屏 / 相机自动恢复", color = cs.onSurface, fontSize = 16.sp)
                Gap(12.dp)

                SwitchRow(
                    title = "录屏或拍照时恢复全部核心",
                    desc = "开启后，检测到录屏或相机正在使用时自动解锁所有核心，避免录制卡顿、拍照掉帧。",
                    trailing = {
                        Switch(
                            checked = cfg.captureBoost,
                            onCheckedChange = { on ->
                                save(cfg.copy(captureBoost = on), null) { ok, _ ->
                                    if (ok) Toaster.show(if (on) "已开启录屏/相机自动恢复" else "已关闭录屏/相机自动恢复")
                                }
                            },
                            colors = switchColors()
                        )
                    }
                )

                Gap(10.dp)
                HintText("检测名单内置了常见录屏与相机包名（含 ColorOS / OxygenOS）。关闭此项后，模块只认 packages.txt 里的游戏列表。")
            }
        }

        // ── 卡片二：充电策略 ─────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(text = "充电策略", color = cs.onSurface, fontSize = 16.sp)
                Gap(12.dp)

                SwitchRow(
                    title = "充电时不锁核",
                    desc = "插上充电器时保持全部核心在线。充电时发热本就明显，锁核收益有限，而放开核心能让充电管理和后台任务更快完成。",
                    trailing = {
                        Switch(
                            checked = cfg.noLockCharging,
                            onCheckedChange = { on ->
                                save(cfg.copy(noLockCharging = on), null) { ok, _ ->
                                    if (ok) Toaster.show(if (on) "已开启充电不锁核" else "已关闭充电不锁核")
                                }
                            },
                            colors = switchColors()
                        )
                    }
                )

                Gap(10.dp)
                HintText("默认关闭：即便在充电，也照常锁核。")
            }
        }

        // ── 卡片三：轮询间隔 ─────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(text = "轮询间隔", color = cs.onSurface, fontSize = 16.sp)
                Gap(10.dp)
                SubText("守护进程每隔多少秒检查一次前台应用。间隔越短响应越快，但唤醒次数越多，待机功耗也略高；间隔越长越省电，但进出游戏的反应会变慢。")
                Gap(12.dp)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = intervalText,
                        onValueChange = { v ->
                            // 只接受数字，最多 3 位，避免用户输入出奇怪内容
                            intervalText = v.filter { it.isDigit() }.take(3)
                        },
                        singleLine = true,
                        modifier = Modifier
                            .width(84.dp)
                            .onFocusChanged { state ->
                                val wasFocused = inputFocused
                                inputFocused = state.isFocused
                                // 失焦即提交
                                if (wasFocused && !state.isFocused) {
                                    commitInterval(intervalText, cfg) { text, newCfg ->
                                        intervalText = text
                                        if (newCfg != null) {
                                            save(newCfg, null) { ok, _ ->
                                                if (ok) Toaster.show("轮询间隔已设为 ${newCfg.pollInterval} 秒")
                                            }
                                        }
                                    }
                                }
                            },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = TextStyle(fontSize = 14.sp, color = cs.onSurface),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = cs.onSurface,
                            unfocusedTextColor = cs.onSurface,
                            focusedBorderColor = cs.primary,
                            unfocusedBorderColor = cs.outline,
                            focusedContainerColor = cs.surfaceContainerHigh,
                            unfocusedContainerColor = cs.surfaceContainerHigh,
                            cursorColor = cs.primary
                        )
                    )
                    HGap(10.dp)
                    Text(text = "秒", color = cs.onSurfaceVariantSummary, fontSize = 14.sp)
                }

                Gap(10.dp)
                HintText("允许范围 5 ~ 600 秒，默认 30 秒。留空或填非法值将回退为 30。")
            }
        }

        // ── 卡片四：当前场景判断 ─────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(text = "当前场景判断", color = cs.onSurface, fontSize = 16.sp)
                Gap(12.dp)

                if (!daemonAlive) {
                    StateBadge("未运行", cs.error)
                    Gap(10.dp)
                    SubText("守护进程未运行，场景判断暂停。")
                } else if (allOnline) {
                    StateBadge("已放开", Semantic.ACC)
                    Gap(10.dp)
                    SubText(
                        if (pkg != null) {
                            "前台应用 $pkg 命中游戏或录屏/相机名单，全部核心已恢复。"
                        } else {
                            "全部核心在线：可能命中了游戏/录屏名单，或锁核总开关被关闭。"
                        }
                    )
                } else {
                    BreathingBadge("锁核中", cs.primary)
                    Gap(10.dp)
                    SubText(
                        if (pkg != null) {
                            "前台应用 $pkg 未命中游戏/录屏名单。"
                        } else {
                            "当前前台应用无法读取（ColorOS 限制），按非游戏处理。"
                        }
                    )
                    Gap(10.dp)
                    SubText("已封锁 $lockedCount 个核心以节省功耗。")
                }
            }
        }

        // ── 卡片五：更多策略（点进各自的二级页）───────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(text = "更多策略", color = cs.onSurface, fontSize = 16.sp)
                Gap(4.dp)

                NavRow(
                    title = "冻结管理",
                    summary = freezeSummary,
                    onClick = onOpenFreeze
                )
                HairLine()
                NavRow(
                    title = "老机型模拟",
                    summary = "让抖音以为这是老手机，开启省电优化",
                    onClick = onOpenSpoof
                )


                Gap(10.dp)
                HintText("以上两项都默认关闭，改之前请先看清各自页面的说明。")
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

/** 呼吸徽标：锁核进行中时用轻微明暗变化提示「正在工作」（0.55~1.0，周期 1.4 秒）。 */
@Composable
private fun BreathingBadge(text: String, color: Color) {
    val transition = rememberInfiniteTransition(label = "policyBadgeBreath")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "policyBadgeAlpha"
    )
    Box(modifier = Modifier.alpha(alpha)) {
        Badge(containerColor = color, contentColor = Color.White) {
            Text(text = text, fontSize = 12.sp, color = Color.White)
        }
    }
}

/**
 * 提交轮询间隔。
 *
 * @param text 输入框当前文本
 * @param cfg  当前配置
 * @param done text = 回填输入框的规范化文本；newCfg = 需要写盘的新配置（null 表示不需要写）
 */
private fun commitInterval(
    text: String,
    cfg: CoreConfig,
    done: (text: String, newCfg: CoreConfig?) -> Unit
) {
    val raw = text.trim().toIntOrNull()
    // 留空或非法：回退默认 30
    val normalized = if (raw == null) 30 else CoreConfig.clampInterval(raw)
    // 与当前配置一致：不写盘，只把输入框回填成规范值
    if (normalized == cfg.pollInterval) {
        done(normalized.toString(), null)
        return
    }
    done(normalized.toString(), cfg.copy(pollInterval = normalized))
}

/** 开关配色：统一走 Miuix 主题色。 */
@Composable
private fun switchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = MiuixTheme.colorScheme.primary,
    checkedBorderColor = Color.Transparent,
    uncheckedThumbColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    uncheckedTrackColor = MiuixTheme.colorScheme.surfaceContainerHigh,
    uncheckedBorderColor = MiuixTheme.colorScheme.outline
)