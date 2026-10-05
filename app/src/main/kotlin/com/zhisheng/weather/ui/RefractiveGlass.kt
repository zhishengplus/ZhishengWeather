package com.zhisheng.weather.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.asComposeRenderEffect

/** Refracts the actual content layer. Symbols are drawn afterwards and remain optically sharp. */
@RequiresApi(33)
internal class RefractiveGlass {
    private val shader = RuntimeShader(GLASS_OPTICS)
    fun effect(width: Float, height: Float, center: Float, cell: Float, pressure: Float,
               stretch: Float, lift: Float, light: Boolean, density: Float = 1f, apron: Float = 0f): androidx.compose.ui.graphics.RenderEffect {
        shader.setFloatUniform("resolution", width.coerceAtLeast(1f), height.coerceAtLeast(1f))
        shader.setFloatUniform("lens", center, cell, pressure, stretch)
        shader.setFloatUniform("lift", lift)
        shader.setFloatUniform("lightMode", if (light) 1f else 0f)
        shader.setFloatUniform("pixel", density)
        shader.setFloatUniform("apron", apron)
        return RenderEffect.createChainEffect(
            RenderEffect.createRuntimeShaderEffect(shader, "scene"),
            RenderEffect.createBlurEffect(6f * density, 6f * density, android.graphics.Shader.TileMode.CLAMP)
        ).asComposeRenderEffect()
    }
}

private const val GLASS_OPTICS = """
uniform shader scene;
uniform float2 resolution;
uniform float4 lens;
uniform float lift;
uniform float lightMode;
uniform float pixel;
uniform float apron;

float roundedDistance(float2 p, float2 halfSize, float radius) {
    float2 q = abs(p) - halfSize + radius;
    return length(max(q,0.0)) + min(max(q.x,q.y),0.0) - radius;
}
float2 surfaceNormal(float2 p, float2 halfSize, float radius) {
    float e=.75;
    float2 g=float2(roundedDistance(p+float2(e,0),halfSize,radius)-roundedDistance(p-float2(e,0),halfSize,radius),
                    roundedDistance(p+float2(0,e),halfSize,radius)-roundedDistance(p-float2(0,e),halfSize,radius));
    return g/max(length(g),.001);
}
half4 main(float2 coordinates) {
    float2 xy=coordinates-apron;
    float h=resolution.y;
    float2 halfSize=resolution*.5;
    float2 p=xy-halfSize;
    float d=roundedDistance(p,halfSize,h*.5);
    float2 n=surfaceNormal(p,halfSize,h*.5);
    // A curved bevel bends background lines, while the broad center remains transparent.
    float bevel=1.0-smoothstep(0.0,h*.22,max(-d,0.0));
    float2 bend=n*bevel*bevel*h*.105;
    float2 lp=xy-float2(lens.x,h*.5+lift);
    float2 lh=float2(lens.y*.5*(1.0+lens.w)-h*.065,h*(.415-lens.w*.1));
    float ld=roundedDistance(lp,lh,min(lh.x,lh.y));
    float2 ln=surfaceNormal(lp,lh,min(lh.x,lh.y));
    float inside=1.0-smoothstep(-h*.045,h*.02,ld);
    float lensBevel=(1.0-smoothstep(0.0,h*.18,max(-ld,0.0)))*inside;
    bend+=ln*lensBevel*h*(.032+lens.z*.07);
    bend+=lp*inside*(.035+lens.z*.075);
    float2 samplePoint=coordinates-bend;
    half4 base=scene.eval(samplePoint);
    // Keep a clear refracting bevel and adapt scattering/tint in the reading center.
    float body=1.0-bevel;
    float3 color=float3(base.rgb)/max(float(base.a),.001);
    float3 tint=mix(float3(.075,.10,.13),float3(.95,.97,.98),lightMode);
    float luminance=dot(color,float3(.2126,.7152,.0722));
    float protection=mix(.08,mix(.28,.22,lightMode),body);
    protection+=body*(1.0-lightMode)*smoothstep(.25,.85,luminance)*.16;
    color=mix(color,tint,protection);
    // Highlights follow the same geometry and the finger-driven lens, never a painted white frame.
    float2 illumination=normalize(float2(-.55+lens.z*.55,-1.0));
    float rim=exp(-pow((d+.55*pixel)/(.60*pixel),2.0));
    float spec=pow(max(dot(n,illumination),0.0),3.0)*rim;
    float innerRim=exp(-pow((ld+.55*pixel)/(.65*pixel),2.0))*inside;
    float innerSpec=pow(max(dot(ln,illumination),0.0),4.0)*innerRim;
    float opposite=pow(max(dot(n,-illumination),0.0),5.0)*rim;
    color=mix(color,float3(1.0),rim*.08+spec*.52+opposite*.30);
    color+=float3(.78,.90,1.0)*innerSpec*(.06+lens.z*.16);
    color+=float3(.72,.87,1.0)*lens.z*.025*inside;
    return half4(half3(clamp(color,0.0,1.0)*float(base.a)),base.a);
}
"""
