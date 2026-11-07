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

/** A single anchored surface; content stays measured while the surface flows home. */
@Composable
internal fun MusicDroplet(
    expanded: Boolean,
    color: Color,
    onExpand: () -> Unit,
    button: @Composable () -> Unit,
    panel: @Composable () -> Unit
) {
    val cs = MiuixTheme.colorScheme
    val edge = cs.onSurface.copy(alpha = 0.10f)
    val surface = lerp(color, cs.primary, 0.035f)
    val flow = remember { Animatable(if (expanded) 1f else 0f) }
    LaunchedEffect(expanded) {
        // Retarget from the current position and velocity on rapid taps.
        flow.animateTo(if (expanded) 1f else 0f,
            spring(dampingRatio = 0.78f, stiffness = 115f))
    }
    val raw = flow.value
    val p = raw.coerceIn(0f, 1f)
    val swell = sin(p * Math.PI).toFloat()
    val ripple = sin(p * Math.PI * 2.0).toFloat() * swell
    val density = LocalDensity.current
    val radiusSmall = with(density) { 24.dp.toPx() }
    val radiusLarge = with(density) { 22.dp.toPx() }
    val rippleSize = with(density) { 5.dp.toPx() }
    val shape = GenericShape { size, _ ->
        val w = size.width
        val h = size.height
        val r = (radiusSmall * (1f - p) + radiusLarge * p).coerceAtMost(h / 2f)
        val bow = ripple * rippleSize
        moveTo(r, 0f)
        cubicTo(w * 0.38f, bow, w * 0.72f, -bow, w - r, 0f)
        cubicTo(w, 0f, w, 0f, w, r)
        cubicTo(w - bow, h * 0.4f, w + bow, h * 0.65f, w, h - r)
        cubicTo(w, h, w, h, w - r, h)
        cubicTo(w * 0.65f, h - bow, w * 0.35f, h + bow, r, h)
        cubicTo(0f, h, 0f, h, 0f, h - r)
        cubicTo(bow, h * 0.65f, -bow, h * 0.35f, 0f, r)
        cubicTo(0f, 0f, 0f, 0f, r, 0f)
        close()
    }
    Layout(
        modifier = Modifier.shadow(6.dp, shape, clip = false)
            .graphicsLayer {
                this.shape = shape
                clip = true
                transformOrigin = TransformOrigin(1f, 1f)
                rotationZ = ripple * 0.65f
                scaleY = 1f + (raw - p) * 0.12f
            }.background(surface.copy(alpha = 0.98f)).liquidSheen()
            .border(0.75.dp, Brush.verticalGradient(listOf(edge, edge.copy(alpha = 0.025f))), shape),
        content = {
            Box(Modifier.pointerInput(expanded) {
                if (!expanded) awaitPointerEventScope {
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
            small.placeRelative(actualWidth - small.width, actualHeight - small.height)
        }
    }
}