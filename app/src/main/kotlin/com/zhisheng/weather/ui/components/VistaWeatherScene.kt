package com.zhisheng.weather.ui.components

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.zhisheng.weather.R
import com.zhisheng.weather.data.AmbienceLevel
import com.zhisheng.weather.model.ThermalModifier
import com.zhisheng.weather.model.WeatherIntensity
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import com.zhisheng.weather.ui.theme.ZhishengPalette
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/* 澄空天气场景层 v5（对标小米天气）：
 * 美术 = 每种天气 × 昼夜一幅 AI 生成整幅插画（drawable-nodpi/amb_bg_*，风格统一、可随包分发）——
 *        美的部分由插画承担，代码只让画面活着：cover 裁切 + 极慢漂移呼吸 + 底部主题色纱。
 * 动效 = 密集低透明度粒子（雨幕/雪花/冰雹/沙线/风弧/星闪）叠加在插画上，
 *        雨丝倾角与插画一致，终端速度 + 阵风成簇 + 60fps；闪电加法闪白；夜间对日间图压暗。
 */

private const val SCENE_FADE_MS = 450

internal const val SCENE_PARTICLE_CAP = 200

@Composable
internal fun VistaWeatherScene(
    spec: AmbienceSpec,
    level: AmbienceLevel,
    night: Boolean,
    modifier: Modifier = Modifier,
    parallax: () -> Float = { 0f },
) {
    if (level == AmbienceLevel.OFF || spec.kind == AmbienceKind.NONE) return
    val palette = LocalZhishengPalette.current
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val preview = LocalInspectionMode.current

    val strength = when (level) {
        AmbienceLevel.OFF -> 0f
        AmbienceLevel.SUBTLE -> 0.5f
        AmbienceLevel.VIVID -> 0.78f
        AmbienceLevel.INTENSE -> 1f
    }

    val fade = remember { Animatable(if (preview) 1f else 0f) }
    LaunchedEffect(spec.kind, night) {
        if (preview) return@LaunchedEffect
        fade.snapTo(0f)
        fade.animateTo(1f, tween(SCENE_FADE_MS, easing = FastOutSlowInEasing))
    }

    val clock = remember { mutableDoubleStateOf(if (preview) 4.25 else SystemClock.uptimeMillis() / 1000.0) }
    val power = remember(context) { context.getSystemService(PowerManager::class.java) }
    var animationsEnabled by remember { mutableStateOf(ValueAnimator.areAnimatorsEnabled()) }
    var powerSave by remember { mutableStateOf(power?.isPowerSaveMode == true) }
    if (!preview) {
        DisposableEffect(context, lifecycle) {
            fun refresh() {
                animationsEnabled = ValueAnimator.areAnimatorsEnabled()
                powerSave = power?.isPowerSaveMode == true
            }
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) = refresh()
            }
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) = refresh()
            }
            val resume = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
            lifecycle.addObserver(resume)
            context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
            ContextCompat.registerReceiver(context, receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
            onDispose {
                lifecycle.removeObserver(resume)
                context.contentResolver.unregisterContentObserver(observer)
                context.unregisterReceiver(receiver)
            }
        }
    }
    LaunchedEffect(lifecycle, spec.kind, animationsEnabled, powerSave, preview) {
        if (preview || !sceneMotionAllowed(spec.kind, animationsEnabled, powerSave)) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = 0L
            while (isActive) {
                withFrameNanos { frame ->
                    // 60fps：雨雪的快速运动在 30fps 下会顿挫；云层慢速运动不受影响
                    if (last == 0L || frame - last >= 16_666_667L) {
                        clock.doubleValue = SystemClock.uptimeMillis() / 1000.0
                        last = frame
                    }
                }
            }
        }
    }

    // 美术素材只解码一次；背景插画按当前天气惰性解码（切换天气时才解码一次 ~20ms）
    val bundled = remember { BundledSprites(context) }
    val background = remember(spec.kind, night) { bundled.background(spec.kind, night) }
    val particles = remember(palette.isLight) {
        val c0 = SceneColors(palette)
        ParticleSpriteSet(palette.isLight, c0.rain.toArgb(), 0xFFFFFFFF.toInt())
    }
    val assets = remember { SceneAssets() }
    val density = LocalDensity.current.density
    // 重力传感器：手机倾斜 → 整个场景轻微跟着偏（成熟天气 App 的"活"感来源）
    val tilt = remember { FloatArray(2) } // [rotation2D 左右, rotation3D 前后]，度
    DisposableEffect(context) {
        val sm = context.getSystemService(android.hardware.SensorManager::class.java)
        val gravity = sm?.getDefaultSensor(android.hardware.Sensor.TYPE_GRAVITY)
        val listener = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(ev: android.hardware.SensorEvent) {
                val ax = ev.values[0] / 9.81f
                val ay = ev.values[1] / 9.81f
                val az = ev.values[2] / 9.81f
                tilt[0] = Math.toDegrees(Math.atan2(ax.toDouble(), kotlin.math.sqrt((ay * ay + az * az).toDouble().coerceAtLeast(1e-4)))).toFloat()
                tilt[1] = Math.toDegrees(Math.atan2(az.toDouble(), kotlin.math.sqrt((ax * ax + ay * ay).toDouble().coerceAtLeast(1e-4)))).toFloat()
            }
            override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
        }
        gravity?.let { sm?.registerListener(listener, it, android.hardware.SensorManager.SENSOR_DELAY_GAME) }
        onDispose { sm?.unregisterListener(listener) }
    }
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val gain = strength * fade.value
            if (gain <= 0.002f) return@Canvas
            drawWeatherScene(spec, level, night, clock.doubleValue, gain, density, palette, assets, bundled, background, particles, parallax, tilt)
        }
    }
}

internal fun sceneMotionAllowed(kind: AmbienceKind, animationsEnabled: Boolean, powerSave: Boolean): Boolean =
    kind != AmbienceKind.NONE && animationsEnabled && !powerSave

