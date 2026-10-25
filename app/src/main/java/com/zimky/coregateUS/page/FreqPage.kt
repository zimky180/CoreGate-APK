package com.zimky.coregateUS.page

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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
 * v2.2.4 起目标档位可自定义（存放在 config.json 的 cap_* 四个键里）：
 *  - 卡片一只管总开关；
 *  - 卡片二比对「实测值 vs 目标值」，目标值来自 [FreqCap.Targets.from]，
 *    因此改完阈值后实况区的「≤ XXX MHz」会立刻跟着变，不会继续显示旧硬编码；
 *  - 卡片三就是阈值编辑器：CPU / GPU 频率用档位滑杆（吸附内核实测的
 *    scaling_available_frequencies，因为本机档位不等比，按百分比或固定步进
 *    算出来的值大概率不是合法档，写进去会被内核回弹），GPU 功耗档用 -/+ 步进器。
 *
 * 保存策略：拖滑杆时只改内存态，松手（onValueChangeFinished）才落盘一次，
 * 避免一次拖动打出几十次 root 写盘。
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

    // 当前生效的目标档位：0 / -1 表示「跟随默认」，由 Targets.from 归一化成常量
    val targets = FreqCap.Targets.from(cfg)

    // 四项判定：无快照时全部按「读不到」处理，但目标文案仍取当前设置值
    val little: FreqCap.Item
    val big: FreqCap.Item
    val gpu: FreqCap.Item
    val pwr: FreqCap.Item
    if (s == null) {
        little = FreqCap.Item("小核簇", "≤ ${targets.effLittle} MHz", "读不到", FreqCap.STATE_UNKNOWN)
        big = FreqCap.Item("大核簇", "≤ ${targets.effBig} MHz", "读不到", FreqCap.STATE_UNKNOWN)
        gpu = FreqCap.Item("GPU", "≤ ${targets.effGpu} MHz", "读不到", FreqCap.STATE_UNKNOWN)
        pwr = FreqCap.Item("GPU 功耗档", "= ${targets.effPwr}", "读不到", FreqCap.STATE_UNKNOWN)
    } else {
        little = FreqCap.judgeLittle(s, limitOn, targets)
        big = FreqCap.judgeBig(s, limitOn, targets)
        gpu = FreqCap.judgeGpu(s, limitOn, targets)
        pwr = FreqCap.judgePwr(s, limitOn, targets)
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

        // ── 卡片三：限频目标档位（可自定义） ─────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(text = "限频目标档位", color = cs.onSurface, fontSize = 16.sp)
                Gap(12.dp)
                SubText("拖动滑杆设定上限，松手即写入 config.json，模块下一轮循环生效。")
                CardDivider()

                val littleAvail = s?.littleAvail ?: emptyList()
                val bigAvail = s?.bigAvail ?: emptyList()
                val gpuAvail = s?.gpuAvail ?: emptyList()

                // 小核簇
                FreqSliderRow(
                    name = "小核簇",
                    avail = littleAvail,
                    currentMhz = targets.effLittle,
                    fallbackMhz = FreqCap.LITTLE_MHZ,
                    onCommit = { mhz ->
                        save(cfg.copy(capLittleMhz = mhz), null) { ok, msg ->
                            if (ok) { Toaster.show("小核簇上限已设为 $mhz MHz"); onRefresh() }
                            else Toaster.show("保存失败：$msg", true)
                        }
                    }
                )

                CardDivider()

                // 大核簇
                FreqSliderRow(
                    name = "大核簇",
                    avail = bigAvail,
                    currentMhz = targets.effBig,
                    fallbackMhz = FreqCap.BIG_MHZ,
                    onCommit = { mhz ->
                        save(cfg.copy(capBigMhz = mhz), null) { ok, msg ->
                            if (ok) { Toaster.show("大核簇上限已设为 $mhz MHz"); onRefresh() }
                            else Toaster.show("保存失败：$msg", true)
                        }
                    }
                )

                CardDivider()

                // GPU 频率
                FreqSliderRow(
                    name = "GPU",
                    avail = gpuAvail,
                    currentMhz = targets.effGpu,
                    fallbackMhz = FreqCap.GPU_MHZ,
                    availInHz = true,
                    onCommit = { mhz ->
                        save(cfg.copy(capGpuMhz = mhz), null) { ok, msg ->
                            if (ok) { Toaster.show("GPU 上限已设为 $mhz MHz"); onRefresh() }
                            else Toaster.show("保存失败：$msg", true)
                        }
                    }
                )

                CardDivider()

                // GPU 功耗档上限：优先用实测档数（num_pwrlevels - 1），
                // 其次用档位表长度 - 1，最后才回退到内置的 13，
                // 避免把用户卡在默认档上无法继续上调。
                val pwrMax = when {
                    (s?.gpuNumLevels ?: 0) > 1 -> s!!.gpuNumLevels - 1
                    gpuAvail.isNotEmpty() -> gpuAvail.size - 1
                    else -> FreqCap.GPU_PWRLEVEL
                }
                StepperRow(
                    name = "GPU 功耗档",
                    value = targets.effPwr,
                    hint = "0 最高，数字越大越省",
                    canMinus = targets.effPwr > 0,
                    canPlus = targets.effPwr < pwrMax,
                    onMinus = {
                        val v = (targets.effPwr - 1).coerceAtLeast(0)
                        save(cfg.copy(capGpuPwrLevel = v), null) { ok, msg ->
                            if (ok) { Toaster.show("GPU 功耗档已设为 $v"); onRefresh() }
                            else Toaster.show("保存失败：$msg", true)
                        }
                    },
                    onPlus = {
                        val v = (targets.effPwr + 1).coerceAtMost(pwrMax)
                        save(cfg.copy(capGpuPwrLevel = v), null) { ok, msg ->
                            if (ok) { Toaster.show("GPU 功耗档已设为 $v"); onRefresh() }
                            else Toaster.show("保存失败：$msg", true)
                        }
                    }
                )

                val allDefault = cfg.capLittleMhz == 0 && cfg.capBigMhz == 0 &&
                    cfg.capGpuMhz == 0 && cfg.capGpuPwrLevel < 0
                if (!allDefault) {
                    Gap(10.dp)
                    ResetRow {
                        save(
                            cfg.copy(
                                capLittleMhz = 0,
                                capBigMhz = 0,
                                capGpuMhz = 0,
                                capGpuPwrLevel = -1
                            ),
                            null
                        ) { ok, msg ->
                            if (ok) { Toaster.show("已恢复默认目标档位"); onRefresh() }
                            else Toaster.show("保存失败：$msg", true)
                        }
                    }
                }

                Gap(10.dp)
                HintText(
                    if (littleAvail.isEmpty()) {
                        "未读到内核档位表，滑杆按 100 MHz 步进近似取值；" +
                            "写入后内核会自动落到最近的真实档位。"
                    } else {
                        "滑杆只吸附内核实测的 scaling_available_frequencies，" +
                            "避免写进非法档位被内核回弹。"
                    } + "写入时 GPU 频率额外 +800000 做对齐，" +
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

/**
 * 档位滑杆一行：标题 + 当前值，下方是可拖动的滑杆。
 *
 * 滑杆的取值来源有两种：
 *  - 有内核实测档位表（[avail]，kHz）时，索引直接映射到真实档位，
 *    拖到哪一格写入的就是内核认可的合法值，避免被内核回弹；
 *  - 读不到档位表时降级为 100 MHz 步进的等差数列，写入后由内核自行就近落档。
 *
 * 拖动过程中只更新本地 state，松手（onValueChangeFinished）才回调 [onCommit] 落盘，
 * 否则一次拖动会打出几十次 root 写盘。
 */
@Composable
private fun FreqSliderRow(
    name: String,
    avail: List<Long>,
    currentMhz: Int,
    fallbackMhz: Int,
    /** 档位表单位是否为 Hz（GPU 的 kgsl 节点是 Hz，CPU 的 cpufreq 节点是 kHz）。 */
    availInHz: Boolean = false,
    onCommit: (Int) -> Unit
) {
    val cs = MiuixTheme.colorScheme

    // MHz 档位表：CPU 的 avail 是 kHz 除以 1000，GPU 的是 Hz 除以 1000000
    val mhzList = remember(avail, availInHz) {
        if (avail.isEmpty()) {
            emptyList()
        } else {
            val div = if (availInHz) 1_000_000L else 1_000L
            avail.map { (it / div).toInt() }.filter { it > 0 }.distinct().sorted()
        }
    }
    val hasTable = mhzList.isNotEmpty()
    val steps = if (hasTable) mhzList else listOf(
        600, 800, 1000, 1200, 1400, 1600, 1800, 2000, 2200, 2400, 2600, 2800, 3000, 3200, 3400
    )

    // 当前值吸附到最近合法档位；外部改了配置（比如点「恢复默认」）会跟着重算
    val targetIdx = remember(steps, currentMhz) {
        var best = 0
        var bestDiff = Int.MAX_VALUE
        steps.forEachIndexed { i, v ->
            val d = kotlin.math.abs(v - currentMhz)
            if (d < bestDiff) { bestDiff = d; best = i }
        }
        best
    }
    var idx by remember(targetIdx) { mutableIntStateOf(targetIdx) }
    val shownMhz = steps.getOrElse(idx) { fallbackMhz }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LabelText(name)
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                Text(
                    text = "≤ $shownMhz MHz",
                    color = cs.onSurface,
                    fontSize = 13.sp
                )
            }
        }
        Gap(6.dp)
        MiuixSlider(
            index = idx,
            maxIndex = steps.size - 1,
            onIndexChange = { idx = it },
            onCommit = { onCommit(steps.getOrElse(idx) { fallbackMhz }) }
        )
        Gap(2.dp)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            HintText("${steps.firstOrNull() ?: fallbackMhz}")
            HintText(if (hasTable) "${steps.size} 档" else "100 MHz 步进")
            HintText("${steps.lastOrNull() ?: fallbackMhz}")
        }
    }
}

