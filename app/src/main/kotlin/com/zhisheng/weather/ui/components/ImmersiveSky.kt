package com.zhisheng.weather.ui.components

import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import com.zhisheng.weather.model.ThermalModifier

/** A bounded volume of cloud, evaluated directly by the hardware canvas on Android 13+. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class ImmersiveSky {
    private val shader = RuntimeShader(SKY_SHADER).apply {
        setInputShader("noiseTexture", BitmapShader(CloudNoiseTexture.bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
            setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
        })
    }
    private val paint = Paint().apply { shader = this@ImmersiveSky.shader }

    private data class Frame(val state: NaturalLightState, val palette: NaturalLightPalette,
        val phase: Double, val gain: Float, val light: Boolean, val size: IntSize,
        val previous: NaturalWeather, val next: NaturalWeather, val blend: Float)
    private var recorded: Frame? = null

    /** Only the soft cloud volume is rasterized at half resolution. Text and rain stay native.
     * Scrolling changes layer position/opacity without reevaluating the cloud volume. */
    fun drawCached(scope: DrawScope, layer: GraphicsLayer, state: NaturalLightState, palette: NaturalLightPalette,
                   phase: Double, gain: Float, opacity: Float, light: Boolean, height: Float,
                   previousWeather: NaturalWeather, nextWeather: NaturalWeather, blend: Float) {
        val target = IntSize(ceil(scope.size.width * .5f).toInt().coerceAtLeast(1),
            ceil(height * .5f).toInt().coerceAtLeast(1))
        val frame = Frame(state, palette, phase, gain, light, target, previousWeather, nextWeather, blend)
        layer.compositingStrategy = CompositingStrategy.Offscreen
        layer.alpha = opacity.coerceIn(0f, 1f)
        with(scope) {
            if (recorded != frame || layer.size != target) {
                layer.record(size = target) {
                    draw(this, state, palette, phase, gain, light, size.height, previousWeather, nextWeather, blend)
                }
                recorded = frame
            }
            scale(size.width / target.width, height / target.height, pivot = Offset.Zero) { drawLayer(layer) }
        }
    }

    private fun draw(scope: DrawScope, state: NaturalLightState, palette: NaturalLightPalette,
             phase: Double, gain: Float, light: Boolean, height: Float,
             previousWeather: NaturalWeather = state.weather, nextWeather: NaturalWeather = state.weather, blend: Float = 1f) {
        fun weatherWeight(predicate: (NaturalWeather) -> Boolean): Float =
            (if (predicate(previousWeather)) 1f - blend else 0f) + (if (predicate(nextWeather)) blend else 0f)
        shader.setFloatUniform("resolution", scope.size.width, height)
        // A closed path in density space has matching position and velocity at the clock seam.
        val angle = phase * PI * 2
        val travel = .22 + state.wind * .24
        shader.setFloatUniform("drift", (cos(angle) * travel).toFloat(), (sin(angle) * travel).toFloat())
        shader.setFloatUniform("cover", state.cloud)
        shader.setFloatUniform("day", state.daylight)
        shader.setFloatUniform("dusk", state.twilight)
        shader.setFloatUniform("solarMode", if (state.solar != null) 1f else 0f)
        shader.setFloatUniform("sunSource", state.solar?.lightX ?: .80f, state.solar?.lightY ?: (.38f + .07f * state.twilight))
        shader.setFloatUniform("skyCloudShade", palette.cloudShadow.red, palette.cloudShadow.green, palette.cloudShadow.blue)
        shader.setFloatUniform("skyCloudLit", palette.cloudLit.red, palette.cloudLit.green, palette.cloudLit.blue)
        shader.setFloatUniform("strength", gain)
        shader.setFloatUniform("lightTheme", if (light) 1f else 0f)
        val stormWeight = weatherWeight { it == NaturalWeather.STORM }
        shader.setFloatUniform("storm", stormWeight)
        shader.setFloatUniform("lightning", stormWeight * naturalLightning(phase * 72.0))
        shader.setFloatUniform("mist", weatherWeight { it == NaturalWeather.FOG || it == NaturalWeather.HAZE || it == NaturalWeather.DUST })
        shader.setFloatUniform("sunVisible", weatherWeight { it != NaturalWeather.NEUTRAL })
        shader.setFloatUniform("heat", if (state.thermal == ThermalModifier.HOT) 1f else 0f)
        shader.setFloatUniform("heatPhase", (phase * 2 * PI).toFloat())
        fun structure(weather: NaturalWeather): FloatArray = when (weather) {
            NaturalWeather.OVERCAST -> floatArrayOf(1.45f, 2.3f, .23f, .95f)
            NaturalWeather.RAIN, NaturalWeather.MIXED, NaturalWeather.ICE -> floatArrayOf(2.8f, 5.5f, .17f, 1f)
            NaturalWeather.STORM -> floatArrayOf(1.8f, 3.8f, .11f, 1f)
            NaturalWeather.SNOW -> floatArrayOf(1.35f, 2.7f, .29f, .72f)
            NaturalWeather.FOG -> floatArrayOf(.8f, 1.2f, .38f, .08f)
            NaturalWeather.HAZE -> floatArrayOf(.7f, 1.1f, .4f, .04f)
            NaturalWeather.DUST -> floatArrayOf(3.6f, 1.0f, .35f, .12f)
            NaturalWeather.WIND -> floatArrayOf(3.4f, 6.2f, .16f, .8f)
            else -> floatArrayOf(2.25f, 4.1f, .15f, 1f)
        }
        val previous = structure(previousWeather)
        val next = structure(nextWeather)
        shader.setFloatUniform("structure", FloatArray(4) { previous[it] + (next[it]-previous[it])*blend })
        shader.setFloatUniform("aerosol", weatherWeight { it == NaturalWeather.FOG },
            weatherWeight { it == NaturalWeather.HAZE }, weatherWeight { it == NaturalWeather.DUST })
        shader.setFloatUniform("cloudColor", palette.cloud.red, palette.cloud.green, palette.cloud.blue)
        scope.drawContext.canvas.nativeCanvas.drawRect(0f, 0f, scope.size.width, height, paint)
    }
}

