package com.zimky.coregateUS.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.zimky.coregateUS.BuildConfig
import com.zimky.coregateUS.core.ApkUpdates
import kotlinx.coroutines.launch
import com.zimky.coregateUS.ui.LiquidCard as Card
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Version summary, release metadata, changes and actions follow the reference update prompt. */
@Composable
fun ApkUpdateCard() {
    val cs = MiuixTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ApkUpdates.Result?>(null) }
    var showDetails by remember { mutableStateOf(false) }
    fun openLink(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toaster.show("无法打开链接，请检查浏览器是否可用。", true) }
    }
    fun checkUpdates() {
        if (busy) return
        busy = true
        scope.launch {
            try {
                result = ApkUpdates.check()
                showDetails = result is ApkUpdates.Result.Available
            } finally { busy = false }
        }
    }
    val message = when (val value = result) {
        is ApkUpdates.Result.Available -> "发现新版本 ${value.release.version}，点按查看"
        is ApkUpdates.Result.Current -> "已是最新版本（GitHub：${value.version}）"
        is ApkUpdates.Result.Failed -> value.message
        ApkUpdates.Result.NoRelease -> "GitHub 暂无正式版 APK 发布"
        null -> "当前 APK：${BuildConfig.VERSION_NAME}"
    }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button) {
            if (!busy) {
                if (result is ApkUpdates.Result.Available) showDetails = true else checkUpdates()
            }
        }.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("检查更新", color = cs.onSurface, fontSize = 16.sp)
                SubText(if (busy) "正在检查…" else message)
            }
            Box(Modifier.size(40.dp).clickable(enabled = !busy, role = Role.Button,
                onClickLabel = "检查 APK 更新", onClick = { checkUpdates() }),
                contentAlignment = Alignment.Center) {
                UpdateCheckSpinner(busy, result != null && result !is ApkUpdates.Result.Failed)
            }
        }
    }
    val release = (result as? ApkUpdates.Result.Available)?.release
    if (showDetails && release != null) {
        Dialog(onDismissRequest = { showDetails = false }) {
            Card(Modifier.fillMaxWidth(), cornerRadius = 28.dp) {
                Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("发现 APK 更新", color = cs.onSurface, fontSize = 20.sp)
                    Text("${BuildConfig.VERSION_NAME} → ${release.version}", color = cs.primary, fontSize = 15.sp)
                    KeyValueRow("发布名称", release.title)
                    KeyValueRow("发布时间", release.published.replace('T', ' ').removeSuffix("Z") + " UTC")
                    KeyValueRow("安装包", release.assetName)
                    HairLine()
                    Text("更新内容", color = cs.onSurface, fontSize = 15.sp)
                    Text(release.notes.ifBlank { "此版本未提供更新说明。" },
                        color = cs.onSurfaceVariantSummary, fontSize = 13.sp, lineHeight = 20.sp)
                    TextButton("查看完整发布记录", onClick = { openLink(release.pageUrl) })
                    TextButton("下载 APK", onClick = { openLink(release.apkUrl) },
                        modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColorsPrimary())
                    TextButton("稍后", onClick = { showDetails = false }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
@Composable
private fun UpdateCheckSpinner(busy: Boolean, succeeded: Boolean) {
    val rotation = if (busy) {
        val transition = rememberInfiniteTransition(label = "updateCheck")
        val angle by transition.animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(850, easing = LinearEasing), RepeatMode.Restart),
            label = "updateRotation"
        )
        angle
    } else 0f
    val color = MiuixTheme.colorScheme.primary
    Canvas(Modifier.size(22.dp).graphicsLayer { rotationZ = rotation }
        .semantics {
            contentDescription = when {
                busy -> "正在检查更新"
                succeeded -> "更新检查成功"
                else -> "检查更新"
            }
        }) {
        if (!busy && succeeded) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width * 0.18f, size.height * 0.52f)
                lineTo(size.width * 0.40f, size.height * 0.74f)
                lineTo(size.width * 0.82f, size.height * 0.26f)
            }
            drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        } else {
            drawArc(color, startAngle = -65f, sweepAngle = 285f, useCenter = false,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}