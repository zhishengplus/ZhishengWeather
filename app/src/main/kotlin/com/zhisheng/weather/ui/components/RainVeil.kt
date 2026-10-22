package com.zhisheng.weather.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil

/** Screen-rain math adapted from the supplied rain_screen vertex/fragment shaders.
 * Original sky, artwork and Canvas near-rain stay intact. No MGL, compute or native dependency. */
@RequiresApi(33)
internal class RainVeil {
    private val shader = RuntimeShader(RAIN_VEIL).apply {
        setInputShader("noiseTexture", BitmapShader(RainVeilNoise.bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
            setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
        })
    }
    private val paint = Paint().apply { shader = this@RainVeil.shader }
    private val clock = AtmosphereEffectClock()
    private data class Frame(val size: IntSize, val time: Long, val intensity: Float,
        val weight: Float, val tint: androidx.compose.ui.graphics.Color, val budget: SkyRenderQuality)
    private var recorded: Frame? = null
    private var disabled = false

    fun drawCached(scope: DrawScope, layer: GraphicsLayer, frame: NaturalSkyFrame, phase: Double,
        gain: Float, height: Float, budget: SkyRenderQuality, moving: Boolean): Boolean {
        if (disabled) return false
        val rain = frame.weatherWeights[NaturalWeather.RAIN.ordinal] + frame.weatherWeights[NaturalWeather.STORM.ordinal] +
            frame.weatherWeights[NaturalWeather.MIXED.ordinal] * .5f
        if (rain < .001f || gain < .001f) { clock.seconds(0.0, false); return true }
        val seconds = clock.seconds(phase, moving)
        val target = IntSize(ceil(scope.size.width * budget.resolutionScale).toInt().coerceAtLeast(1),
            ceil(height * budget.resolutionScale).toInt().coerceAtLeast(1))
        val next = Frame(target, atmosphereTimeTick(seconds, budget), rainVeilIntensity(frame.state.intensity),
            rain.coerceIn(0f, 1f), frame.palette.particle, budget)
        return runCatching {
            layer.alpha = gain.coerceIn(0f, 1f)
            layer.blendMode = BlendMode.Plus
            layer.compositingStrategy = CompositingStrategy.Offscreen
            with(scope) {
                if (recorded != next || layer.size != target) {
                    shader.setFloatUniform("resolution", target.width.toFloat(), target.height.toFloat())
                    shader.setFloatUniform("time", (next.time / if (budget == SkyRenderQuality.HIGH) 30.0 else 20.0).toFloat())
                    shader.setFloatUniform("intensity", next.intensity)
                    shader.setFloatUniform("speedY", if (frame.state.intensity > .6f) 1.7f else 1.49f)
                    shader.setFloatUniform("opacity", next.weight * .85f)
                    // The source's negative filter excludes the closest layer (4/1 distant layers).
                    shader.setFloatUniform("layers", if (budget == SkyRenderQuality.HIGH) 4f else 1f)
                    shader.setFloatUniform("tint", next.tint.red, next.tint.green, next.tint.blue)
                    layer.record(size = target) {
                        drawContext.canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
                    }
                    recorded = next
                }
                scale(size.width / target.width, height / target.height, pivot = Offset.Zero) { drawLayer(layer) }
            }
            true
        }.getOrElse {
            disabled = true
            android.util.Log.w("RainVeil", "Distant rain unavailable; retaining original Canvas rain", it)
            false
        }
    }
}

private object RainVeilNoise {
    val bitmap: Bitmap by lazy {
        fun channel(index: Int, salt: Int): Int {
            var n = index * 374761393 + salt * 668265263
            n = (n xor (n ushr 13)) * 1274126177
            return (n xor (n ushr 16)) and 255
        }
        Bitmap.createBitmap(IntArray(256 * 256) { i ->
            (255 shl 24) or (channel(i, 1) shl 16) or (channel(i, 2) shl 8) or channel(i, 3)
        }, 256, 256, Bitmap.Config.ARGB_8888)
    }
}

// Vertex-stage constants from the supplied shader are evaluated here, so this is one AGSL pass.
// Noise is locally generated; no extracted ASTC/MGL texture decoder is needed.
private const val RAIN_VEIL = """
uniform float2 resolution;
uniform float time;
uniform float intensity;
uniform float speedY;
uniform float opacity;
uniform float layers;
uniform float3 tint;
uniform shader noiseTexture;

float dropLayer(float2 original, float scale, float strength, int index) {
    float2 uv = (original * scale - .5) * float2(resolution.x / resolution.y, 1.0);
    uv.x *= 20.0;
    float dx = fract(uv.x);
    float track = floor(uv.x);
    float intensityY = .028 * mix(1.6, 1.0, intensity / 5.0);
    float intensity3 = mix(1.3, 1.0, intensity);
    float threshold = mix(-2.0, -5.0, intensity / 5.0);
    float3 noise = noiseTexture.eval(float2((track + float(index)) * 1.5)).rgb;
    float offset = (noise.x + noise.y - 1.5) * 3.0;
    float speed = offset * .033 + .7;
    float yv = fract(uv.y * intensityY + (time + 100.0) * speedY * speed - .1 * speedY * intensity3 + offset) * 95.0;
    yv = 1.0 / max(yv, .0001);
    yv = smoothstep(0.0, 1.0, yv * yv);
    float gradient = clamp(yv + .5, 0.0, 1.0);
    yv = sin(yv * 3.14) * (speed * 5.0);
    float width = sin(dx * 3.14);
    yv *= width * width;
    return offset > threshold ? smoothstep(0.0, 1.0, yv) * gradient * strength : 0.0;
}

half4 main(float2 xy) {
    // GLES texture coordinates increase upward; AGSL pixels increase downward.
    float2 uv = float2(xy.x / resolution.x, 1.0 - xy.y / resolution.y);
    float alpha = 0.0;
    for (int i = 0; i < 5; i++) {
        if (float(i) >= layers) break;
        float index = float(i + 1);
        float amount = .35 - index * mix(.10, .05, intensity / 5.0);
        float scale = 10.0 + (2.5 + index * 1.5) * 5.0;
        alpha += dropLayer(uv, scale, max(amount, 0.0) * .3, i + 2);
    }
    float y = xy.y / resolution.y;
    float top = smoothstep(.025, .14, y);
    float bottom = 1.0 - smoothstep(.60, .96, y);
    float reading = .20 + .80 * smoothstep(.35, .76, xy.x / resolution.x);
    alpha = clamp(alpha * opacity * top * bottom * reading, 0.0, .45);
    return half4(half3(tint * alpha), half(alpha));
}
"""