/** 粒子预算：档位是主旋钮（0.4/0.7/1），天气强度细调（0.55~1），总量封顶。 */
internal fun sceneParticleCount(kind: AmbienceKind, level: AmbienceLevel, intensity: WeatherIntensity? = null): Int {
    if (level == AmbienceLevel.OFF || kind == AmbienceKind.NONE) return 0
    val maximum = when (kind) {
        AmbienceKind.CLEAR_DAY, AmbienceKind.PARTLY_CLOUDY, AmbienceKind.OVERCAST,
        AmbienceKind.FOG, AmbienceKind.HAZE -> 0
        AmbienceKind.STARFIELD -> 40
        AmbienceKind.DRIZZLE -> 80
        AmbienceKind.RAIN -> 150
        AmbienceKind.STORM -> 200
        AmbienceKind.SNOW -> 120
        AmbienceKind.SLEET -> 110
        AmbienceKind.HAIL -> 90
        AmbienceKind.FREEZING_RAIN -> 100
        AmbienceKind.SAND -> 120
        AmbienceKind.WIND -> 48
        AmbienceKind.NONE -> 0
    }
    if (maximum == 0) return 0
    val budget = when (level) {
        AmbienceLevel.OFF -> 0f
        AmbienceLevel.SUBTLE -> 0.4f
        AmbienceLevel.VIVID -> 0.7f
        AmbienceLevel.INTENSE -> 1f
    }
    val density = when (intensity) {
        WeatherIntensity.LIGHT -> 0.55f
        WeatherIntensity.MODERATE -> 0.72f
        WeatherIntensity.HEAVY -> 0.88f
        WeatherIntensity.EXTREME, null -> 1f
    }
    return (maximum * budget * density).toInt().coerceIn(0, SCENE_PARTICLE_CAP)
}

internal fun rainTiltDp(windSpeedKmh: Float, windDirectionDeg: Float): Float {
    if (windSpeedKmh <= 0f) return 0f
    val eastComponent = sin((windDirectionDeg + 180.0) * PI / 180.0).toFloat()
    return eastComponent * min(26f, windSpeedKmh * 0.45f)
}

internal fun sceneMaskY(yDp: Float, heightDp: Float): Float {
    if (heightDp <= 0f || yDp !in 0f..heightDp) return 0f
    val top = (yDp / 56f).coerceIn(0f, 1f)
    val bottomStart = heightDp * 0.92f
    val bottom = if (yDp <= bottomStart) 1f else (1f - (yDp - bottomStart) / (heightDp * 0.08f)).coerceIn(0f, 1f)
    return top * bottom
}

internal fun stormFlashAlpha(timeSeconds: Double, phase: Double): Float {
    val cycle = 5.4 + phase * 3.4
    val t = timeSeconds % cycle
    return if (t in 2.0..2.14 || t in 2.34..2.46) 0.16f else 0f
}

internal fun stormBoltVisible(timeSeconds: Double, phase: Double): Boolean {
    val cycle = 5.4 + phase * 3.4
    val t = timeSeconds % cycle
    return t in 2.0..2.2
}

/* ─────────── 美术素材（全部 CC0/自产）：AI 整幅天气插画（bg）+ 雪花贴图 ─────────── */

/** 每种天气 × 昼夜 → 背景插画资源。夜间缺图的天气复用日间图 + 代码压暗纱。 */
private fun backgroundResOf(kind: AmbienceKind, night: Boolean): Int = when (kind) {
    AmbienceKind.CLEAR_DAY -> R.drawable.amb_bg_clear_day
    AmbienceKind.STARFIELD -> R.drawable.amb_bg_clear_night
    AmbienceKind.PARTLY_CLOUDY -> R.drawable.amb_bg_partly
    AmbienceKind.OVERCAST -> R.drawable.amb_bg_overcast
    AmbienceKind.DRIZZLE, AmbienceKind.RAIN, AmbienceKind.SLEET, AmbienceKind.FREEZING_RAIN, AmbienceKind.HAIL ->
        if (night) R.drawable.amb_bg_rain_night else R.drawable.amb_bg_rain_day
    AmbienceKind.STORM -> R.drawable.amb_bg_storm
    AmbienceKind.SNOW -> if (night) R.drawable.amb_bg_snow_night else R.drawable.amb_bg_snow_day
    AmbienceKind.FOG -> R.drawable.amb_bg_fog
    AmbienceKind.HAZE -> R.drawable.amb_bg_haze
    AmbienceKind.SAND -> R.drawable.amb_bg_sand
    AmbienceKind.WIND -> R.drawable.amb_bg_wind
    AmbienceKind.NONE -> 0
}

private class BundledSprites(private val context: Context) {
    val snow = BitmapFactory.decodeResource(context.resources, R.drawable.amb_snow).asImageBitmap()
    val clouds = listOf(
        BitmapFactory.decodeResource(context.resources, R.drawable.amb_cloud_01).asImageBitmap(),
        BitmapFactory.decodeResource(context.resources, R.drawable.amb_cloud_03).asImageBitmap(),
        BitmapFactory.decodeResource(context.resources, R.drawable.amb_cloud_09).asImageBitmap(),
    )
    val glow = BitmapFactory.decodeResource(context.resources, R.drawable.amb_glow).asImageBitmap()
    fun background(kind: AmbienceKind, night: Boolean): ImageBitmap? {
        val res = backgroundResOf(kind, night)
        if (res == 0) return null
        return BitmapFactory.decodeResource(context.resources, res).asImageBitmap()
    }

    // 云的浓淡：浅色主题压暗一档才在亮天上读得出；深色主题染成月下蓝灰
    val cloudFilterLight: ColorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(0.86f, 0.89f, 0.94f, 1f) })
    val cloudFilterDark: ColorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(0.60f, 0.68f, 0.80f, 1f) })
    // 光斑色温：白光贴图 × 倍色 = 暖阳 / 冷月
    val glowFilterWarm: ColorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(1f, 0.84f, 0.58f, 1f) })
    val glowFilterCool: ColorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(0.72f, 0.82f, 1f, 1f) })
}

/* ─────────── 粒子精灵：沿运动方向渐隐的雨滴纺锤 / 柔边雪晶 / 水雾带 ─────────── */

