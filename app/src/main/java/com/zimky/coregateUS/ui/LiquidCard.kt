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
import androidx.compose.ui.graphics.luminance
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
    val appearance = com.zimky.coregateUS.ui.theme.LocalAppSettings.current
    if (!appearance.gravityGlow) return@composed this
    val cs = MiuixTheme.colorScheme
    // Use the actual accent seed rather than a muted tonal primary. Keep the
    // light visible against both light and dark cards for every accent.
    val tint = if (appearance.keyColor != 0) Color(appearance.keyColor) else cs.primary
    val accentBoost = if (appearance.keyColor == 0xff43a047.toInt() || appearance.keyColor == 0xff8e65d3.toInt()) 1.16f else 1f
    val glowAlpha = (if (cs.surfaceContainer.luminance() < 0.35f) 0.24f else 0.19f) * accentBoost
    val gravity = rememberLiquidGravity()
    val position = remember { floatArrayOf(0.5f, 0.08f) }
    val frameTime = remember { longArrayOf(0L) }
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
            val now = android.os.SystemClock.uptimeMillis()
            val live = gravity.lastSample != 0L && now - gravity.lastSample < 1500L
            val direction = gravity.direction
            val targetX = if (live) 0.5f + direction.x * 0.44f else 0.18f + p * 0.64f
            val targetY = if (live) 0.5f + direction.y * 0.44f else
                0.04f + sin(p * PI).toFloat() * 0.06f
            val dt = if (frameTime[0] == 0L) 0.016f else
                ((now - frameTime[0]) / 1000f).coerceIn(0f, 0.1f)
            frameTime[0] = now
            val follow = 1f - kotlin.math.exp(-dt / 0.22f)
            position[0] += (targetX - position[0]) * follow
            position[1] += (targetY - position[1]) * follow
            val center = Offset(size.width * position[0], size.height * position[1])
            scale(1f, depth / radius, pivot = center) {
                drawCircle(Brush.radialGradient(
                    0f to tint.copy(alpha = glowAlpha),
                    0.42f to tint.copy(alpha = glowAlpha * 0.52f),
                    1f to Color.Transparent,
                    center = center, radius = radius
                ), radius = radius, center = center)
            }
        }
    }
}

/** Observe presses without consuming them, so switches and scrolling keep their own gestures. */
internal fun Modifier.capillaryTouch(): Modifier = composed {
    if (!com.zimky.coregateUS.ui.theme.LocalAppSettings.current.waterRipples) return@composed this
    val tint = MiuixTheme.colorScheme.primary
    val waves = remember { mutableStateListOf<TouchWave>() }
    val scope = rememberCoroutineScope()
    clipToBounds().pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.filter { it.pressed && !it.previousPressed }.forEach { press ->
                    val wave = TouchWave(press.position)
                    waves.add(wave)
                    scope.launch {
                        try {
                            wave.progress.animateTo(1f, tween(1100, easing = LinearEasing))
                        } finally {
                            waves.remove(wave)
                        }
                    }
                }
            }
        }
    }.drawWithContent {
        drawContent()
        waves.forEach { wave ->
            drawCapillaryWaves(wave.origin, wave.progress.value, tint, intensity = 0.65f)
        }
    }
}

private class TouchWave(val origin: Offset) {
    val progress = Animatable(0f)
}
