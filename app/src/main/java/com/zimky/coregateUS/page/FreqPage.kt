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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zimky.coregateUS.core.CoreConfig
import com.zimky.coregateUS.core.FreqCap
import com.zimky.coregateUS.core.RootShell
import com.zimky.coregateUS.core.SysProbe
import com.zimky.coregateUS.ui.BottomSpacer
import com.zimky.coregateUS.ui.CardDivider
import com.zimky.coregateUS.ui.Gap
import com.zimky.coregateUS.ui.HintText
import com.zimky.coregateUS.ui.LabelText
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
 * 第 3 页：日常限频。
 *
 * 行为契约与旧 FreqPage.java / WebUI 的 refreshFreqCap 一致：
 *  - 目标档位是只读的，因为 CAP_LITTLE_MHZ / CAP_BIG_MHZ / CAP_GPU_MHZ / CAP_GPU_PWRLEVEL
 *    硬编码在 service.sh 里，APK 不参与决策；
 *  - 本页只负责「开/关」这一个写操作，其余全是展示。
 *
 * 视觉：Miuix [Card] + [Badge]，与 PolicyPage / LockPage 同一套组件语言。
 */
@Composable
fun FreqPage(
    cfg: CoreConfig,
    snapshot: SysProbe.Snapshot?,
    packagesRaw: String,
    sh: RootShell,
    onRefresh: () -> Unit,
    save: (CoreConfig, String?, ((Boolean, String) -> Unit)?) -> Unit
) {
    val cs = MiuixTheme.colorScheme
    val s = snapshot
    val limitOn = cfg.limitFreq

    // 四项判定：无快照时全部按「读不到」处理，与旧版一致
    val little: FreqCap.Item
    val big: FreqCap.Item
    val gpu: FreqCap.Item
    val pwr: FreqCap.Item
    if (s == null) {
        little = FreqCap.Item("小核簇", "≤ ${FreqCap.LITTLE_MHZ} MHz", "读不到", FreqCap.STATE_UNKNOWN)
        big = FreqCap.Item("大核簇", "≤ ${FreqCap.BIG_MHZ} MHz", "读不到", FreqCap.STATE_UNKNOWN)
        gpu = FreqCap.Item("GPU", "≤ ${FreqCap.GPU_MHZ} MHz", "读不到", FreqCap.STATE_UNKNOWN)
        pwr = FreqCap.Item("GPU 功耗档", "= ${FreqCap.GPU_PWRLEVEL}", "读不到", FreqCap.STATE_UNKNOWN)
    } else {
        little = FreqCap.judgeLittle(s, limitOn)
        big = FreqCap.judgeBig(s, limitOn)
        gpu = FreqCap.judgeGpu(s, limitOn)
        pwr = FreqCap.judgePwr(s, limitOn)
    }
    val items = listOf(little, big, gpu, pwr)

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

        // ── 卡片一：限频开关 ─────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(text = "日常限频", color = cs.onSurface, fontSize = 16.sp)
                Gap(12.dp)

                SwitchRow(
                    title = "限制 CPU / GPU 上限频率",
                    desc = if (limitOn) {
                        "已开启：小核、大核、GPU 均压到目标档位，功耗更低。"
                    } else {
                        "未开启：CPU 与 GPU 按系统默认策略运行。"
                    }
                ) {
                    Switch(
                        checked = cfg.limitFreq,
                        onCheckedChange = { on ->
                            save(cfg.copy(limitFreq = on), null) { ok, msg ->
                                if (ok) {
                                    Toaster.show(if (on) "已开启日常限频" else "已关闭日常限频，正在恢复原始频率")
                                    // 频率是异步写 sysfs 的，成功后补一次刷新才看得到结果
                                    onRefresh()
                                } else {
                                    Toaster.show("保存失败：$msg", true)
                                }
                            }
                        },
                        colors = switchColors()
                    )
                }

                CardDivider()
                HintText(
                    "关闭后会优先按开启前的快照恢复各簇上限；没有快照时回退到硬件真实最大合法档。" +
                        "系统重启会整体还原，不写任何系统分区。"
                )
            }
        }

        // ── 卡片二：限频实况 ─────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(text = "限频实况", color = cs.onSurface, fontSize = 16.sp)
                Gap(12.dp)

                val allOk = items.all { it.state == FreqCap.STATE_OK }
                val anyOver = items.any { it.state == FreqCap.STATE_OVER }

                when {
                    !limitOn -> {
                        StateBadge("未开启", Semantic.OFF)
                        Gap(10.dp)
                        SubText("限频关闭，CPU / GPU 按系统默认策略运行。")
                    }
                    allOk -> {
                        StateBadge("已生效", Semantic.OK)
                        Gap(10.dp)
                        SubText("四项全部落在目标档位。")
                    }
                    anyOver -> {
                        StateBadge("未完全生效", Semantic.ERR)
                        Gap(10.dp)
                        SubText(
                            "有项目仍未压到目标值，可能刚改动、还没到下一轮循环，" +
                                "或被其他模块 / 系统策略改写。"
                        )
                    }
                    else -> {
                        BreathingBadge("部分生效", Semantic.WARN)
                        Gap(10.dp)
                        SubText("部分项目读不到实测值，稍等片刻再看。")
                    }
                }

                CardDivider()

                items.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LabelText(item.name)
                        SubText(item.actual, modifier = Modifier.padding(start = 8.dp))
                        // 尾部标签靠右
                        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                            StateBadge(
                                FreqCap.stateText(item.state),
                                Color(FreqCap.stateColor(item.state))
                            )
                        }
                    }
                }

                Gap(6.dp)
                HintText("实测值来自 scaling_max_freq / max_gpuclk / max_pwrlevel，随系统刷新。")
            }
        }

        // ── 卡片三：限频目标档位 ─────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(text = "限频目标档位", color = cs.onSurface, fontSize = 16.sp)
                Gap(12.dp)
                SubText("以下数值硬编码在模块的 service.sh 中，APK 只读取不修改。")
                CardDivider()
                TargetRow("小核簇", "≤ ${FreqCap.LITTLE_MHZ} MHz")
                TargetRow("大核簇", "≤ ${FreqCap.BIG_MHZ} MHz")
                TargetRow("GPU", "≤ ${FreqCap.GPU_MHZ} MHz")
                TargetRow("GPU 功耗档", "= ${FreqCap.GPU_PWRLEVEL}（0 最高，数字越大越省）")
                Gap(10.dp)
                HintText(
                    "写入时 GPU 频率会额外 +800000 做对齐，内核再自行落到最近的真实档位，" +
                        "因此实测值可能是 500000000 这种整数。"
                )
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

/** 呼吸徽标：部分生效时用轻微明暗变化提示「还在等下一轮循环」（0.55~1.0，周期 1.4 秒）。 */
@Composable
private fun BreathingBadge(text: String, color: Color) {
    val transition = rememberInfiniteTransition(label = "freqBadgeBreath")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "freqBadgeAlpha"
    )
    Box(modifier = Modifier.alpha(alpha)) {
        Badge(containerColor = color, contentColor = Color.White) {
            Text(text = text, fontSize = 12.sp, color = Color.White)
        }
    }
}

/** 「名称 —— 目标值」一行，右对齐数值。 */
@Composable
private fun TargetRow(name: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LabelText(name)
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
            SubText(value)
        }
    }
}

/** 开关配色：统一走 Miuix 主题色，与 PolicyPage 完全一致。 */
@Composable
private fun switchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = MiuixTheme.colorScheme.primary,
    checkedBorderColor = Color.Transparent,
    uncheckedThumbColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    uncheckedTrackColor = MiuixTheme.colorScheme.surfaceContainerHigh,
    uncheckedBorderColor = MiuixTheme.colorScheme.outline
)