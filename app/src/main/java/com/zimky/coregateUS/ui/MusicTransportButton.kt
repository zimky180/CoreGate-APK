package com.zimky.coregateUS.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun MusicTransportButton(mode: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    val cs = MiuixTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.86f else 1f,
        spring(dampingRatio = 0.65f), label = "musicButtonPress")
    val color = cs.primary.copy(alpha = if (enabled) 1f else 0.3f)
    Box(Modifier.size(48.dp).scale(scale).clip(CircleShape)
        .background(cs.primary.copy(alpha = if (enabled) 0.10f else 0.04f))
        .clickable(interactionSource = interaction, indication = null, enabled = enabled,
            role = Role.Button, onClick = onClick)
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        AnimatedContent(targetState = mode, transitionSpec = {
            (fadeIn() + scaleIn(initialScale = 0.65f)) togetherWith
                (fadeOut() + scaleOut(targetScale = 0.65f))
        }, label = "musicTransportIcon") { icon ->
            Canvas(Modifier.size(24.dp)) {
                val u = size.width / 24f
                fun triangle(x: Float, reverse: Boolean = false) {
                    val path = Path().apply {
                        moveTo((if (reverse) x + 10 else x) * u, 5 * u)
                        lineTo((if (reverse) x else x + 10) * u, 12 * u)
                        lineTo((if (reverse) x + 10 else x) * u, 19 * u)
                        close()
                    }
                    drawPath(path, color)
                }
                when (icon) {
                    "previous" -> {
                        drawRect(color, Offset(5 * u, 5 * u), Size(3 * u, 14 * u))
                        triangle(9f, true)
                    }
                    "next" -> {
                        triangle(5f)
                        drawRect(color, Offset(16 * u, 5 * u), Size(3 * u, 14 * u))
                    }
                    "pause" -> {
                        drawRect(color, Offset(6 * u, 5 * u), Size(4 * u, 14 * u))
                        drawRect(color, Offset(14 * u, 5 * u), Size(4 * u, 14 * u))
                    }
                    "cancel" -> drawRect(color, Offset(6 * u, 6 * u), Size(12 * u, 12 * u))
                    else -> triangle(8f)
                }
            }
        }
    }
}
