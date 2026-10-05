package com.zhisheng.weather.ui.home

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.asComposeRenderEffect

/** A small optical bevel on the two live exits of the hourly glass viewport. */
@RequiresApi(33)
internal class HourlyEdgeOptics {
    private val shader = RuntimeShader(EDGE_OPTICS)

    fun effect(
        width: Float,
        height: Float,
        edgeWidth: Float,
        hasLeft: Boolean,
        hasRight: Boolean,
    ): androidx.compose.ui.graphics.RenderEffect {
        shader.setFloatUniform("resolution", width.coerceAtLeast(1f), height.coerceAtLeast(1f))
        shader.setFloatUniform("edgeWidth", edgeWidth.coerceAtLeast(1f))
        shader.setFloatUniform("exits", if (hasLeft) 1f else 0f, if (hasRight) 1f else 0f)
        return RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    }
}

private const val EDGE_OPTICS = """
uniform shader content;
uniform float2 resolution;
uniform float2 exits;
uniform float edgeWidth;

half4 main(float2 p) {
    float fromLeft = clamp(p.x / edgeWidth, 0.0, 1.0);
    float fromRight = clamp((resolution.x - p.x) / edgeWidth, 0.0, 1.0);
    float leftCurve = (1.0 - smoothstep(0.0, 1.0, fromLeft)) * exits.x;
    float rightCurve = (1.0 - smoothstep(0.0, 1.0, fromRight)) * exits.y;
    float verticalCurve = 0.78 + 0.22 * cos((p.y / resolution.y - 0.5) * 3.14159265);

    // Re-sample the real forecast layer through the rounded glass lip. The centre is untouched.
    float shift = (leftCurve * leftCurve - rightCurve * rightCurve) * edgeWidth * 0.12 * verticalCurve;
    float sampleX = clamp(p.x + shift, 0.5, resolution.x - 0.5);
    half4 base = content.eval(float2(sampleX, p.y));

    return base;
}
"""