/** Adjacent z slices in R/G; bilinear xy + interpolation in z gives smooth 3D noise. */
private object CloudNoiseTexture {
    val bitmap: Bitmap by lazy {
        val side = 256
        val values = IntArray(side * side) { i ->
            var n = i * 374761393 + 668265263
            n = (n xor (n ushr 13)) * 1274126177
            (n xor (n ushr 16)) and 255
        }
        val pixels = IntArray(values.size) { i ->
            val x = i % side
            val y = i / side
            val next = values[((y + 17) and 255) * side + ((x + 37) and 255)]
            (255 shl 24) or (values[i] shl 16) or (next shl 8)
        }
        Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888)
    }
}

// Front-to-back extinction through eight cloud slices. The density field is independent of
// screen resolution; fBm supplies structure at three scales and the displaced sample estimates
// light penetration. No per-frame textures, bitmaps, allocations or independent animation loop.
private const val SKY_SHADER = """
uniform float2 resolution;
uniform float2 drift;
uniform float cover;
uniform float day;
uniform float dusk;
uniform float solarMode;
uniform float2 sunSource;
uniform float3 skyCloudShade;
uniform float3 skyCloudLit;
uniform float strength;
uniform float lightTheme;
uniform float storm;
uniform float lightning;
uniform float mist;
uniform float sunVisible;
uniform float heat;
uniform float heatPhase;
uniform float3 cloudColor;
uniform float4 structure;
uniform float3 aerosol;
uniform shader noiseTexture;

float noise(float3 p) {
    float3 i = floor(p);
    float3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float2 lookup = i.xy + float2(37,17)*i.z + f.xy + .5;
    float2 rg = noiseTexture.eval(lookup).rg;
    return mix(rg.x,rg.y,f.z);
}
float field(float3 p) {
    return noise(p)*.48 + noise(p*2.03+11.3)*.26 + noise(p*4.11+27.1)*.14
         + noise(p*8.23+31.7)*.08 + noise(p*16.51+7.9)*.04;
}
float density(float3 p) {
    float n = field(p);
    float threshold = mix(.78,.39,cover);
    return smoothstep(threshold,threshold+structure.z,n);
}
half4 main(float2 coord) {
    float2 uv = coord / resolution;
    // Subpixel refraction in the lower air mass, leaving text and icons untouched.
    uv.x += heat * .0018 * sin(uv.y*150.0 + heatPhase*6.0) * smoothstep(.25,.65,uv.y);
    float fade = 1.0 - smoothstep(.35,.92,uv.y);
    if (fade < .001) return half4(0);
    float aspect = resolution.x / resolution.y;
    float2 screen = float2((uv.x-.5)*aspect, uv.y-.25);
    float3 ray = normalize(float3(screen*1.8,1.35));
    float3 origin = float3(2.7+drift.x,-1.4+drift.y,3.2);
    float3 sum = float3(0);
    float trans = 1.0;
    float3 shade = mix(float3(.15,.19,.27),float3(.43,.55,.65),day);
    shade = mix(shade, float3(.51,.63,.73),lightTheme*.75);
    shade *= 1.0-storm*.12;
    float3 lit = mix(float3(.48,.55,.69),float3(.99,.98,.95),day);
    lit = mix(lit,float3(.97,.98,1),lightTheme*.65);
    lit = mix(lit,float3(1,.75,.57),dusk*.40);
    shade = mix(shade, skyCloudShade, solarMode);
    lit = mix(lit, skyCloudLit, solarMode);
    if (cover > .015 && structure.w > .025) {
    for (int i=0; i<6; i++) {
        float t = 1.0+float(i)*.30;
        // Broad horizontal cloud banks, with finer vertical structure and clear air between.
        float3 pos = origin + ray*t*float3(structure.x,structure.y,2.8);
        float d = density(pos);
        float3 lp = pos+float3(mix(.24,(sunSource.x-.5)*.72,solarMode),-.32,-.18);
        float lightDensity = noise(lp)*.65+noise(lp*2.03+11.3)*.35;
        float threshold = mix(.78,.39,cover);
        float towardLight = smoothstep(threshold,threshold+.15,lightDensity);
        float illumination = clamp(.32+(d-towardLight)*1.35,.12,.86);
        float alpha = d * mix(.36,.46,lightTheme) * structure.w;
        sum += trans * alpha * mix(shade,lit,illumination);
        trans *= 1.0-alpha;
    }
    }
    float alpha = 1.0-trans;
    float flash = lightning * exp(-dot(uv-float2(.76,.25),uv-float2(.76,.25))*18.0);
    sum += float3(.78,.86,1.0)*flash*alpha;
    float2 source = mix(float2(.80,mix(.38,.45,dusk)),sunSource,solarMode);
    float distance = length((uv-source)*float2(aspect,1));
    float halo = exp(-distance*distance*30.0) * (1.0-cover*.85)*sunVisible;
    float3 lightColor = mix(float3(.92,.95,1),float3(1,.96,.85),day);
    lightColor = mix(lightColor,float3(1,.69,.43),dusk*.65);
    lightColor = mix(lightColor,skyCloudLit,solarMode*(1.0-lightTheme));
    // The foreground weather artwork owns the sun/moon; the sky supplies only its soft light.
    float lightAlpha = halo*.22;
    sum += trans * lightColor * lightAlpha;
    alpha += trans*lightAlpha;
    // A translucent veil gives aerosols a continuous air mass rather than discrete cloud blobs.
    float fogBand = .6 + .4*noise(float3(uv*float2(1.4,5.0)+drift*.25,1.0));
    float veil = aerosol.x*.40*fogBand + aerosol.y*.30 + aerosol.z*.32*(.55+uv.y*.45);
    sum = sum*(1.0-veil)+cloudColor*veil;
    alpha = alpha*(1.0-veil)+veil;
    float2 artDelta = (uv-float2(.81,.45))*float2(aspect,1.0);
    float readingSpace = 1.0-.30*exp(-dot(artDelta,artDelta)*42.0);
    float quietReading = mix(1.0, .40+.60*smoothstep(.25,.65,uv.x),solarMode);
    float opacity = fade * min(.92,strength*1.4) * readingSpace * quietReading;
    return half4(sum*opacity,alpha*opacity);
}
"""
