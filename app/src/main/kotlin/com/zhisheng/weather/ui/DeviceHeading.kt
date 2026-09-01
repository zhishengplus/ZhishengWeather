package com.zhisheng.weather.ui

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

internal const val HEADING_PUBLISH_DEADBAND_DEG = 0.8f
internal const val UPRIGHT_MATRIX_Z_THRESHOLD = 0.75f

internal fun wrapDegrees(value: Float): Float = ((value % 360f) + 360f) % 360f

internal fun shortestAngleDelta(from: Float, to: Float): Float {
    var delta = wrapDegrees(to - from)
    if (delta > 180f) delta -= 360f
    return delta
}

internal fun lerpAngleDegrees(from: Float, to: Float, t: Float): Float =
    wrapDegrees(from + shortestAngleDelta(from, to) * t)

/** 箭头在屏幕上的转角：指向真实来风方向，不随手机转动。 */
internal fun windNeedleScreenRotation(windFromDeg: Float, headingDeg: Float): Float =
    wrapDegrees(windFromDeg - headingDeg)

internal fun magneticDeclinationDeg(latitude: Double?, longitude: Double?, nowMillis: Long = System.currentTimeMillis()): Float {
    if (latitude == null || longitude == null) return 0f
    if (!latitude.isFinite() || !longitude.isFinite()) return 0f
    return GeomagneticField(latitude.toFloat(), longitude.toFloat(), 0f, nowMillis).declination
}

internal fun deviceHeldUpright(rotationMatrix: FloatArray): Boolean {
    if (rotationMatrix.size < 9) return true
    return abs(rotationMatrix[8]) < UPRIGHT_MATRIX_Z_THRESHOLD
}

internal fun compassWorldAxes(displayRotation: Int, upright: Boolean): Pair<Int, Int> {
    if (upright) {
        return when (displayRotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Z to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Z
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Z to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Z
        }
    }
    return when (displayRotation) {
        Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
        Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
        Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
        else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
    }
}

internal fun azimuthFromRotationVector(rotationVector: FloatArray, displayRotation: Int): Float {
    val rotationMatrix = FloatArray(9)
    val remapped = FloatArray(9)
    val orientation = FloatArray(3)
    SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector)
    val upright = deviceHeldUpright(rotationMatrix)
    val (axisX, axisY) = compassWorldAxes(displayRotation, upright)
    SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, remapped)
    SensorManager.getOrientation(remapped, orientation)
    return wrapDegrees(Math.toDegrees(orientation[0].toDouble()).toFloat())
}

internal class HeadingFilter(private val alpha: Float = 0.16f) {
    private var sinAcc = 0.0
    private var cosAcc = 1.0
    var value: Float? = null
        private set

    fun push(sample: Float): Float {
        val rad = Math.toRadians(sample.toDouble())
        val s = sin(rad)
        val c = cos(rad)
        if (value == null) {
            sinAcc = s
            cosAcc = c
            value = wrapDegrees(sample)
            return value!!
        }
        sinAcc = sinAcc * (1.0 - alpha) + s * alpha
        cosAcc = cosAcc * (1.0 - alpha) + c * alpha
        val blended = wrapDegrees(Math.toDegrees(atan2(sinAcc, cosAcc)).toFloat())
        value = blended
        return blended
    }
}

/**
 * 设备指向的真北方位角（0=北，顺时针）。
 * 不依赖定位权限；经纬度只用来把磁北校成真北，任意已有坐标的城市都能用。
 */
@Composable
fun rememberWorldHeadingDegrees(latitude: Double? = null, longitude: Double? = null): Float? {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val declination = remember(latitude, longitude) { magneticDeclinationDeg(latitude, longitude) }
    val declinationState = rememberUpdatedState(declination)
    var heading by remember { mutableStateOf<Float?>(null) }

    DisposableEffect(lifecycleOwner) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        if (sensor == null) {
            heading = null
            return@DisposableEffect onDispose { }
        }
        val filter = HeadingFilter()
        val listener = object : SensorEventListener {
            @Suppress("DEPRECATION")
            override fun onSensorChanged(event: SensorEvent) {
                val type = event.sensor.type
                if (type != Sensor.TYPE_ROTATION_VECTOR && type != Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR) return
                val vector = event.values.copyOf(event.values.size.coerceAtMost(5))
                val magnetic = azimuthFromRotationVector(vector, view.display.rotation)
                val next = filter.push(wrapDegrees(magnetic + declinationState.value))
                val current = heading
                if (current == null || abs(shortestAngleDelta(current, next)) >= HEADING_PUBLISH_DEADBAND_DEG) {
                    heading = next
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        fun bind() {
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        fun unbind() {
            sensorManager.unregisterListener(listener)
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> bind()
                Lifecycle.Event.ON_PAUSE -> unbind()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) bind()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            unbind()
        }
    }
    return heading
}
