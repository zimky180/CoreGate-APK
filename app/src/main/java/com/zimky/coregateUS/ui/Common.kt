package com.zimky.coregateUS.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 各页滚动内容的底部内边距。
 *
 * 底栏是悬浮在内容之上的（Box + align(BottomCenter) + 背景模糊），
 * 所以内容区本身不再留白，而是让每页的滚动容器在末尾补出底栏遮挡的高度，
 * 这样内容能流到底栏背后 → 底栏看起来才是「浮」着的，而不是「单独开一栏」。
 *
 * 64（底栏高）+ 28（底栏底部间距）+ 12（缓冲）= 104dp
 */
val PAGE_BOTTOM_INSET: Dp = 104.dp

/**
 * 通用文本与间距组件。
 *
 * 视觉规范全部取自 Miuix 主题（[MiuixTheme.colorScheme]），不自建整套色板：
 * 主色 primary、异常 error、正文 onSurface、次要 onSurfaceVariantSummary、分割线 outline。
 *
 * 唯一的例外是 [Semantic]：核心频率需要「低频绿 / 中频蓝 / 高频橙」这种与功耗语义绑定的
 * 颜色，Miuix 主题色板里没有对应项，故保留五个固定值（且与 KernelSU 的强调色一致）。
 */

/** 语义色：只保留频率/状态语义必需的几项，其余一律走 MiuixTheme.colorScheme。 */
object Semantic {
    /** 正常 / 低频省电 */
    val OK = Color(0xFF34C759)

    /** 强调 / 中频 / 全核在线 */
    val ACC = Color(0xFF0A84FF)

    /** 警告 / 高频大核（优先封锁对象） */
    val WARN = Color(0xFFFF9F0A)

    /** 错误 */
    val ERR = Color(0xFFFF453A)

    /** 离线 / 灰 */
    val OFF = Color(0xFF8E8E93)
}

/** 单个核心方块，颜色由调用方按频率语义给出（离线 = 灰 · 低频绿 · 中频蓝 · 高频橙）。 */
@Composable
fun CoreCell(
    id: Int,
    online: Boolean,
    freqText: String,
    color: Color,
    modifier: Modifier = Modifier,
    /** 非 null 时整块可点：首页用它实现「点一下封禁 / 再点一下解冻」。 */
    onClick: (() -> Unit)? = null
) {
    val cs = MiuixTheme.colorScheme
    val cellBg = if (online) color.copy(alpha = 0.16f) else cs.surfaceContainerHigh
    val cellBorder = if (online) color.copy(alpha = 0.45f) else cs.outline
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(cellBg)
            .border(1.dp, cellBorder, RoundedCornerShape(12.dp))
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        // 直接用 material3 ripple：LocalIndication 已被新版 Compose 移除
                        indication = ripple(),
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onClick() }
                } else Modifier
            )
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "CPU$id",
            color = if (online) cs.onSurface else cs.onSurfaceVariantSummary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = freqText,
            color = if (online) color else cs.onSurfaceVariantSummary,
            fontSize = 10.sp
        )
    }
}

/** 1dp 细线（日志框、诊断区用）。 */
@Composable
fun HairLine(color: Color = MiuixTheme.colorScheme.outline, modifier: Modifier = Modifier) {
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

/** 底部留白，避免最后一张卡片贴住底栏。 */
@Composable
fun BottomSpacer() {
    Spacer(Modifier.height(20.dp))
}

/** 等宽小字，用于日志与诊断。 */
@Composable
fun MonoText(
    text: String,
    color: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontFamily = FontFamily.Monospace,
        modifier = modifier
    )
}

/** 次要说明文字，12sp。用于卡片内的解释段落。 */
@Composable
fun SubText(text: String, modifier: Modifier = Modifier, color: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary) {
    Text(text = text, color = color, fontSize = 12.sp, lineHeight = 17.sp, modifier = modifier)
}

/** 更小一号的提示文字，11.5sp。用于参数范围、查找顺序这类脚注。 */
@Composable
fun HintText(text: String, modifier: Modifier = Modifier, color: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary) {
    Text(text = text, color = color, fontSize = 11.5.sp, lineHeight = 16.sp, modifier = modifier)
}

/**
 * 一行可点击的二级页入口：左边标题，右边一句现状摘要 + 箭头。
 *
 * 只负责「看上去能点、点了有反馈」，真正的跳转由调用方在 onClick 里做。
 * 箭头用文字「›」而不是图标，避免为了一个符号多引一套图标依赖。
 */
@Composable
fun NavRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MiuixTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, color = cs.onSurface, fontSize = 15.sp)
        Spacer(Modifier.width(10.dp))
        Text(
            text = summary,
            color = cs.onSurfaceVariantSummary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(6.dp))
        Text(text = "›", color = cs.onSurfaceVariantSummary, fontSize = 18.sp)
    }
}

/** 卡片内小标题，14sp 强调色。用于分区名。 */
@Composable
fun LabelText(text: String, modifier: Modifier = Modifier, color: Color = MiuixTheme.colorScheme.onSurface) {
    Text(text = text, color = color, fontSize = 14.sp, modifier = modifier)
}

/** 卡片内分割线，两侧自带 10dp 留白。 */
@Composable
fun CardDivider() {
    Gap(10.dp)
    HairLine()
    Gap(10.dp)
}

/** 一行「名称 / 数值」，数值右对齐，诊断与关于页用。 */
@Composable
fun KeyValueRow(name: String, value: String) {
    val cs = MiuixTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = name, color = cs.onSurfaceVariantSummary, fontSize = 12.5.sp)
        Spacer(Modifier.width(12.dp))
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
            Text(text = value, color = cs.onSurface, fontSize = 12.5.sp)
        }
    }
}

/** 一行左右布局：左侧标题+说明，右侧控件。 */
@Composable
fun SwitchRow(
    title: String,
    desc: String?,
    trailing: @Composable () -> Unit
) {
    val cs = MiuixTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = cs.onSurface, fontSize = 15.sp)
            if (!desc.isNullOrEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = desc,
                    color = cs.onSurfaceVariantSummary,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

/* ======================================================================
 * 轻提示（Compose 版 Toaster）
 *
 * Toaster.show(msg) 只写一个 MutableState，任何地方都能调，不需要 context；
 * MainActivity 的根 Box 里放一个 ToastHost()，由它负责渲染胶囊与淡入淡出。
 * 观感：深底胶囊 + 顶部居中 + 1.8 秒后淡出。
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
    val cs = MiuixTheme.colorScheme
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
                color = if (shown.isError) cs.error else cs.onSurface,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(cs.surfaceContainerHighest)
                    .border(
                        width = 1.dp,
                        color = if (shown.isError) cs.error.copy(alpha = 0.4f) else cs.outline,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 11.dp)
            )
        }
    }
}