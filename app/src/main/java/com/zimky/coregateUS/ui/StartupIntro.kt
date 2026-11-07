package com.zimky.coregateUS.ui

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import top.yukonga.miuix.kmp.basic.Text

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.zimky.coregateUS.R
import kotlin.random.Random

private val IntroMint = Color(0xFF75E1B5)
private data class IntroParticle(
    val x: Float, val y: Float, val dx: Float, val dy: Float,
    val delay: Float, val radius: Float
)

private fun phase(time: Float, start: Float, end: Float): Float {
    val p = ((time - start) / (end - start)).coerceIn(0f, 1f)
    return p * p * (3f - 2f * p)
}

/** The particles and the final solid word share exactly the same glyph outline. */
private class IntroArtwork {
    val word = Path()
    val solidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFECFFF6.toInt() }
    val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = android.graphics.LinearGradient(
            -85f, 0f, 85f, 0f,
            intArrayOf(0x00FFFFFF, 0xCFFFFFFF.toInt(), 0x00FFFFFF),
            null, android.graphics.Shader.TileMode.CLAMP
        )
    }
    val sheenMatrix = android.graphics.Matrix()
    val outline = Path().apply {
        addRoundRect(RectF(116f, 116f, 396f, 396f), 57f, 57f, Path.Direction.CW)
    }
    val measure = PathMeasure(outline, true)
    val segment = Path()
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = 0xFF75E1B5.toInt()
    }
    val particles: List<IntroParticle>

    init {
        val type = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 100f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
        }
        val text = "CoreGate"
        val baseline = 60f - (type.ascent() + type.descent()) / 2f
        type.getTextPath(text, 0, text.length, (680f - type.measureText(text)) / 2f, baseline, word)
        val bitmap = Bitmap.createBitmap(680, 120, Bitmap.Config.ARGB_8888)
        particles = try {
            android.graphics.Canvas(bitmap).drawPath(word, solidPaint)
            val random = Random(230)
            buildList {
                for (y in 0 until 120 step 4) for (x in 0 until 680 step 4) {
                    if (android.graphics.Color.alpha(bitmap.getPixel(x, y)) > 100) {
                        add(IntroParticle(x.toFloat(), y.toFloat(),
                            (if (x < 340) -1f else 1f) * (35f + random.nextFloat() * 105f),
                            25f + random.nextFloat() * 100f,
                            x / 680f * 0.24f + random.nextFloat() * 0.08f,
                            0.8f + random.nextFloat() * 0.6f))
                    }
                }
            }
        } finally { bitmap.recycle() }
    }
}

