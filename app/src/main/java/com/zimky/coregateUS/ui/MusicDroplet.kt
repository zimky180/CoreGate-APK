package com.zimky.coregateUS.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import kotlinx.coroutines.launch
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.exp

/** A single anchored surface; content stays measured while the surface flows home. */
@Composable
internal fun MusicDroplet(
    expanded: Boolean,
    color: Color,
    onExpand: () -> Unit,
    button: @Composable () -> Unit,
    panel: @Composable () -> Unit
) {
    val waterRipples = com.zimky.coregateUS.ui.theme.LocalAppSettings.current.waterRipples
    val cs = MiuixTheme.colorScheme
    val edge = cs.onSurface.copy(alpha = 0.10f)
    val surface = lerp(color, cs.primary, 0.035f)
    val flow = remember { Animatable(if (expanded) 1f else 0f) }
    val wave = remember { Animatable(1f) }
    var firstFrame by remember { mutableStateOf(true) }
    LaunchedEffect(expanded, waterRipples) {
        if (firstFrame) { firstFrame = false; return@LaunchedEffect }
        if (waterRipples) launch {
            wave.snapTo(0f)
            wave.animateTo(1f, tween(950, easing = LinearEasing))
        } else wave.snapTo(1f)
        // Retarget from the current position and velocity on rapid taps.
        flow.animateTo(if (expanded) 1f else 0f,
            spring(dampingRatio = 0.92f, stiffness = 72f))
    }
    val raw = flow.value
    val p = raw.coerceIn(0f, 1f)
    val density = LocalDensity.current
    val radiusSmall = with(density) { 24.dp.toPx() }
    val radiusLarge = with(density) { 22.dp.toPx() }
    val rippleSize = with(density) { 5.dp.toPx() }
    val shape = GenericShape { size, _ ->
        val w = size.width
        val h = size.height
        val r = (radiusSmall * (1f - p) + radiusLarge * p).coerceAtMost(minOf(w, h) / 2f)
        val origin = Offset(w - radiusSmall, h - radiusSmall)
        val reach = hypot(w, h).coerceAtLeast(1f)
        val front = p * reach * 1.16f
        val amplitude = if (waterRipples) sin(p * Math.PI).toFloat() * rippleSize else 0f
        // Sample the entire rounded perimeter so the travelling wave deforms edges,
        // rather than rocking the whole panel as one rigid object.
        val centers = listOf(Offset(w - r, r), Offset(w - r, h - r),
            Offset(r, h - r), Offset(r, r))
        var first = true
        fun point(x: Float, y: Float, nx: Float, ny: Float) {
            val distance = hypot(x - origin.x, y - origin.y)
            val lag = distance - front
            val wave = sin(lag / (10f * density.density)) *
                exp(-lag * lag / (reach * reach * 0.055f)) * amplitude
            val px = (x + nx * wave).coerceIn(0f, w)
            val py = (y + ny * wave).coerceIn(0f, h)
            if (first) { moveTo(px, py); first = false } else lineTo(px, py)
        }
        centers.forEachIndexed { corner, c ->
            val start = -Math.PI / 2.0 + corner * Math.PI / 2.0
            for (i in 0..12) {
                val angle = start + i / 12.0 * Math.PI / 2.0
                val nx = cos(angle).toFloat()
                val ny = sin(angle).toFloat()
                point(c.x + nx * r, c.y + ny * r, nx, ny)
            }
            val end = start + Math.PI / 2.0
            val nx = cos(end).toFloat(); val ny = sin(end).toFloat()
            val next = centers[(corner + 1) % 4]
            for (i in 1..16) {
                val t = i / 16f
                point(c.x + nx * r + (next.x - c.x) * t,
                    c.y + ny * r + (next.y - c.y) * t, nx, ny)
            }
        }
        close()
    }
    Layout(
        modifier = Modifier.shadow(6.dp, shape, clip = false)
            .graphicsLayer {
                this.shape = shape
                clip = true
                transformOrigin = TransformOrigin(1f, 1f)
                rotationZ = 0f
                scaleY = 1f + (raw - p) * 0.12f
            }.background(surface.copy(alpha = 0.98f)).liquidSheen()
            .border(0.75.dp, Brush.verticalGradient(listOf(edge, edge.copy(alpha = 0.025f))), shape)
            .drawWithContent {
                drawContent()
                if (waterRipples) drawCapillaryWaves(
                    Offset(size.width - 28.dp.toPx(), size.height - 24.dp.toPx()),
                    wave.value, cs.primary, returning = !expanded
                )
            },
        content = {
            Box(Modifier.pointerInput(expanded, p >= 0.92f) {
                if (!expanded || p < 0.92f) awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                    }
                }
            }.graphicsLayer {
                alpha = ((p - 0.45f) / 0.55f).coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(1f, 1f)
                scaleX = 0.94f + p * 0.06f
                scaleY = scaleX
            }) { panel() }
            Box(Modifier.size(56.dp, 48.dp).graphicsLayer {
                // The note returns only in the final part of the contraction.
                alpha = ((0.16f - p) / 0.16f).coerceIn(0f, 1f)
            }.clickable(enabled = !expanded && p < 0.2f, onClick = onExpand),
                contentAlignment = Alignment.Center) { button() }
        }
    ) { measurables, constraints ->
        val panelWidth = 280.dp.roundToPx().coerceAtMost(constraints.maxWidth)
        val full = measurables[0].measure(Constraints.fixedWidth(panelWidth))
        val small = measurables[1].measure(Constraints())
        val width = (small.width + (full.width - small.width) * p).toInt()
        val height = (small.height + (full.height - small.height) * p).toInt()
        val actualWidth = constraints.constrainWidth(width)
        val actualHeight = constraints.constrainHeight(height)
        layout(actualWidth, actualHeight) {
            if (p > 0f) full.placeRelative(actualWidth - full.width, actualHeight - full.height)
            // A transparent clickable still participates in hit testing: remove the note
            // from placement once expanded so it cannot cover the queue button.
            if (p < 0.16f) small.placeRelative(actualWidth - small.width, actualHeight - small.height)
        }
    }
}