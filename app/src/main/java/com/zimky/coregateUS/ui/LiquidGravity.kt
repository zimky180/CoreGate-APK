package com.zimky.coregateUS.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** One low-rate sensor subscription shared by all visible liquid surfaces. */
internal class LiquidGravity private constructor(context: Context) : SensorEventListener {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val displays = linkedMapOf<Any, Display?>()
    private var listening = false
    private var accelerometer = false
    private var filtered = Offset.Zero
    private var initialized = false
    private var previousTimestamp = 0L
    var direction by mutableStateOf(Offset.Zero)
        private set
    var lastSample by mutableLongStateOf(0L)
        private set

    fun acquire(key: Any, display: Display?) {
        displays[key] = display
        if (listening) return
        val sensors = manager ?: return
        for (type in intArrayOf(Sensor.TYPE_GRAVITY, Sensor.TYPE_ACCELEROMETER)) {
            val sensor = sensors.getDefaultSensor(type) ?: continue
            accelerometer = type == Sensor.TYPE_ACCELEROMETER
            initialized = false
            previousTimestamp = 0L
            listening = runCatching {
                sensors.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME,
                    Handler(Looper.getMainLooper()))
            }.getOrDefault(false)
            if (listening) break
        }
    }

    fun release(key: Any) {
        displays.remove(key)
        if (displays.isEmpty()) {
            manager?.unregisterListener(this)
            listening = false
            initialized = false
            lastSample = 0L
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!listening || event.values.size < 2) return
        val x = event.values[0]
        val y = event.values[1]
        if (!x.isFinite() || !y.isFinite()) return
        val rotated = when (displays.values.firstOrNull()?.rotation) {
            Surface.ROTATION_90 -> Offset(-y, -x)
            Surface.ROTATION_180 -> Offset(x, -y)
            Surface.ROTATION_270 -> Offset(y, x)
            else -> Offset(-x, y)
        }
        val dt = if (previousTimestamp == 0L) 0.02f else
            ((event.timestamp - previousTimestamp) / 1_000_000_000f).coerceIn(0.001f, 0.1f)
        previousTimestamp = event.timestamp
        val alpha = dt / ((if (accelerometer) 0.28f else 0.16f) + dt)
        filtered = if (initialized) filtered + (rotated - filtered) * alpha else rotated
        initialized = true
        direction = Offset((filtered.x / 7f).coerceIn(-1f, 1f),
            (filtered.y / 7f).coerceIn(-1f, 1f))
        lastSample = SystemClock.uptimeMillis()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        private var shared: LiquidGravity? = null
        fun get(context: Context): LiquidGravity = shared ?: LiquidGravity(context.applicationContext)
            .also { shared = it }
    }
}

@Composable
internal fun rememberLiquidGravity(): LiquidGravity {
    val context = LocalContext.current
    val view = LocalView.current
    val owner = LocalLifecycleOwner.current
    val gravity = remember(context) { LiquidGravity.get(context) }
    DisposableEffect(owner, gravity, view) {
        val key = Any()
        fun sync() {
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
                gravity.acquire(key, view.display)
            else gravity.release(key)
        }
        val observer = LifecycleEventObserver { _, _ -> sync() }
        owner.lifecycle.addObserver(observer)
        sync()
        onDispose {
            owner.lifecycle.removeObserver(observer)
            gravity.release(key)
        }
    }
    return gravity
}