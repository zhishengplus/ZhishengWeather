uniform half2 uResolution; // 屏幕分辨率
uniform float uTime; // 时间
uniform shader uTex; // 输入的纹理

uniform half uStaticDropSize   ;
uniform half uStaticDropAmount ;
uniform half uStaticDropSpeed  ;
uniform half uRunningDropSize  ;
uniform half uRunningDropAmount;
uniform half uRunningDropSpeed ;
uniform half uAlpha;

// 生成一个三维噪声
vec3 N13(float p) {
    vec3 p3 = fract(vec3(p) * vec3(.1031,.11369,.13787));
    p3 += dot(p3, p3.yzx + 19.19);
    return fract((p3.xxy+p3.yzz)*p3.zyx);
}

// 生成一个一维噪声
float N(float t) {
    return fract(sin(t*12345.564)*7658.76);
}

// 生成一个锯齿波
half Saw(half b, half t) {
    return smoothstep(0., b, t)*smoothstep(1., b, t);
}

// 生成雨滴层
half4 GenDropLayerN(vec2 uv, half t) {
    t *= uRunningDropSpeed;
    uv /= uRunningDropSize;
    uv.y += t * 0.55; // 垂直方向上的位移

    half2 grid = half2(18., 3.); // 网格大小

    vec2 id = floor(uv * grid); // 计算网格索引
    half colShift = N(id.x); // 列偏移
    uv.y += colShift; // 应用列偏移
    id = floor(uv * grid); // 重新计算网格索引
    half3 n = N13(id.x * 35.2 + id.y * 2376.1); // 生成三维噪声
    return half4(n, colShift);
}

half DropLayer2(half2 uv, half3 n, half t, half colShift) {
    t *= uRunningDropSpeed;

    half oldY = uv.y; // 获取纹理坐标

    uv /= uRunningDropSize;
    uv.y += t * 0.55; // 垂直方向上的位移

    half2 a = half2(6., 1.); // 控制雨滴的形状
    half2 grid = half2(18., 3.); // 网格大小

    uv.y += colShift; // 应用列偏移
    half2 st = fract(uv * grid) - half2(.5, 0); // 计算网格内的偏移
    half x = n.x - .5; // x坐标
    half y = oldY * 20.; // y坐标
    x *= .7; // x坐标缩放
    half ti = fract(t + n.z); // 时间缩放
    y = (Saw(.9, ti) - .5) * .9 + .5; // y坐标变化
    half2 p = half2(x, y); // 雨滴位置
    half d = length((st - p) * a.yx); // 计算距离

    half mainDrop = smoothstep(.4, .0, d); // 主要雨滴

    half rr = smoothstep(1.0, y, st.y); // 半径
    half r = sqrt(rr); // 半径缩放
    half cd = abs(st.x - x); // 水平方向上的距离

    //half trail = smoothstep(.23 * r, .15 * rr, cd); // 拖尾效果

    half trailFront = smoothstep(-.02, .02, st.y - y); // 拖尾前沿
    //trail *= trailFront * rr; // 拖尾效果
    y = oldY; // 恢复y坐标

    half trail2 = smoothstep(.2 * r, .0, cd); // 拖尾效果2

    half droplets = max(0., (sin(y * (1. - y) * 120.) - st.y)) * trail2 * trailFront * n.z; // 水滴效果
    y = fract(y * 10.) + (st.y - .5); // 计算y坐标
    half dd = length(st - half2(x, y)); // 计算距离
    droplets = smoothstep(.3, 0., dd); // 水滴效果
    half m = mainDrop + droplets * r * trailFront; // 最终效果
    return m; // 返回结果
}

// 生成静态雨滴
half3 GenStaticLayerN(vec2 uv) {
    // 将纹理坐标缩放到40倍
    // uv *= 40.;
    uv *= 40. / uStaticDropSize;
    // 计算网格索引
    vec2 id = floor(uv);
    // 计算网格内的偏移
    // 生成三维噪声
    half3 n = N13(id.x * 107.45 + id.y * 3543.654);
    return n;
}

half StaticDrops(half2 uv, half3 n, half t) {
    t *= uStaticDropSpeed;
    // 将纹理坐标缩放到40倍
    // uv *= 40.;
    uv *= 40. / uStaticDropSize;
    // 计算网格内的偏移
    uv = fract(uv) - .5;
    // 计算雨滴位置
    half2 p = (n.xy - .5) * .7;
    // 计算距离
    half d = length(uv - p);
    // 计算时间缩放
    half fade = Saw(.025, fract(t + n.z));
    // 计算最终效果
    half c = smoothstep(.3, 0., d) * fract(n.z * 10.) * fade;
    return c;
}

// 生成雨滴效果
half Drops(half2 uv, half3 dropN, half3 staticN, half t, half rs, half ra, half colShift) {
    // 生成静态雨滴效果
    half s = StaticDrops(uv, staticN, t * 1.2) * rs * uStaticDropAmount;
    // 生成雨滴层效果
    half m1 = DropLayer2(uv, dropN, t * 1.85, colShift) * ra * uRunningDropAmount;
    // 计算最终的颜色值
    return smoothstep(.3, 1., s+m1);
}

half4 main(vec2 xy)
{
    half2 xyHalf = xy;
    //值往右下增加
    half2 UV = xyHalf/uResolution; // 获取纹理坐标

    vec2 uv = (xy - .5 *uResolution) / uResolution.y;
    uv.y = 1.0 - uv.y;


    half alpha = smoothstep(0.0, 2.0, uTime); // 透明度
//    float t = uTime * 0.46; // 时间缩放
    half t = mod(uTime * 0.46, 5.0); // 时间缩放

    half rainAmount = sin(UV.x * UV.y)*.3+.7; // 雨滴数量

    half staticDrops = smoothstep(-.5, 1., rainAmount)*2.; // 静态雨滴
    half4 dropN = GenDropLayerN(uv, t);
    half3 staticN = GenStaticLayerN(uv); // 生成噪声
    half c = Drops(uv, dropN.xyz, staticN, t, staticDrops, rainAmount, dropN.w); // 雨滴效果

    if(c>0.001) {
        half2 e = half2(.0005, 0.);

        half cx = Drops(uv+e, dropN.xyz, staticN, t, staticDrops, rainAmount, dropN.w);

        half2 n = half2(c-cx, cx-c);

        half2 texUV = UV+n;//test

        half4 uiColor = uTex.eval(UV * uResolution);
        half4 rainDropColor = uTex.eval(texUV * uResolution);
        half4 fragColor = mix(uiColor, rainDropColor, uAlpha*alpha); // 输出颜色
        return fragColor;
    }
    else {
        half4 uiColor = uTex.eval(UV * uResolution);
        return uiColor;
    }
}
