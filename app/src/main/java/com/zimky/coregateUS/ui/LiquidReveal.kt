package com.zimky.coregateUS.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.Size
import com.zimky.coregateUS.ui.theme.LocalAppSettings
import kotlin.math.*

/** Keeps the destination alive until its surface has returned to the entry bounds. */
@Composable
internal fun LiquidReveal(
    visible: Boolean,
    origin: Rect,
    modifier: Modifier = Modifier,
    onClosed: () -> Unit = {},
    content: @Composable BoxScope.() -> Unit
) {
    val flow = remember { Animatable(0f) }
    var containerOffset by remember { mutableStateOf(Offset.Zero) }
    var retained by remember { mutableStateOf(visible) }
    val closed by rememberUpdatedState(onClosed)
    val ripples = LocalAppSettings.current.waterRipples
    LaunchedEffect(visible) {
        if (visible) retained = true
        flow.animateTo(if (visible) 1f else 0f, spring(dampingRatio = 0.92f, stiffness = 72f))
        if (!visible) { retained = false; closed() }
    }
    if (visible || retained) {
        Box(modifier.fillMaxSize().onGloballyPositioned { containerOffset = it.positionInRoot() }.graphicsLayer {
            val p = flow.value.coerceIn(0f, 1f)
            shape = object : Shape {
                override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
                    val fallback = Rect(size.width * 0.5f - 28f, size.height * 0.8f, size.width * 0.5f + 28f, size.height * 0.8f + 48f)
                    val start = if (origin.width > 0f && origin.height > 0f) origin.translate(-containerOffset) else fallback
                    fun mix(a: Float, b: Float) = a + (b - a) * p
                    val left = mix(start.left, 0f); val top = mix(start.top, 0f)
                    val right = mix(start.right, size.width); val bottom = mix(start.bottom, size.height)
                    val radius = min(start.width, start.height) * 0.5f * (1f - p)
                    val reach = hypot(size.width, size.height).coerceAtLeast(1f)
                    val amplitude = if (ripples) sin(p * PI).toFloat() * 5f * density.density else 0f
                    val centers = listOf(Offset(right - radius, top + radius), Offset(right - radius, bottom - radius),
                        Offset(left + radius, bottom - radius), Offset(left + radius, top + radius))
                    val path = Path()
                    var first = true
                    fun point(x: Float, y: Float, nx: Float, ny: Float) {
                        val lag = hypot(x - start.center.x, y - start.center.y) - p * reach
                        val wave = sin(lag / (10f * density.density)) * exp(-lag * lag / (reach * reach * 0.055f)) * amplitude
                        val px = (x + nx * wave).coerceIn(0f, size.width)
                        val py = (y + ny * wave).coerceIn(0f, size.height)
                        if (first) { path.moveTo(px, py); first = false } else path.lineTo(px, py)
                    }
                    centers.forEachIndexed { corner, c ->
                        val angle = -PI / 2 + corner * PI / 2
                        for (i in 0..12) {
                            val a = angle + i / 12.0 * PI / 2
                            val nx = cos(a).toFloat(); val ny = sin(a).toFloat()
                            point(c.x + nx * radius, c.y + ny * radius, nx, ny)
                        }
                        val nx = cos(angle + PI / 2).toFloat(); val ny = sin(angle + PI / 2).toFloat()
                        val next = centers[(corner + 1) % 4]
                        for (i in 1..16) {
                            val t = i / 16f
                            point(c.x + nx * radius + (next.x - c.x) * t, c.y + ny * radius + (next.y - c.y) * t, nx, ny)
                        }
                    }
                    path.close()
                    return Outline.Generic(path)
                }
            }
            clip = true
        }, content = content)
    }
}
