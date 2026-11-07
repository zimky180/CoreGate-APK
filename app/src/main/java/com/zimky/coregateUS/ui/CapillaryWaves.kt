package com.zimky.coregateUS.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** A dispersive wave packet: short trailing crests follow the leading water ridge. */
internal fun DrawScope.drawCapillaryWaves(
    origin: Offset,
    progress: Float,
    tint: Color,
    returning: Boolean = false,
    intensity: Float = 1f
) {
    val t = progress.coerceIn(0f, 1f)
    val envelope = sin(PI * t).toFloat() * intensity
    if (envelope <= 0.001f || size.minDimension <= 0f) return
    val reach = maxOf(
        hypot(origin.x, origin.y), hypot(size.width - origin.x, origin.y),
        hypot(origin.x, size.height - origin.y),
        hypot(size.width - origin.x, size.height - origin.y)
    )
    val travel = if (returning) 1f - t else t
    val front = reach * 1.18f * sqrt(travel)
    val spacing = (9f * density) + reach * 0.014f
    repeat(6) { index ->
        val radius = front - spacing * index * (1f + index * 0.12f)
        if (radius > 0f) {
            val attenuation = envelope * (1f - index / 6f)
            // A broad faint ridge and a narrow highlight/shadow pair give water depth.
            drawCircle(tint.copy(alpha = 0.035f * attenuation), radius, origin,
                style = Stroke((4f * density)))
            drawCircle(Color.White.copy(alpha = 0.19f * attenuation), radius, origin,
                style = Stroke((0.7f * density)))
            drawCircle(tint.copy(alpha = 0.12f * attenuation),
                (radius - (1.4f * density)).coerceAtLeast(0f), origin,
                style = Stroke((0.9f * density)))
        }
    }
}

