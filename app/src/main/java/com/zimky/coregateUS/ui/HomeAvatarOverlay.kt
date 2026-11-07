package com.zimky.coregateUS.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.zimky.coregateUS.core.CustomAvatar
import com.zimky.coregateUS.core.QqAvatar
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Only layout references are stored. Scroll-dependent positions are resolved in the draw pass. */
class HomeAvatarAnchor {
    var root by mutableStateOf<LayoutCoordinates?>(null)
    var avatar by mutableStateOf<LayoutCoordinates?>(null)
}

private data class AvatarFrame(val center: Offset, val radius: Float, val alpha: Float)

@Composable
fun BoxScope.HomeAvatarOverlay(scroll: ScrollState, anchor: HomeAvatarAnchor) {
    val context = LocalContext.current.applicationContext
    val owner = LocalLifecycleOwner.current
    val density = LocalDensity.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val background = MiuixTheme.colorScheme.surfaceContainerHigh.toArgb()
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    val clip = remember { Path() }
    val destination = remember { RectF() }
    var avatar by remember { mutableStateOf<ImageBitmap?>(null) }
    var saving by remember { mutableStateOf(false) }
    var selectionVersion by remember { mutableIntStateOf(0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && !saving) {
            selectionVersion++
            saving = true
            scope.launch {
                try {
                    val selected = CustomAvatar.save(context, uri)
                    if (selected != null) avatar = selected else Toaster.show("图片读取失败，请重新选择", true)
                } finally { saving = false }
            }
        }
    }
    LaunchedEffect(owner, context) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (avatar == null) {
                val version = selectionVersion
                val loaded = CustomAvatar.load(context) ?: QqAvatar.load()
                if (version == selectionVersion && !saving) avatar = loaded
            }
        }
    }
    val bitmap = remember(avatar) { avatar?.asAndroidBitmap() }
    val statusHeight = WindowInsets.statusBars.getTop(density).toFloat()
    val avatarRadius = with(density) { 26.dp.toPx() }
    val currentSaving by rememberUpdatedState(saving)

    fun frame(): AvatarFrame? {
        val root = anchor.root?.takeIf { it.isAttached } ?: return null
        val slot = anchor.avatar?.takeIf { it.isAttached } ?: return null
        val position = root.localPositionOf(slot, Offset(slot.size.width / 2f, slot.size.height / 2f))
        // A single local coordinate space, read after layout. No window-position + scroll feedback loop.
        val originInWindow = root.localToWindow(Offset.Zero)
        val cutout = ViewCompat.getRootWindowInsets(view)?.displayCutout?.boundingRects
            ?.filter { it.top <= statusHeight && it.width() < view.width / 2 }
            ?.minByOrNull { kotlin.math.abs(it.centerX() - view.width / 2) }
        val target = Offset(
            (cutout?.exactCenterX() ?: (view.width / 2f)) - originInWindow.x,
            (cutout?.exactCenterY() ?: (statusHeight / 2f)) - originInWindow.y
        )
        val startY = position.y + scroll.value
        val travel = (startY - target.y).coerceAtLeast(avatarRadius)
        val progress = (scroll.value / travel).coerceIn(0f, 1f)
        val center = Offset(
            position.x + (target.x - position.x) * progress,
            startY + (target.y - startY) * progress
        )
        // Move the complete circular image; fade only as it reaches the camera.
        val alpha = (1f - ((progress - 0.7f) / 0.3f).coerceIn(0f, 1f))
        return AvatarFrame(center, avatarRadius, alpha)
    }

    Canvas(Modifier.fillMaxSize()) {
        val f = frame() ?: return@Canvas
        drawIntoCanvas { composeCanvas ->
            val canvas = composeCanvas.nativeCanvas
            if (f.alpha > 0f) {
                val saveCount = canvas.save()
                clip.reset()
                clip.addCircle(f.center.x, f.center.y, f.radius, Path.Direction.CW)
                canvas.clipPath(clip)
                paint.alpha = (f.alpha * 255).toInt()
                val image = bitmap
                if (image == null) {
                    paint.color = background
                    paint.alpha = (f.alpha * 255).toInt()
                    canvas.drawCircle(f.center.x, f.center.y, f.radius, paint)
                } else {
                    val scale = (f.radius * 2 / image.width).coerceAtLeast(f.radius * 2 / image.height)
                    val halfWidth = image.width * scale / 2
                    val halfHeight = image.height * scale / 2
                    destination.set(f.center.x - halfWidth, f.center.y - halfHeight,
                        f.center.x + halfWidth, f.center.y + halfHeight)
                    canvas.drawBitmap(image, null, destination, paint)
                }
                canvas.restoreToCount(saveCount)
            }
        }
    }
    // Restrict hit testing to the moving avatar; the drawing layer never intercepts page gestures.
    Box(Modifier.size(52.dp).graphicsLayer {
        val f = frame()
        translationX = if (f == null || f.alpha <= 0f) -10000f else f.center.x - avatarRadius
        translationY = if (f == null || f.alpha <= 0f) -10000f else f.center.y - avatarRadius
        val scale = (f?.radius ?: avatarRadius) / avatarRadius
        scaleX = scale
        scaleY = scale
    }.pointerInput(scroll, anchor) {
        detectTapGestures {
            val f = frame()
            if (!currentSaving && f != null && f.alpha > 0f) {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }
    }.semantics { contentDescription = "主页头像，点击从相册更换；上滑移入摄像头消失，回滑还原" })
}