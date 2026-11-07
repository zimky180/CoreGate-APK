package com.zimky.coregateUS.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import com.zimky.coregateUS.ui.LiquidCard as Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 居中确认框；关闭动画结束后执行动作，连续点击只执行一次。 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    val cs = MiuixTheme.colorScheme
    val transition = remember { MutableTransitionState(false) }
    var decision by remember { mutableStateOf<Boolean?>(null) }
    val currentCancel by rememberUpdatedState(onCancel)
    val currentConfirm by rememberUpdatedState(onConfirm)
    LaunchedEffect(Unit) { transition.targetState = true }
    LaunchedEffect(decision, transition.isIdle, transition.currentState) {
        val confirmed = decision
        if (confirmed != null && transition.isIdle && !transition.currentState) {
            if (confirmed) currentConfirm() else currentCancel()
        }
    }
    fun close(confirmed: Boolean) {
        if (decision != null) return
        decision = confirmed
        transition.targetState = false
    }
    Dialog(
        onDismissRequest = { close(false) },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BackHandler { close(false) }
        Box(
            Modifier.fillMaxSize().safeDrawingPadding().clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null
            ) { close(false) },
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visibleState = transition,
                enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.92f),
                exit = fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.96f)
            ) {
                Card(
                    modifier = Modifier.padding(24.dp).widthIn(max = 420.dp).fillMaxWidth()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                    cornerRadius = 28.dp
                ) {
                    Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()).padding(20.dp)) {
                        AnimatedGradientText(title, cs.primary, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(12.dp))
                        Text(message, color = cs.onSurfaceVariantSummary, fontSize = 13.sp, lineHeight = 20.sp)
                        Spacer(Modifier.height(20.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton("取消", onClick = { close(false) },
                                colors = ButtonDefaults.textButtonColors(textColor = cs.onSurfaceVariantSummary))
                            Spacer(Modifier.width(8.dp))
                            TextButton(confirmText, onClick = { close(true) },
                                colors = ButtonDefaults.textButtonColors(textColor = cs.primary))
                        }
                    }
                }
            }
        }
    }
}
