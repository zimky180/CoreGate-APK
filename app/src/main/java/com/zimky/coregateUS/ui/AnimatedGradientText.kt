package com.zimky.coregateUS.ui

import androidx.compose.animation.core.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 缓慢流动的同色系高光，保留状态本身的语义色。 */
@Composable
fun AnimatedGradientText(
    text: String,
    color: Color = MiuixTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 12.sp,
    fontWeight: FontWeight = FontWeight.SemiBold
) {
    val transition = rememberInfiniteTransition(label = "textShimmer")
    val phase by transition.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse),
        label = "highlightPosition"
    )
    val highlight = lerp(color, MiuixTheme.colorScheme.onSurface, 0.32f)
    Text(
        text = text, modifier = modifier, fontSize = fontSize, fontWeight = fontWeight,
        style = TextStyle(brush = Brush.linearGradient(
            colors = listOf(color, highlight, color),
            start = Offset(phase * 160f, 0f), end = Offset(phase * 160f + 240f, 80f)
        ))
    )
}