/** Chip wake-up → precise word assembly → light collapse → black → app. */
@Composable
fun StartupIntro(
    started: Boolean = true,
    moving: Boolean = true,
    startBounds: androidx.compose.ui.geometry.Rect? = null,
    onIconBounds: (androidx.compose.ui.geometry.Rect) -> Unit = {},
    onIconDrawn: () -> Unit = {},
    onMotionFinished: () -> Unit = {},
    onFinished: () -> Unit
) {
    val clock = remember { Animatable(0f) }
    val handoff = remember { Animatable(0f) }
    var iconBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val owner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val currentFinished by rememberUpdatedState(onFinished)
    val currentMotionFinished by rememberUpdatedState(onMotionFinished)
    val artwork = remember { IntroArtwork() }
    val duration = remember(context) {
        val scale = runCatching {
            android.provider.Settings.Global.getFloat(context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
        if (scale == 0f) 0 else 2600
    }
    LaunchedEffect(owner, duration, started, moving) {
        if (!started || !moving) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            handoff.animateTo(1f, tween(if (duration == 0) 0 else 480,
                easing = androidx.compose.animation.core.FastOutSlowInEasing))
            currentMotionFinished()
            val remaining = ((1f - clock.value) * duration).toInt().coerceAtLeast(0)
            clock.animateTo(1f, tween(remaining, easing = LinearEasing))
            currentFinished()
            kotlinx.coroutines.awaitCancellation()
        }
    }
    val finishIntro = {
        currentMotionFinished()
        currentFinished()
    }
    BackHandler { finishIntro() }
    val iconIn = if (started) 1f else 0f
    Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = 1f - phase(clock.value, 0.84f, 1f) }
            .background(Color.Black)
            .semantics { contentDescription = "CoreGate 启动动画" }
            .pointerInput(Unit) {
                awaitEachGesture {
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - phase(clock.value, 0.68f, 0.80f) }) {
            val t = clock.value
            val center = Offset(size.width / 2f, size.height / 2f - 50.dp.toPx())
            val breath = phase(t, 0.02f, 0.36f)
            drawRect(Brush.radialGradient(
                listOf(IntroMint.copy(alpha = 0.11f * breath), Color.Transparent),
                center = center, radius = size.width * 0.67f))
            // A few short traces converge toward the chip, then quietly disappear.
            val trace = phase(t, 0.02f, 0.13f) * (1f - phase(t, 0.28f, 0.49f))
            val travel = phase(t, 0.04f, 0.43f)
            for (side in listOf(-1f, 1f)) for (row in -1..1) {
                val x = center.x + side * (112.dp.toPx() - travel * 35.dp.toPx())
                val y = center.y + row * 17.dp.toPx()
                drawLine(IntroMint.copy(alpha = trace * 0.22f),
                    Offset(x, y), Offset(x + side * 18.dp.toPx(), y), 1.dp.toPx())
            }
        }
        Column(
            Modifier.fillMaxWidth().graphicsLayer {
                alpha = 1f - phase(clock.value, 0.68f, 0.80f)
                // Geometry matches the system icon at the handoff frame.
            },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.size(140.dp).onGloballyPositioned { coordinates ->
                val origin = coordinates.positionOnScreen()
                val measured = androidx.compose.ui.geometry.Rect(origin,
                    androidx.compose.ui.geometry.Size(coordinates.size.width.toFloat(),
                        coordinates.size.height.toFloat()))
                iconBounds = measured
                onIconBounds(measured)
            }) {
                Box(Modifier.fillMaxSize().graphicsLayer {
                    alpha = iconIn
                    val target = iconBounds
                    val source = startBounds
                    val remaining = 1f - handoff.value
                    if (target != null && source != null && target.width > 0f && target.height > 0f) {
                        translationX = (source.center.x - target.center.x) * remaining
                        translationY = (source.center.y - target.center.y) * remaining
                        scaleX = 1f + (source.width / target.width - 1f) * remaining
                        scaleY = 1f + (source.height / target.height - 1f) * remaining
                    }
                }) {
                    // Black system splash and transparent foreground share the same artwork.
                    Image(painterResource(R.drawable.ic_launcher_foreground), "CoreGate 图标",
                        Modifier.fillMaxSize().drawWithContent {
                            drawContent()
                            if (started) onIconDrawn()
                        })
                }
                Canvas(Modifier.fillMaxSize()) {
                    val t = clock.value
                    val sweep = phase(t, 0f, 0.29f)
                    val fade = phase(t, 0f, 0.035f) * (1f - phase(t, 0.27f, 0.40f))
                    artwork.segment.reset()
                    artwork.measure.getSegment(
                        (sweep - 0.24f).coerceAtLeast(0f) * artwork.measure.length,
                        sweep * artwork.measure.length, artwork.segment, true)
                    drawIntoCanvas { canvas ->
                        val native = canvas.nativeCanvas
                        native.save()
                        native.scale(size.width / 512f, size.height / 512f)
                        // Match the existing vector's 0.85 inset around the 256px pivot.
                        native.translate(38.4f, 38.4f)
                        native.scale(0.85f, 0.85f)
                        artwork.stroke.strokeWidth = 12f
                        artwork.stroke.alpha = (fade * 25).toInt()
                        native.drawPath(artwork.segment, artwork.stroke)
                        artwork.stroke.strokeWidth = 3f
                        artwork.stroke.alpha = (fade * 235).toInt()
                        native.drawPath(artwork.segment, artwork.stroke)
                        native.restore()
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Canvas(Modifier.widthIn(max = 360.dp).fillMaxWidth(0.88f).height(76.dp)) {
                val t = clock.value
                val scale = size.width / 680f
                val top = (size.height - 120f * scale) / 2f
                val assembly = phase(t, 0.12f, 0.49f)
                val visible = phase(t, 0.12f, 0.20f)
                val solid = phase(t, 0.46f, 0.56f)
                if (solid < 1f) artwork.particles.forEach { p ->
                    val progress = ((assembly - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
                    val eased = 1f - (1f - progress) * (1f - progress) * (1f - progress)
                    val drift = 1f - eased
                    val point = Offset((p.x + p.dx * drift) * scale,
                        top + (p.y + p.dy * drift * drift) * scale)
                    val color = androidx.compose.ui.graphics.lerp(IntroMint, Color(0xFFECFFF6), eased)
                    drawCircle(color.copy(alpha = visible * (1f - solid) * (0.3f + eased * 0.7f)),
                        (p.radius * scale).coerceAtLeast(0.45.dp.toPx()), point)
                }
                if (solid > 0f) drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    native.save()
                    native.translate(0f, top)
                    native.scale(scale, scale)
                    artwork.solidPaint.alpha = (solid * 255).toInt()
                    native.drawPath(artwork.word, artwork.solidPaint)
                    val sheen = phase(t, 0.52f, 0.66f)
                    if (sheen > 0f && sheen < 1f) {
                        artwork.sheenMatrix.setTranslate(-100f + sheen * 880f, 0f)
                        artwork.sheenPaint.shader?.setLocalMatrix(artwork.sheenMatrix)
                        artwork.sheenPaint.alpha = (solid * 190).toInt()
                        native.drawPath(artwork.word, artwork.sheenPaint)
                    }
                    native.restore()
                }
            }
            Text(
                text = "让每一分电量，多一分陪伴",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .graphicsLayer {
                        alpha = phase(clock.value, 0.44f, 0.58f)
                    },
                color = Color(0xFFECFFF6).copy(alpha = 0.65f),
                fontSize = 13.sp,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}