package com.zimky.coregateUS.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Compose 版色板与通用组件。
 *
 * 色值一对一照搬旧 View 版 Ui.java（而 Ui.java 又是 WebUI :root 的原样搬运），
 * 因此 APK 与 webroot 页面观感继续一致。
 */
object C {
    val ACC = Color(0xFF4F8CFF)   // 主色
    val OK = Color(0xFF34C759)    // 正常
    val WARN = Color(0xFFFF9F0A)  // 注意
    val ERR = Color(0xFFFF453A)   // 异常
    val OFF = Color(0xFF5A6474)   // 关闭
    val TXT = Color(0xFFEEF2F9)   // 正文
    val SUB = Color(0xFF9AA5B8)   // 次要

    val CARD_BG = Color(0x17FFFFFF)
    val CARD_BORDER = Color(0x21FFFFFF)
    val DIVIDER = Color(0x14FFFFFF)
}

/** 正文标题（17sp 粗体）。 */
@Composable
fun TitleText(text: String, color: Color = C.TXT, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = color,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
    )
}

/** 正文（14sp）。 */
@Composable
fun LabelText(text: String, color: Color = C.TXT, modifier: Modifier = Modifier) {
    Text(text = text, color = color, fontSize = 14.sp, modifier = modifier)
}

/** 次级说明（12.5sp，行距略松）。 */
@Composable
fun SubText(text: String, color: Color = C.SUB, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = color,
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
        modifier = modifier
    )
}

/** 小提示（11.5sp）。 */
@Composable
fun HintText(text: String, color: Color = C.SUB, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = color,
        fontSize = 11.5.sp,
        lineHeight = 16.sp,
        modifier = modifier
    )
}

/** 等宽小字，用于日志与诊断。 */
@Composable
fun MonoText(text: String, color: Color = C.SUB, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontFamily = FontFamily.Monospace,
        modifier = modifier
    )
}

/**
 * 玻璃卡片。
 *
 * 视觉降级自 WebUI 的 backdrop-filter：半透明白底 + 1px 描边 + 20dp 圆角。
 * 标题与内容通过 [content] 自行排布，卡片只负责外观与间距。
 */
@Composable
fun GlassCard(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(C.CARD_BG)
            .border(1.dp, C.CARD_BORDER, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 15.dp)
    ) {
        if (title != null) {
            TitleText(title)
            Spacer(Modifier.height(12.dp))
        }
        content()
    }
}

/** 卡片内分隔线。 */
@Composable
fun CardDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .height(1.dp)
            .background(C.DIVIDER)
    )
}

/**
 * 状态胶囊。
 *
 * [breathing] 为真时做透明度呼吸，用于「锁核中」这类持续态，
 * 让用户一眼看出状态是活的而不是静止文字。
 */
@Composable
fun StatusPill(
    text: String,
    color: Color,
    breathing: Boolean = false,
    modifier: Modifier = Modifier
) {
    val alpha = if (breathing) {
        val transition = rememberInfiniteTransition(label = "pill")
        val a by transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pillAlpha"
        )
        a
    } else {
        1f
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.alpha(alpha)
        )
    }
}

/** 实心圆角主按钮。 */
@Composable
fun PrimaryButton(
    text: String,
    enabled: Boolean = true,
    color: Color = C.ACC,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) color else color.copy(alpha = 0.45f))
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** 描边小按钮，用于「读取日志」「保存名单」这类次要动作。 */
@Composable
fun OutlineButton(
    text: String,
    color: Color = C.ACC,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** 「名称 —— 值」的横向行，名称列宽固定，保证多行对齐。 */
@Composable
fun KeyValueRow(
    key: String,
    value: String,
    keyWidth: Dp = 86.dp,
    valueColor: Color = C.TXT,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = key, color = C.SUB, fontSize = 14.sp, modifier = Modifier.width(keyWidth))
        Text(
            text = value,
            color = valueColor,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) trailing()
    }
}

