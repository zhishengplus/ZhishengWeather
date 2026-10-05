package com.zhisheng.weather.ui.theme

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.asComposeRenderEffect

/** Our optical approximation, not Apple's private shader. Input includes a sampling apron.
 * Only the backdrop is filtered; the caller draws foreground text afterwards.
 */
@RequiresApi(33)
internal class HomeGlassOptics {
    private val shader = RuntimeShader(OPTICS)

    fun effect(width: Float, height: Float, edge: Float, radius: Float, light: Boolean,
        density: Float, apron: Float, blur: Float): androidx.compose.ui.graphics.RenderEffect {
        shader.setFloatUniform("resolution", width.coerceAtLeast(1f), height.coerceAtLeast(1f))
        shader.setFloatUniform("edge", edge.coerceAtLeast(1f))
        shader.setFloatUniform("cornerRadius", radius.coerceIn(0f, minOf(width, height) * .5f))
        shader.setFloatUniform("lightMode", if (light) 1f else 0f)
        shader.setFloatUniform("pixel", density)
        shader.setFloatUniform("apron", apron)
        return RenderEffect.createChainEffect(
            RenderEffect.createRuntimeShaderEffect(shader, "scene"),
            RenderEffect.createBlurEffect(blur.coerceAtLeast(.1f), blur.coerceAtLeast(.1f), Shader.TileMode.CLAMP)
        ).asComposeRenderEffect()
    }
}

private const val OPTICS = """
uniform shader scene;
uniform float2 resolution;
uniform float edge;
uniform float cornerRadius;
uniform float lightMode;
uniform float pixel;
uniform float apron;

float distanceToEdge(float2 p) {
    float2 q = abs(p - resolution * .5) - (resolution * .5 - cornerRadius);
    return length(max(q, float2(0))) + min(max(q.x, q.y), 0.0) - cornerRadius;
}

half4 main(float2 xy) {
    float2 p = xy - apron;
    float d = distanceToEdge(p);
    float depth = max(-d, 0.0);
    float2 g = float2(distanceToEdge(p + float2(pixel, 0)) - distanceToEdge(p - float2(pixel, 0)),
                      distanceToEdge(p + float2(0, pixel)) - distanceToEdge(p - float2(0, pixel)));
    float2 n = g / max(length(g), .001);
    // A smooth convex lip, with a flat reading centre.
    float lip = 1.0 - smoothstep(0.0, edge, depth);
    float bend = edge * .62 * lip * lip;
    half4 sampled = scene.eval(xy - n * bend);
    float3 color = float3(sampled.rgb) / max(float(sampled.a), .001);
    float luminance = dot(color, float3(.2126, .7152, .0722));
    color = mix(float3(luminance), color, 1.08);
    // Keep the weather colour; compress only enough to protect foreground contrast.
    float protection = mix(.13 + .28 * smoothstep(.25, .8, luminance), .17, lightMode);
    float3 tint = mix(float3(.065, .083, .105), float3(.97, .98, .985), lightMode);
    color = mix(color, tint, protection * (1.0 - lip * .55));
    float facing = dot(n, normalize(float2(-.55, -.83)));
    float key = pow(max(facing, 0.0), 5.0);
    float bounce = pow(max(-facing, 0.0), 6.0);
    // One sub-dp silhouette with a soft optical shoulder, not two painted outlines.
    float rim = exp(-pow((depth - .55 * pixel) / (.60 * pixel), 2.0));
    float shoulder = exp(-pow((depth - 2.3 * pixel) / (1.8 * pixel), 2.0));
    float specular = rim * (.10 + .62 * key + .40 * bounce);
    color = mix(color, float3(1.0), specular * mix(.54, 1.0, lightMode));
    color += float3(.88, .94, 1.0) * shoulder * key * .035;
    color *= 1.0 - shoulder * (1.0 - abs(facing)) * .020;
    // Replace the background. Low-alpha output would erase the refraction.
    float alpha = float(sampled.a);
    return half4(half3(clamp(color, 0.0, 1.0) * alpha), half(alpha));
}
"""