private class ParticleSpriteSet(light: Boolean, rainColor: Int, snowColor: Int) {
    // 雨滴纺锤：头亮尾隐 + 两端收口；近景失焦用白雾纺锤，远景用实色纺锤
    val rainStreak = bakeStreak(rainColor, headPeak = 0.78f, tail = 0.0f).asImageBitmap()
    val softStreak = bakeStreak(0xFFE8F0F8.toInt(), headPeak = 0.55f, tail = 0.0f).asImageBitmap()
    val glintStreak = bakeStreak(0xFFFFFFFF.toInt(), headPeak = 0.9f, tail = 0.25f).asImageBitmap()
    val flake = bakeBlob(snowColor).asImageBitmap()
    val mist = bakeMist(0xFFDDE6EE.toInt()).asImageBitmap()
}

/** 纺锤：垂直渐变，y=0 尾（透明）→ headPeak 处最亮 → 底部收口；横向中心实边缘虚。 */
private fun bakeStreak(argb: Int, headPeak: Float, tail: Float): Bitmap {
    val w = 12
    val h = 128
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val base = android.graphics.Color.alpha(argb) / 255f
    val r = android.graphics.Color.red(argb)
    val g = android.graphics.Color.green(argb)
    val b = android.graphics.Color.blue(argb)
    val xFall = FloatArray(w) { x -> sin(((x + 0.5f) / w) * PI.toFloat()).toFloat() }
    val pixels = IntArray(w * h)
    for (y in 0 until h) {
        val t = y / (h - 1f)
        val longitudinal = when {
            t < headPeak -> (t / headPeak).let { it * it * (3f - 2f * it) } * headPeak
            else -> headPeak + (1f - headPeak) * (1f - ((t - headPeak) / (1f - headPeak)).let { it * it })
        }
        val a = base * longitudinal
        val ai = (a.coerceIn(0f, 1f) * 255f).toInt()
        for (x in 0 until w) {
            pixels[y * w + x] = ((ai * xFall[x]).toInt() shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
    bmp.setPixels(pixels, 0, w, 0, 0, w, h)
    return bmp
}

/** 柔边雪晶：径向渐变，中心 0.85 → 0.55 处衰减 → 边缘透明。 */
private fun bakeBlob(argb: Int): Bitmap {
    val size = 64
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val base = android.graphics.Color.alpha(argb) / 255f
    val r = android.graphics.Color.red(argb); val g = android.graphics.Color.green(argb); val b = android.graphics.Color.blue(argb)
    val pixels = IntArray(size * size)
    val c = (size - 1) / 2f
    for (y in 0 until size) {
        for (x in 0 until size) {
            val d = kotlin.math.sqrt((x - c) * (x - c) + (y - c) * (y - c)) / c
            val a = if (d >= 1f) 0f else (1f - d).let { core -> (0.25f + 0.75f * core * core) * base }
            pixels[y * size + x] = ((a.coerceIn(0f, 1f) * 255f).toInt() shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
    bmp.setPixels(pixels, 0, size, 0, 0, size, size)
    return bmp
}

/** 水雾带：横向白雾，上缘渐入下缘实。 */
private fun bakeMist(argb: Int): Bitmap {
    val w = 256
    val h = 64
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val base = android.graphics.Color.alpha(argb) / 255f
    val r = android.graphics.Color.red(argb); val g = android.graphics.Color.green(argb); val b = android.graphics.Color.blue(argb)
    val pixels = IntArray(w * h)
    for (y in 0 until h) {
        val t = y / (h - 1f)
        val v = t * t * (3f - 2f * t)
        val a = base * v
        for (x in 0 until w) pixels[y * w + x] = ((a.coerceIn(0f, 1f) * 255f).toInt() shl 24) or (r shl 16) or (g shl 8) or b
    }
    bmp.setPixels(pixels, 0, w, 0, 0, w, h)
    return bmp
}

/* ─────────── 场景装配 ─────────── */

private class SceneAssets {
    val count = 160
    val x = FloatArray(count)
    val y = FloatArray(count)
    val phase = FloatArray(count)
    val speed = FloatArray(count)
    val size = FloatArray(count)

    init {
        var state = 0x5E17C1A9
        fun next(): Float {
            state = state * 1664525 + 1013904223
            return ((state ushr 8) and 0xFFFF) / 65535f
        }
        for (i in 0 until count) {
            x[i] = next()
            y[i] = next()
            phase[i] = next()
            speed[i] = next()
            size[i] = next()
        }
    }
}

private class SceneColors(p: ZhishengPalette) {
    val light = p.isLight
    val bg = p.bg
    val sun = Color(0xFFFFD9A0)
    val sunCore = Color(0xFFFFEBC4)
    val moon = Color(0xFFC9D6F5)
    val rain = if (light) Color(0xFF6E88A0) else Color(0xFF9FB6CB)
    val snow = if (light) Color(0xFFFFFFFF) else Color(0xFFE8F0FA)
    val fog = if (light) Color(0xFFF1F4F6) else Color(0xFF35465A)
    val haze = if (light) Color(0xFFC9BFA8) else Color(0xFF8A8468)
    val sand = Color(0xFFCDA96A)
    val bolt = Color(0xFFEAF4FF)
    val star = if (light) Color(0xFF8DA1B8) else Color(0xFFD8E2F4)
    val wind = if (light) Color(0xFF8FA8BC) else Color(0xFFA9C0D4)
    fun nighted(color: Color): Color = lerp(color, Color(0xFFC4D2EA), 0.35f)
}



private fun DrawScope.drawWeatherScene(
    spec: AmbienceSpec,
    level: AmbienceLevel,
    night: Boolean,
    time: Double,
    gain: Float,
    density: Float,
    palette: ZhishengPalette,
    assets: SceneAssets,
    bundled: BundledSprites,
    background: ImageBitmap?,
    particles: ParticleSpriteSet,
    scrollPx: () -> Float,
    tilt: FloatArray,
) {
    val c = SceneColors(palette)
    val kind = spec.kind
    // 成熟方案的滚动处理：天气层被内容 1:1 推走，滚过一屏整层停画（永不断层）
    val scrollPxValue = scrollPx()
    val scrollRate = (scrollPxValue / size.height).coerceIn(0f, 1f)
    if (scrollRate >= 1f) return
    val sceneAlpha = gain * (1f - scrollRate)

    // 陀螺仪平滑（每帧向目标靠拢，避免抖动）
    smoothTilt[0] += (tilt[0] - smoothTilt[0]) * 0.08f
    smoothTilt[1] += (tilt[1] - smoothTilt[1]) * 0.08f

    drawIntoCanvas { canvas ->
        canvas.save()
        canvas.translate(0f, -scrollPxValue)
        // 陀螺仪：整场景绕屏幕中心微转 + 平移（breezy 同款手法，衰减到 1/3）
        rotate(degrees = smoothTilt[0] * 0.35f, pivot = Offset(size.width / 2f, size.height / 2f)) {
            translate(
                smoothTilt[0] * 0.12f * density,
                smoothTilt[1] * 0.12f * density
            ) {
                drawBackgroundArt(background, kind, night, time, sceneAlpha, density, c, scrollPxValue)
                drawSkyGlow(kind, night, time, sceneAlpha, density, bundled)
                when (kind) {
                    AmbienceKind.DRIZZLE -> drawRain(spec, level, time, sceneAlpha, density, c, assets, particles, smoothTilt, RainStyle.FINE)
                    AmbienceKind.RAIN -> drawRain(spec, level, time, sceneAlpha, density, c, assets, particles, smoothTilt, RainStyle.NORMAL)
                    AmbienceKind.SLEET -> {
                        drawRain(spec, level, time, sceneAlpha, density, c, assets, particles, smoothTilt, RainStyle.SLEET)
                        drawSnow(spec, level, time, sceneAlpha * 0.8f, density, c, assets, bundled, particles, share = 0.5f)
                    }
                    AmbienceKind.FREEZING_RAIN -> {
                        drawRain(spec, level, time, sceneAlpha, density, c, assets, particles, smoothTilt, RainStyle.COLD)
                        drawFreezingGlints(time, sceneAlpha, density, c, assets)
                    }
                    AmbienceKind.STORM -> {
                        drawRain(spec, level, time, sceneAlpha, density, c, assets, particles, smoothTilt, RainStyle.STORM)
                        drawStormLightning(time, sceneAlpha, density, c)
                    }
                    AmbienceKind.SNOW -> drawSnow(spec, level, time, sceneAlpha, density, c, assets, bundled, particles, share = 1f)
                    AmbienceKind.HAIL -> drawHail(level, time, sceneAlpha, density, c, assets)
                    AmbienceKind.SAND -> drawSandStreaks(spec, level, time, sceneAlpha, density, c, assets)
                    AmbienceKind.WIND -> drawWindArcs(spec, level, time, sceneAlpha, density, c, assets)
                    AmbienceKind.STARFIELD -> drawStarfield(level, time, sceneAlpha, density, c, assets, scrollPxValue)
                    else -> Unit
                }
                // 前景漂移云：让"云在动"可见
                if (kind in setOf(
                        AmbienceKind.PARTLY_CLOUDY, AmbienceKind.OVERCAST,
                        AmbienceKind.DRIZZLE, AmbienceKind.RAIN, AmbienceKind.SLEET,
                        AmbienceKind.FREEZING_RAIN, AmbienceKind.STORM,
                        AmbienceKind.SNOW, AmbienceKind.HAIL,
                    )
                ) {
                    drawDriftingClouds(kind, night, time, sceneAlpha, density, c, bundled, scrollPxValue)
                }
            }
        }
        canvas.restore()
    }
    // 底部主题色纱钉在屏幕上（不随场景平移，内容区始终干净）
    drawBottomScrim(c, gain)
}

/** 陀螺仪平滑值（绘制线程读，避免每帧重组）。 */
private val smoothTilt = floatArrayOf(0f, 0f)

/** 整幅插画背景：cover 裁切 + 极慢漂移呼吸 + 顶部文字保护纱 + 夜间压暗。 */
private fun DrawScope.drawBackgroundArt(
    background: ImageBitmap?,
    kind: AmbienceKind,
    night: Boolean,
    time: Double,
    gain: Float,
    density: Float,
    c: SceneColors,
    scrollPxValue: Float,
) {
    if (background != null) {
        val imgAspect = background.width.toFloat() / background.height
        val screenAspect = size.width / size.height
        val baseScale = 1.04f
        val dstW: Float
        val dstH: Float
        if (imgAspect > screenAspect) {
            dstH = size.height * baseScale
            dstW = dstH * imgAspect
        } else {
            dstW = size.width * baseScale
            dstH = dstW / imgAspect
        }
        val driftX = sin(time * 0.013).toFloat() * 6f * density
        val dx = (size.width - dstW) / 2f + driftX
        val dy = (size.height - dstH) / 2f - scrollPxValue * 0.25f // 背景以 1/4 速率上移：有余量、无断层
        drawImage(
            image = background,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(background.width, background.height),
            dstOffset = IntOffset(dx.toInt(), dy.toInt()),
            dstSize = IntSize(dstW.toInt().coerceAtLeast(1), dstH.toInt().coerceAtLeast(1)),
            alpha = gain.coerceIn(0f, 1f),
            filterQuality = FilterQuality.Medium,
        )
    }
    // 顶部文字保护纱（NN/g 规范：文字区 40% 上下透明度渐变）
    val topH = size.height * 0.30f
    drawRect(
        Brush.verticalGradient(listOf(c.bg.copy(alpha = 0.55f), Color.Transparent), startY = 0f, endY = topH),
        topLeft = Offset(0f, 0f),
        size = Size(size.width, topH),
    )
    // 复用日间图的夜间天气压暗（晴夜/雨夜/雪夜/雷暴有自己的夜景图）
    val dayArtAtNight = night && kind in setOf(
        AmbienceKind.PARTLY_CLOUDY, AmbienceKind.OVERCAST, AmbienceKind.WIND,
        AmbienceKind.HAZE, AmbienceKind.FOG, AmbienceKind.SAND,
    )
    if (dayArtAtNight) drawRect(Color(0xFF0B1626).copy(alpha = 0.40f * gain))
}

/** 晴/多云：在插画的太阳/月亮位置叠一层呼吸光斑（加法，跟 hero 图标呼应）。 */
private fun DrawScope.drawSkyGlow(
    kind: AmbienceKind,
    night: Boolean,
    time: Double,
    gain: Float,
    density: Float,
    bundled: BundledSprites,
) {
    val anchor = when (kind) {
        AmbienceKind.CLEAR_DAY -> 0.74f to 0.15f
        AmbienceKind.PARTLY_CLOUDY -> if (!night) 0.74f to 0.14f else return
        AmbienceKind.STARFIELD -> 0.80f to 0.12f
        else -> return
    }
    val filter = if (night) bundled.glowFilterCool else bundled.glowFilterWarm
    val pulse = (0.30f + 0.10f * sin(time * 0.5).toFloat()) * gain
    val glowSize = size.width * (0.52f + 0.04f * sin(time * 0.35).toFloat())
    drawImage(
        image = bundled.glow,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(bundled.glow.width, bundled.glow.height),
        dstOffset = IntOffset(
            (size.width * anchor.first - glowSize / 2f).toInt(),
            (size.height * anchor.second - glowSize / 2f).toInt(),
        ),
        dstSize = IntSize(glowSize.toInt().coerceAtLeast(1), glowSize.toInt().coerceAtLeast(1)),
        alpha = pulse.coerceIn(0f, 1f),
        colorFilter = filter,
        blendMode = BlendMode.Plus,
        filterQuality = FilterQuality.Medium,
    )
}

/** 前景漂移云：手绘云贴图三层速度横漂（无缝环绕），云"在动"的证据。 */
private fun DrawScope.drawDriftingClouds(
    kind: AmbienceKind,
    night: Boolean,
    time: Double,
    gain: Float,
    density: Float,
    c: SceneColors,
    bundled: BundledSprites,
    scrollPxValue: Float,
) {
    val filter = if (night) bundled.cloudFilterDark else bundled.cloudFilterLight
    data class DriftCloud(val fraction: Float, val yFraction: Float, val widthFraction: Float, val speed: Float, val alpha: Float)
    val clouds = when (kind) {
        AmbienceKind.PARTLY_CLOUDY -> listOf(
            DriftCloud(0.1f, 0.16f, 0.52f, 14f, 0.42f),
            DriftCloud(0.55f, 0.30f, 0.64f, 22f, 0.5f),
        )
        AmbienceKind.OVERCAST -> listOf(
            DriftCloud(0.05f, 0.10f, 0.75f, 10f, 0.5f),
            DriftCloud(0.45f, 0.22f, 0.85f, 15f, 0.55f),
            DriftCloud(0.75f, 0.32f, 0.7f, 20f, 0.5f),
        )
        else -> listOf(
            DriftCloud(0.15f, 0.08f, 0.7f, 16f, 0.4f),
            DriftCloud(0.6f, 0.18f, 0.85f, 24f, 0.45f),
        )
    }
    clouds.forEachIndexed { i, cloud ->
        val bmp = bundled.clouds[i % bundled.clouds.size]
        val dstW = size.width * cloud.widthFraction
        val spanPx = size.width + dstW
        val xf = (((cloud.fraction + time * cloud.speed * density / spanPx) % 1.0) + 1.0) % 1.0
        val cx = (xf * spanPx - dstW / 2f).toFloat()
        val cy = size.height * cloud.yFraction - scrollPxValue * 0.5f
        drawImage(
            image = bmp,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(bmp.width, bmp.height),
            dstOffset = IntOffset((cx - dstW / 2f).toInt(), (cy - dstW / 2f).toInt()),
            dstSize = IntSize(dstW.toInt().coerceAtLeast(1), dstW.toInt().coerceAtLeast(1)),
            alpha = (cloud.alpha * gain).coerceIn(0f, 1f),
            colorFilter = filter,
            filterQuality = FilterQuality.Medium,
        )
    }
}

/** 底部主题色纱钉在屏幕上：内容区始终干净可读。 */
private fun DrawScope.drawBottomScrim(c: SceneColors, gain: Float) {
    val scrimTop = size.height * 0.42f
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, c.bg.copy(alpha = 0.92f)), startY = scrimTop, endY = size.height),
        topLeft = Offset(0f, scrimTop),
        size = Size(size.width, size.height - scrimTop),
    )
}

private enum class RainStyle { FINE, NORMAL, SLEET, COLD, STORM }

/** 雨：远/中/近三层景深 + 终端速度 + 运动模糊拖尾 + 底部水雾与溅花。 */
private fun DrawScope.drawRain(
    spec: AmbienceSpec,
    level: AmbienceLevel,
    time: Double,
    gain: Float,
    density: Float,
    c: SceneColors,
    assets: SceneAssets,
    particles: ParticleSpriteSet,
    tilt: FloatArray,
    style: RainStyle,
) {
    val total = sceneParticleCount(spec.kind, level, spec.intensity)
    // 整层统一倾角：基础 -10°（与插画雨向一致）+ 陀螺仪左右倾斜
    val layerDeg = -10f + smoothTilt[0] * 0.6f
    val heightDp = size.height / density
    val widthDp = size.width / density
    val wrapWidth = widthDp + 60f
    // soft = 近景失焦：大而虚；glint = 逆光高光滴（加法混合）
    data class RainLayer(val share: Float, val speed: Float, val len: Float, val widthDp: Float, val alpha: Float, val soft: Boolean, val glint: Boolean)
    val layers = when (style) {
        RainStyle.FINE -> listOf(
            RainLayer(0.55f, 300f, 11f, 1.8f, 0.24f, false, false),
            RainLayer(0.45f, 380f, 16f, 2.4f, 0.30f, true, false))
        RainStyle.NORMAL -> listOf(
            RainLayer(0.42f, 360f, 15f, 2.0f, 0.24f, false, false),
            RainLayer(0.33f, 480f, 25f, 2.6f, 0.32f, false, true),
            RainLayer(0.25f, 580f, 38f, 3.6f, 0.30f, true, false))
        RainStyle.SLEET -> listOf(
            RainLayer(0.5f, 330f, 13f, 2.0f, 0.22f, false, false),
            RainLayer(0.5f, 470f, 23f, 2.6f, 0.30f, true, false))
        RainStyle.COLD -> listOf(
            RainLayer(0.5f, 340f, 13f, 2.0f, 0.23f, false, false),
            RainLayer(0.5f, 500f, 25f, 2.7f, 0.32f, true, true))
        RainStyle.STORM -> listOf(
            RainLayer(0.40f, 430f, 20f, 2.2f, 0.26f, false, true),
            RainLayer(0.33f, 560f, 32f, 3.0f, 0.36f, false, false),
            RainLayer(0.27f, 680f, 48f, 4.0f, 0.36f, true, true))
    }
    var cursor = 0
    layers.forEachIndexed { li, layer ->
        val count = (total * layer.share).toInt()
        val sprite = if (layer.soft) particles.softStreak else particles.rainStreak
        for (i in 0 until count) {
            val a = assets.at(cursor * 3 + i + li * 41)
            // 阵风：每滴独立相位 → 雨成簇成阵，不是均匀壁纸
            val gust = 0.72f + 0.28f * sin(time * 0.45 + a.phase * 6.283).toFloat()
            val speed = layer.speed * (0.7f + a.speed * 1.0f) * gust
            val cycleDp = heightDp + 150f
            val yDp = ((time * speed + a.phase * cycleDp) % cycleDp).toFloat()
            val lenDp = layer.len * (0.6f + a.size * 0.9f)
            val xBase = a.x * wrapWidth - 30f
            val xDp = (((xBase % wrapWidth) + wrapWidth) % wrapWidth) - 30f
            val alpha = layer.alpha * gain * (0.5f + a.size * 1.0f) * sceneMaskY(yDp, heightDp)
            if (alpha < 0.006f) continue
            val center = Offset(xDp * density, yDp * density)
            val lenPx = (lenDp * density).toInt().coerceAtLeast(2)
            val wPx = (layer.widthDp * density).toInt().coerceAtLeast(1)
            rotate(degrees = layerDeg, pivot = Offset(size.width / 2f, size.height / 2f)) {
                drawImage(
                    image = sprite,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(12, 128),
                    dstOffset = IntOffset((center.x - wPx / 2f).toInt(), (center.y - lenPx / 2f).toInt()),
                    dstSize = IntSize(wPx, lenPx),
                    alpha = alpha.coerceIn(0f, 1f),
                    filterQuality = FilterQuality.Medium,
                )
                // 逆光闪点：一部分滴加白色加法高光，雨在亮天上才读得到
                if (layer.glint && a.speed > 0.82f) {
                    drawImage(
                        image = particles.glintStreak,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(12, 128),
                        dstOffset = IntOffset((center.x - wPx / 2f).toInt(), (center.y - lenPx / 2f).toInt()),
                        dstSize = IntSize(wPx, lenPx),
                        alpha = (alpha * 0.9f).coerceIn(0f, 1f),
                        blendMode = BlendMode.Plus,
                        filterQuality = FilterQuality.Medium,
                    )
                }
            }
        }
        cursor += count
    }
    // 底部水雾：烘焙雾带精灵拉宽铺底
    if (style == RainStyle.NORMAL || style == RainStyle.STORM) {
        val mistH = size.height * 0.14f
        drawImage(
            image = particles.mist,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(256, 64),
            dstOffset = IntOffset(0, (size.height - mistH).toInt()),
            dstSize = IntSize(size.width.toInt(), mistH.toInt().coerceAtLeast(1)),
            alpha = (0.34f * gain).coerceIn(0f, 1f),
            filterQuality = FilterQuality.Medium,
        )
        // 溅花：底部一条带上周期性亮起的短斜杠（加法，像水花反光）
        repeat(7) { i ->
            val a = assets.at(97 + i)
            val cycle = 1.6f + a.speed
            val t = ((time + a.phase * cycle) % cycle).toFloat()
            val blink = if (t < 0.3f) sin(t / 0.3f * PI).toFloat() else 0f
            if (blink <= 0.05f) return@repeat
            val xDp = a.x * widthDp
            val yDp = heightDp * (0.90f + a.y * 0.06f)
            drawLine(Color(0xFFEAF2FA).copy(alpha = 0.34f * gain * blink),
                Offset((xDp - 2.5f) * density, yDp * density),
                Offset((xDp + 2.5f) * density, (yDp - 4f) * density), density, blendMode = BlendMode.Plus)
        }
    }
}

/** 雪：柔边雪晶精灵 + 双频湍流摆动 + 三层景深（近大而虚）。天空已压暗给白雪让对比度。 */
private fun DrawScope.drawSnow(
    spec: AmbienceSpec,
    level: AmbienceLevel,
    time: Double,
    gain: Float,
    density: Float,
    c: SceneColors,
    assets: SceneAssets,
    bundled: BundledSprites,
    particles: ParticleSpriteSet,
    share: Float,
) {
    val total = (sceneParticleCount(AmbienceKind.SNOW, level, spec.intensity) * share).toInt()
    val heightDp = size.height / density
    val wrapWidth = size.width / density + 60f
    data class SnowLayer(val share: Float, val speed: Float, val radius: Float, val alpha: Float, val sway: Float)
    val layers = listOf(
        SnowLayer(0.45f, 15f, 2.4f, 0.34f, 9f),
        SnowLayer(0.33f, 24f, 4.6f, 0.48f, 15f),
        SnowLayer(0.22f, 35f, 7.5f, 0.58f, 24f),
    )
    var cursor = 0
    layers.forEachIndexed { li, layer ->
        val count = (total * layer.share).toInt()
        for (i in 0 until count) {
            val a = assets.at(cursor * 3 + i + li * 53 + 17)
            val speed = layer.speed * (0.75f + a.speed * 0.5f)
            val cycleDp = heightDp + 50f
            val yDp = ((time * speed + a.phase * cycleDp) % cycleDp).toFloat()
            val sway = sin(time * (0.35 + a.speed * 0.35) + a.phase * 6.283).toFloat() * layer.sway +
                sin(time * (0.9 + a.speed * 0.6) + a.phase * 12.56).toFloat() * layer.sway * 0.35f
            val xDp = (((a.x * wrapWidth + sway) % wrapWidth) + wrapWidth) % wrapWidth - 30f
            val alpha = layer.alpha * gain * (0.6f + a.speed * 0.6f) * sceneMaskY(yDp, heightDp)
            if (alpha < 0.008f) continue
            val center = Offset(xDp * density, yDp * density)
            val radiusPx = layer.radius * density * (0.8f + a.size * 0.6f)
            val diaPx = (radiusPx * 2f).toInt().coerceAtLeast(2)
            if (li > 0 && diaPx > 10) {
                // 中近景：真雪花贴图，带自转
                val deg = ((time * (9.0 + a.speed * 18.0) + a.phase * 360.0) % 360.0).toFloat()
                rotate(degrees = deg, pivot = center) {
                    drawImage(
                        image = bundled.snow,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(bundled.snow.width, bundled.snow.height),
                        dstOffset = IntOffset((center.x - radiusPx).toInt(), (center.y - radiusPx).toInt()),
                        dstSize = IntSize(diaPx, diaPx),
                        alpha = alpha.coerceIn(0f, 1f),
                        filterQuality = FilterQuality.Medium,
                    )
                }
            } else {
                // 远景：柔边光斑（雪花贴图缩太小会糊成一团）
                drawImage(
                    image = particles.flake,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(64, 64),
                    dstOffset = IntOffset((center.x - radiusPx).toInt(), (center.y - radiusPx).toInt()),
                    dstSize = IntSize(diaPx, diaPx),
                    alpha = alpha.coerceIn(0f, 1f),
                    filterQuality = FilterQuality.Medium,
                )
            }
        }
        cursor += count
    }
}

private fun DrawScope.drawHail(level: AmbienceLevel, time: Double, gain: Float, density: Float, c: SceneColors, assets: SceneAssets) {
    val count = sceneParticleCount(AmbienceKind.HAIL, level)
    val heightDp = size.height / density
    val wrapWidth = size.width / density + 60f
    for (i in 0 until count) {
        val a = assets.at(i + 41)
        val speed = 240f + a.speed * 130f
        val cycleDp = heightDp + 70f
        val yDp = ((time * speed + a.phase * cycleDp) % cycleDp).toFloat()
        val xDp = (((a.x * wrapWidth + sin(time * 3 + a.phase * 6.283).toFloat() * 4f) % wrapWidth) + wrapWidth) % wrapWidth - 30f
        val alpha = 0.32f * gain * sceneMaskY(yDp, heightDp)
        if (alpha < 0.005f) continue
        // 雹 = 亮点 + 短拖尾（速度感）
        val center = Offset(xDp * density, yDp * density)
        drawLine(c.snow.copy(alpha = alpha * 0.5f), center - Offset(0f, 7f * density), center, density * 0.8f)
        drawCircle(c.snow.copy(alpha = alpha), (1.3f + a.size * 1.3f) * density, center)
    }
}

/** 冻雨：雨丝之外，固定点位上周期性亮起的冰晶十字。 */
private fun DrawScope.drawFreezingGlints(time: Double, gain: Float, density: Float, c: SceneColors, assets: SceneAssets) {
    repeat(16) { i ->
        val a = assets.at(i + 61)
        val cycle = 2.2f + a.speed * 1.6f
        val t = ((time + a.phase * cycle) % cycle).toFloat()
        val blink = if (t < 0.4f) sin(t / 0.4f * PI).toFloat() else 0f
        if (blink <= 0.02f) return@repeat
        val center = Offset(a.x * size.width, size.height * (0.18f + a.y * 0.62f))
        val r = 2.4f * density
        val alpha = 0.16f * gain * blink
        drawLine(c.snow.copy(alpha = alpha), center - Offset(r, 0f), center + Offset(r, 0f), density * 0.8f)
        drawLine(c.snow.copy(alpha = alpha), center - Offset(0f, r), center + Offset(0f, r), density * 0.8f)
    }
}

private fun DrawScope.drawSandStreaks(
    spec: AmbienceSpec,
    level: AmbienceLevel,
    time: Double,
    gain: Float,
    density: Float,
    c: SceneColors,
    assets: SceneAssets,
) {
    val count = sceneParticleCount(AmbienceKind.SAND, level, spec.intensity)
    val boost = 0.75f + spec.windSpeedKmh.coerceAtMost(90f) / 90f
    val heightDp = size.height / density
    val spanDp = size.width / density + 60f
    for (i in 0 until count) {
        val a = assets.at(i + 83)
        val speed = (150f + a.speed * 130f) * boost
        val xDp = spanDp - ((time * speed + a.phase * spanDp) % spanDp).toFloat() - 30f
        val yDp = a.y * heightDp
        val lenDp = 7f + a.size * 9f
        val alpha = 0.20f * gain * (0.5f + a.speed * 0.5f) * sceneMaskY(yDp, heightDp)
        if (alpha < 0.005f) continue
        drawLine(c.sand.copy(alpha = alpha), Offset(xDp * density, yDp * density),
            Offset((xDp + lenDp) * density, yDp * density + a.size * 2f), density)
    }
}

private fun DrawScope.drawWindArcs(
    spec: AmbienceSpec,
    level: AmbienceLevel,
    time: Double,
    gain: Float,
    density: Float,
    c: SceneColors,
    assets: SceneAssets,
) {
    val count = sceneParticleCount(AmbienceKind.WIND, level, spec.intensity) * 2
    val boost = 0.6f + spec.windSpeedKmh.coerceAtMost(90f) / 90f
    val heightDp = size.height / density
    for (i in 0 until count) {
        val a = assets.at(i + 101)
        val spanDp = size.width / density + 90f
        val speed = (110f + a.speed * 150f) * boost
        val xDp = spanDp - ((time * speed + a.phase * spanDp) % spanDp).toFloat() - 45f
        val yDp = (0.08f + a.y * 0.7f) * heightDp
        val lenDp = (46f + a.size * 60f) * boost
        val lift = sin((xDp / spanDp) * PI).toFloat() * 10f
        val alpha = 0.15f * gain * (0.5f + a.speed * 0.5f) * sceneMaskY(yDp, heightDp)
        if (alpha < 0.005f) continue
        val start = Offset(xDp * density, (yDp + lift) * density)
        val mid = Offset((xDp + lenDp * 0.5f) * density, (yDp - lift * 0.6f) * density)
        val end = Offset((xDp + lenDp) * density, (yDp + lift * 0.3f) * density)
        val path = Path().apply {
            moveTo(start.x, start.y)
            quadraticBezierTo(mid.x, mid.y, end.x, end.y)
        }
        drawPath(path, c.wind.copy(alpha = alpha), style = Stroke(width = density * (0.8f + a.speed * 0.7f)))
    }
}

private fun DrawScope.drawStarfield(
    level: AmbienceLevel,
    time: Double,
    gain: Float,
    density: Float,
    c: SceneColors,
    assets: SceneAssets,
    parallaxPx: Float,
) {
    val count = sceneParticleCount(AmbienceKind.STARFIELD, level)
    val widthDp = size.width / density
    val heightDp = size.height / density
    for (i in 0 until count) {
        val a = assets.at(i)
        val xDp = a.x * widthDp
        val yDp = a.y * heightDp * 0.85f - parallaxPx / density * 0.06f
        val pulse = 0.35f + 0.65f * (0.5f + 0.5f * sin(time * (0.25 + a.speed * 0.5) + a.phase * 6.283)).toFloat()
        val alpha = (if (a.speed > 0.86f) 0.36f else 0.17f) * gain * pulse
        drawCircle(c.star.copy(alpha = alpha), (if (a.speed > 0.86f) 1.7f else 1.0f) * density, Offset(xDp * density, yDp * density))
        if (a.speed > 0.94f) {
            // 最亮的几颗带十字星芒（加法，真正的发光感）
            val glint = alpha * 0.7f
            val r = 3.4f * density
            drawLine(c.star.copy(alpha = glint), Offset(xDp * density - r, yDp * density), Offset(xDp * density + r, yDp * density), density * 0.7f, blendMode = BlendMode.Plus)
            drawLine(c.star.copy(alpha = glint), Offset(xDp * density, yDp * density - r), Offset(xDp * density, yDp * density + r), density * 0.7f, blendMode = BlendMode.Plus)
        }
    }
    val t = time % 7.0
    if (t < 0.6) {
        val progress = (t / 0.6).toFloat()
        val fade = sin(progress * PI).toFloat()
        val start = Offset(widthDp * 0.16f, heightDp * 0.06f)
        val head = start + Offset(widthDp * 0.34f, heightDp * 0.16f) * progress
        val tail = head - Offset(widthDp * 0.09f, heightDp * 0.042f)
        drawLine(c.star.copy(alpha = 0.32f * gain * fade), tail * density, head * density, density * 1.2f)
    }
}

/** 雾：手绘云贴图压扁成奶白横带，随能见度变浓。 */
private fun DrawScope.drawFogBands(
    spec: AmbienceSpec,
    time: Double,
    gain: Float,
    density: Float,
    c: SceneColors,
    particles: ParticleSpriteSet,
    parallaxPx: Float,
) {
    // 插画已带雾景；这里只铺两条极淡的漂移雾纱让雾"活"起来
    val visibility = (spec.visibilityKm ?: 8f).coerceIn(0.5f, 12f)
    val densityBoost = 0.7f + (12f - visibility) / 12f * 0.9f
    repeat(2) { i ->
        val seed = 503 + i * 131
        val widthPx = size.width * 1.3f
        val spanPx = size.width + widthPx
        val xf = (((hash01(seed * 0.713f) + time * (2.5f + i) * density / spanPx) % 1.0) + 1.0) % 1.0
        val cx = (xf * spanPx - widthPx / 2f).toFloat()
        val cy = size.height * (0.30f + i * 0.22f) - parallaxPx * 0.03f
        val bandH = size.height * 0.16f
        drawImage(
            image = particles.mist,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(256, 64),
            dstOffset = IntOffset((cx - widthPx / 2f).toInt(), (cy - bandH / 2f).toInt()),
            dstSize = IntSize(widthPx.toInt().coerceAtLeast(1), bandH.toInt().coerceAtLeast(1)),
            alpha = ((0.10f * densityBoost).coerceAtMost(0.2f) * gain).coerceIn(0f, 1f),
            filterQuality = FilterQuality.Medium,
        )
    }
}

private fun DrawScope.drawThermalWash(thermal: ThermalModifier, gain: Float, c: SceneColors) {
    when (thermal) {
        ThermalModifier.HOT -> {
            val depth = size.height * 0.35f
            drawRect(Brush.verticalGradient(listOf(c.sun.copy(alpha = 0.08f * gain), Color.Transparent), startY = 0f, endY = depth),
                topLeft = Offset(0f, 0f), size = Size(size.width, depth))
        }
        ThermalModifier.COLD -> {
            val depth = size.height * 0.35f
            drawRect(Brush.verticalGradient(listOf(c.moon.copy(alpha = 0.07f * gain), Color.Transparent), startY = 0f, endY = depth),
                topLeft = Offset(0f, 0f), size = Size(size.width, depth))
        }
        ThermalModifier.NONE -> Unit
    }
}

/** 闪电 = 全屏闪白两次 + 折线闪道 + 云体被照亮。 */
private fun DrawScope.drawStormLightning(
    time: Double,
    gain: Float,
    density: Float,
    c: SceneColors,
) {
    val phase = floor(time / 8.8) % 4.0 / 4.0
    val flash = stormFlashAlpha(time, phase) * gain
    if (flash > 0f) drawRect(c.bolt.copy(alpha = flash), blendMode = BlendMode.Plus)
    if (stormBoltVisible(time, phase)) {
        val cycleIndex = floor(time / (5.4 + phase * 3.4)).toInt()
        val w = size.width
        val h = size.height
        var x = w * (0.62f + hash01(cycleIndex * 3.71f) * 0.16f)
        var y = h * 0.05f
        val path = Path().apply { moveTo(x, y) }
        repeat(5) { seg ->
            x += w * (hash01(cycleIndex * 7.13f + seg * 3.3f) * 0.10f - 0.05f)
            y += h * (0.07f + hash01(cycleIndex * 5.41f + seg * 9.7f) * 0.05f)
            path.lineTo(x, y)
        }
        drawPath(path, c.bolt.copy(alpha = 0.60f * gain), style = Stroke(width = 2.2f * density))
    }
}

private fun SceneAssets.at(index: Int): Element {
    val i = abs(index) % count
    return Element(x[i], y[i], phase[i], speed[i], size[i])
}

private class Element(val x: Float, val y: Float, val phase: Float, val speed: Float, val size: Float)

private fun hash01(value: Float): Float {
    val s = sin(value.toDouble()).toFloat() * 43758.5453f
    return s - floor(s)
}