/**
 * 自绘滑杆。
 *
 * 项目里没有现成的 Slider 组件，Material3 的 Slider 又与 Miuix 的视觉语言不合，
 * 因此手写一个最小可用版本：圆角轨道 + 实心进度 + 白色圆钮，
 * 配色全部走 [MiuixTheme.colorScheme]，与其余卡片一致。
 *
 * 交互用 detectHorizontalDragGestures + detectTapGestures 组合：
 * 点一下跳到该位置，按住拖动连续更新，松手回调 [onCommit]。
 */
@Composable
private fun MiuixSlider(
    index: Int,
    maxIndex: Int,
    onIndexChange: (Int) -> Unit,
    onCommit: () -> Unit
) {
    val cs = MiuixTheme.colorScheme
    val trackH = 6.dp
    val knobR = 9.dp
    var widthPx by remember { mutableIntStateOf(0) }

    val fraction = if (maxIndex <= 0) 0f else index.toFloat() / maxIndex.toFloat()

    // 拖动回调里若直接读 index，会拿到 pointerInput 建立那一刻的旧值，
    // 快速拖动时 pick 的「没变化就不回调」判断就会失准、漏掉末段位移。
    // 用 rememberUpdatedState 保证回调里始终读到最新 index。
    val latestIndex by rememberUpdatedState(index)

    fun pick(x: Float) {
        if (maxIndex <= 0 || widthPx <= 0) return
        val f = (x / widthPx).coerceIn(0f, 1f)
        val i = Math.round(f * maxIndex)
        if (i != latestIndex) onIndexChange(i)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(maxIndex, widthPx) {
                detectTapGestures { offset -> pick(offset.x) }
            }
            .pointerInput(maxIndex, widthPx, index) {
                detectHorizontalDragGestures(
                    onDragEnd = { onCommit() },
                    onDragCancel = { onCommit() }
                ) { change, _ ->
                    change.consume()
                    pick(change.position.x)
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // 轨道底
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackH)
                .clip(RoundedCornerShape(trackH / 2))
                .background(cs.surfaceContainerHigh)
        )
        // 已选进度
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(trackH)
                .clip(RoundedCornerShape(trackH / 2))
                .background(cs.primary)
        )
        // 圆钮：用 offset 定位，注意左右各留半个钮宽，避免贴到轨道外侧
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val totalW = maxWidth
            val knobOffset = (totalW - knobR * 2) * fraction
            Box(
                modifier = Modifier
                    .offset(x = knobOffset)
                    .size(knobR * 2)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(width = 2.dp, color = cs.primary, shape = CircleShape)
            )
        }
    }
}

/** GPU 功耗档步进器：－ / 数值 / ＋。 */
@Composable
private fun StepperRow(
    name: String,
    value: Int,
    hint: String,
    canMinus: Boolean,
    canPlus: Boolean,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            LabelText(name)
            Gap(2.dp)
            HintText(hint)
        }
        StepButton("－", enabled = canMinus, onClick = onMinus)
        Box(
            modifier = Modifier.width(52.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "$value", color = MiuixTheme.colorScheme.onSurface, fontSize = 15.sp)
        }
        StepButton("＋", enabled = canPlus, onClick = onPlus)
    }
}

/** 步进器按钮：圆形描边，禁用态整体降透明度。 */
@Composable
private fun StepButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val cs = MiuixTheme.colorScheme
    Box(
        modifier = Modifier
            .size(32.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(CircleShape)
            .background(cs.surfaceContainerHigh)
            .border(width = 1.dp, color = cs.outline, shape = CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = cs.onSurface, fontSize = 14.sp)
    }
}

/** 「恢复默认」小按钮，只在用户改过阈值后才出现。 */
@Composable
private fun ResetRow(onClick: () -> Unit) {
    val cs = MiuixTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(cs.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "恢复默认档位", color = cs.primary, fontSize = 13.sp)
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