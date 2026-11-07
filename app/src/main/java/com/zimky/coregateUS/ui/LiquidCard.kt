package com.zimky.coregateUS.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.PI
import kotlin.math.sin

/** Miuix surface with a quiet, translucent pool of light above the original base. */
@Composable
fun LiquidCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit
) {
    MiuixCard(modifier = modifier, cornerRadius = cornerRadius) {
        Box(Modifier.liquidSheen().capillaryTouch(), content = content)
    }
}

/** Only the drawing observes the animation; children do not recompose on every frame. */
internal fun Modifier.liquidSheen(): Modifier = composed {
    val tint = MiuixTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "liquidLight")
    val phase = transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(12000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "liquidDrift"
    )
    drawBehind {
        if (size.width > 0f && size.height > 0f) {
            val p = phase.value
            val radius = size.width * 0.70f
            val depth = minOf(size.height * 0.62f, 180.dp.toPx())
            val center = Offset(size.width * (0.18f + p * 0.64f),
                size.height * (0.04f + sin(p * PI).toFloat() * 0.06f))
            scale(1f, depth / radius, pivot = center) {
                drawCircle(Brush.radialGradient(
                    0f to tint.copy(alpha = 0.105f),
                    0.42f to tint.copy(alpha = 0.055f),
                    1f to Color.Transparent,
                    center = center, radius = radius
                ), radius = radius, center = center)
            }
        }
    }
}

/** Observe presses without consuming them, so switches and scrolling keep their own gestures. */
internal fun Modifier.capillaryTouch(): Modifier = composed {
    val tint = MiuixTheme.colorScheme.primary
    val wave = remember { Animatable(1f) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    clipToBounds().pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.firstOrNull { it.pressed && !it.previousPressed }?.let { press ->
                    origin = press.position
                    job?.cancel()
                    job = scope.launch {
                        wave.snapTo(0f)
                        wave.animateTo(1f, tween(1100, easing = LinearEasing))
                    }
                }
            }
        }
    }.drawWithContent {
        drawContent()
        drawCapillaryWaves(origin, wave.value, tint, intensity = 0.65f)
    }
}