/** 一行左右布局：左侧标题+说明，右侧控件。 */
@Composable
fun SwitchRow(
    title: String,
    desc: String?,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            LabelText(title)
            if (!desc.isNullOrEmpty()) {
                Spacer(Modifier.height(4.dp))
                HintText(desc)
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

/** 页面通用纵向排布：卡片之间 14dp。 */
@Composable
fun PageColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content
    )
}

/** 底部留白，避免最后一张卡片贴住底栏。 */
@Composable
fun BottomSpacer() {
    Spacer(Modifier.height(20.dp))
}

/**
 * 单个核心方块。
 *
 * 颜色语义与旧版 CoreGridView 一致：
 *   离线 = 灰 · 低频在线 = 绿 · 中频 = 蓝 · 高频大核 = 橙（优先封锁对象）
 */
@Composable
fun CoreCell(
    id: Int,
    online: Boolean,
    freqText: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (online) color.copy(alpha = 0.16f) else Color(0x0DFFFFFF))
            .border(
                1.dp,
                if (online) color.copy(alpha = 0.45f) else Color(0x14FFFFFF),
                RoundedCornerShape(12.dp)
            )
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "CPU$id",
            color = if (online) C.TXT else C.OFF,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = freqText,
            color = if (online) color else C.OFF,
            fontSize = 10.sp
        )
    }
}

/** 1dp 细线（日志框、诊断区用）。 */
@Composable
fun HairLine(color: Color = C.DIVIDER, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().height(1.dp).background(color))
}

/** 空白占位，固定高度。 */
@Composable
fun Gap(h: Dp) {
    Spacer(Modifier.height(h))
}

/** 横向空白。 */
@Composable
fun HGap(w: Dp) {
    Spacer(Modifier.width(w))
}

/** 给外部用的尺寸常量，避免各页各写一套。 */
object D {
    val cell = 0.dp // 占位，核心网格用 weight 排布
    val gap = 14.dp
    val inner = 10.dp
}

/* ======================================================================
 * 轻提示（Compose 版 Toaster）
 *
 * 旧 View 版的 Toaster 是「new 一个实例、往 Activity 的 root 里塞 TextView」，
 * 在 Compose 里没有 root 可塞，改用「状态 + 顶层覆盖层」：
 *   - Toaster.show(msg) 只写一个 MutableState，任何地方都能调，不需要 context；
 *   - MainActivity 的根 Box 里放一个 ToastHost()，由它负责渲染胶囊与淡入淡出。
 * 观感与旧版一致：深底胶囊 + 顶部居中 + 1.8 秒后淡出。
 * ====================================================================== */

/** 提示状态：文案 + 是否是错误提示 + 当前序号（同文案连点时也能重启计时）。 */
private data class ToastState(val msg: String = "", val isError: Boolean = false, val seq: Long = 0L)

private val toastState = mutableStateOf(ToastState())

/** 全局轻提示入口。页面里直接 Toaster.show("...") 即可。 */
object Toaster {
    fun show(msg: String) = show(msg, false)

    fun show(msg: String, isError: Boolean) {
        if (msg.isEmpty()) return
        toastState.value = ToastState(msg, isError, toastState.value.seq + 1)
    }
}

/**
 * 提示渲染层。放在 AppRoot 的最外层 Box 里，盖在内容与底栏之上。
 * 自己管理可见性与淡出，1.8 秒后自动消失。
 */
@Composable
fun ToastHost(modifier: Modifier = Modifier) {
    val state = toastState.value
    var visible by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf(ToastState()) }

    // 每次 show() 递增 seq，这里跟着重启计时
    LaunchedEffect(state.seq) {
        if (state.seq == 0L) return@LaunchedEffect
        shown = state
        visible = true
        delay(1800L)
        visible = false
    }

    if (shown.seq == 0L) return

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(180)) +
            slideInVertically(animationSpec = tween(180), initialOffsetY = { -it / 2 }),
        exit = fadeOut(animationSpec = tween(220))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Text(
                text = shown.msg,
                color = if (shown.isError) C.ERR else C.TXT,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xF21A202B))
                    .border(
                        width = 1.dp,
                        color = if (shown.isError) Color(0x66FF453A) else Color(0x2EFFFFFF),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 11.dp)
            )
        }
    }
}