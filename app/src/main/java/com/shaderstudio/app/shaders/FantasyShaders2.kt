package com.shaderstudio.app.shaders

/**
 * Fantasy pack 2/2 — light moods, flow-field paint looks and sci-fi overlays.
 */

private const val MOONLIGHT = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float2 c = uCenter * uResolution;
    float r = length(coord - c) / min(uResolution.x, uResolution.y);
    float moon = exp(-r * r * mix(34.0, 9.0, uParam1));
    float halo = exp(-abs(r - 0.20) * 11.0) * 0.14;
    float3 src = float3(uImage.eval(coord).rgb);
    float lum = dot(src, float3(0.2126, 0.7152, 0.0722));
    float3 cool = mix(src, float3(lum) * float3(0.72, 0.83, 1.15), 0.6 * uParam2);
    float3 col = cool + (moon * 0.65 + halo) * float3(0.8, 0.88, 1.1);
    float2 sp = floor(coord / max(uResolution.x * 0.01, 4.0));
    float star = step(0.99, hash21(sp)) * (0.4 + 0.6 * sin(uTime * 2.0 + hash21(sp + 1.7) * 6.28));
    col += star * uParam3 * 0.5 * (1.0 - smoothstep(0.0, 0.6, r));
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

private const val GOLDEN_HOUR = PRELUDE + """
half4 main(float2 coord) {
    float2 c = uCenter * uResolution;
    float minD = min(uResolution.x, uResolution.y);
    float r = length(coord - c) / minD;
    float3 src = float3(uImage.eval(coord).rgb);
    float3 warm = src * mix(float3(1.0), float3(1.14, 0.99, 0.80), uParam1);
    warm = warm + float3(0.10, 0.05, 0.0) * uParam1 * (1.0 - warm);
    float sun = exp(-r * r * 26.0) * uParam2;
    float streak = exp(-pow((coord.y - c.y) / (minD * 0.012), 2.0))
                 * exp(-abs(coord.x - c.x) / (uResolution.x * 0.45)) * uParam3;
    float3 col = 1.0 - (1.0 - warm) * (1.0 - clamp(float3(1.3, 1.0, 0.6) * (sun + streak * 0.8), 0.0, 1.0));
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

private const val STARDUST_TRAIL = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float t = uTime * mix(0.15, 1.2, uParam3);
    float3 col = float3(uImage.eval(coord).rgb);
    for (int i = 0; i < 2; i++) {
        float fl = float(i);
        float cell = uResolution.x / (mix(8.0, 26.0, uParam1) * (1.0 + fl * 0.6));
        float ang = (fbm(floor(coord / cell) * 0.13 + fl) - 0.5) * 3.0 + 0.6;
        float2 dir = float2(cos(ang), sin(ang));
        float2 gp = coord / cell + dir * t * (2.0 + fl);
        float2 id = floor(gp);
        float2 f = fract(gp);
        float2 pos = float2(0.3, 0.3) + 0.4 * float2(hash21(id), hash21(id + 6.6));
        float2 d = (f - pos) * cell;
        float along = dot(d, dir);
        float across = dot(d, float2(-dir.y, dir.x));
        float len = cell * mix(0.2, 0.9, uParam2);
        float trail = exp(-across * across / (cell * cell * 0.004))
                    * exp(-along * along / (len * len))
                    * smoothstep(0.0, -len, along - len * 0.4);
        float tw = 0.5 + 0.5 * sin(uTime * 3.0 + hash21(id + 2.4) * 6.28);
        col += float3(0.9, 0.85, 1.1) * trail * tw * 0.9;
    }
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

private const val CRYSTAL_PRISM = PRELUDE + NOISE_LIB + """
float3 cpSmp(float2 p) {
    p = clamp(p, float2(0.0), uResolution - 1.0);
    return float3(uImage.eval(p).rgb);
}

half4 main(float2 coord) {
    float s = uResolution.x / mix(28.0, 8.0, uParam1);
    float2 g = coord / s;
    float2 gi = floor(g);
    float2 gf = fract(g);
    float upper = step(gf.x + gf.y, 1.0);
    float2 seed = gi * 2.3 + upper * 5.1;
    float ang = hash21(seed) * 6.28318;
    float2 dir = float2(cos(ang), sin(ang));
    float amt = s * 0.35 * hash21(seed + 3.3);
    float edge = min(min(gf.x, gf.y), abs(1.0 - gf.x - gf.y));
    float disp = clamp(0.16 - edge, 0.0, 0.16) * mix(0.0, 26.0, uParam2);
    float2 base = coord + dir * amt;
    float rr = cpSmp(base + dir * disp * 2.0).r;
    float gg = cpSmp(base).g;
    float bb = cpSmp(base - dir * disp * 2.0).b;
    float3 facet = float3(rr, gg, bb) * (0.92 + 0.16 * hash21(seed + 8.8));
    float3 src = float3(uImage.eval(coord).rgb);
    return half4(half3(clamp(mix(src, facet, uParam3), 0.0, 1.0)), 1.0);
}
"""

private const val IRIDESCENCE = PRELUDE + """
float irLum(float2 p, float2 res) {
    p = clamp(p, float2(0.0), res - 1.0);
    return dot(float3(uImage.eval(p).rgb), float3(0.2126, 0.7152, 0.0722));
}

float3 irPal(float t) {
    return 0.5 + 0.5 * cos(6.28318 * (t + float3(0.0, 0.33, 0.67)));
}

half4 main(float2 coord) {
    float o = max(1.5, uResolution.x / 800.0);
    float gx = irLum(coord + float2(o, 0.0), uResolution) - irLum(coord - float2(o, 0.0), uResolution);
    float gy = irLum(coord + float2(0.0, o), uResolution) - irLum(coord - float2(0.0, o), uResolution);
    float ang = atan(gy, gx + 0.0001);
    float lum = irLum(coord, uResolution);
    float t = uTime * mix(0.02, 0.3, uParam2);
    float3 film = irPal(ang / 6.28318 + lum * 1.8 + t);
    float grad = clamp(length(float2(gx, gy)) * 6.0, 0.0, 1.0);
    float sheen = mix(0.25, 1.0, grad) * uParam1;
    float3 src = float3(uImage.eval(coord).rgb);
    float3 col = 1.0 - (1.0 - src) * (1.0 - film * sheen);
    return half4(half3(clamp(mix(src, col, uParam3), 0.0, 1.0)), 1.0);
}
"""

private const val FLOW_FIELD = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float t = uTime * mix(0.05, 0.5, uParam3);
    float sc = mix(0.012, 0.004, uParam2);
    float ang = fbm(coord * sc + float2(t, -t * 0.7)) * 12.56636;
    float2 dir = float2(cos(ang), sin(ang));
    float len = min(uResolution.x, uResolution.y) * 0.02 * mix(0.4, 2.4, uParam1);
    float3 acc = float3(0.0);
    for (int i = 0; i < 8; i++) {
        float ft = (float(i) / 7.0 - 0.5) * 2.0;
        float2 p = clamp(coord + dir * len * ft, float2(0.0), uResolution - 1.0);
        acc += float3(uImage.eval(p).rgb);
    }
    float3 paint = acc / 8.0;
    float grain = (vnoise(coord * 0.9) - 0.5) * 0.06;
    return half4(half3(clamp(paint + grain, 0.0, 1.0)), 1.0);
}
"""

private const val MARBLE_INK = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float2 uv = coord / uResolution;
    float t = uTime * mix(0.02, 0.25, uParam3);
    float k = mix(2.0, 6.0, uParam2);
    float2 q = float2(fbm(uv * k + t), fbm(uv * k + float2(5.2, 1.3) - t));
    float2 r = float2(fbm(uv * k + q * 2.6 + float2(1.7, 9.2)), fbm(uv * k + q * 2.6 + float2(8.3, 2.8)));
    float2 warp = (r - 0.5) * uResolution.x * 0.22 * uParam1;
    float2 p = clamp(coord + warp, float2(0.0), uResolution - 1.0);
    float3 col = float3(uImage.eval(p).rgb);
    float vein = smoothstep(0.48, 0.5, fbm(uv * k * 2.0 + r * 2.0)) -
                 smoothstep(0.5, 0.52, fbm(uv * k * 2.0 + r * 2.0));
    col *= 1.0 - vein * 0.35;
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

private const val LIQUID_CHROME = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float2 uv = coord / uResolution;
    float t = uTime * mix(0.05, 0.5, uParam3);
    float n = fbm(uv * 4.0 + float2(t, -t * 0.6));
    float2 warp = (float2(n, fbm(uv * 4.0 + 7.7 - t)) - 0.5) * uResolution.x * 0.05;
    float2 p = clamp(coord + warp, float2(0.0), uResolution - 1.0);
    float3 src = float3(uImage.eval(p).rgb);
    float lum = dot(src, float3(0.2126, 0.7152, 0.0722));
    float bands = 0.5 + 0.5 * sin(lum * mix(6.0, 22.0, uParam1) + n * 5.0);
    float3 metal = float3(bands) * float3(0.78, 0.84, 0.96);
    metal += pow(bands, 8.0) * 0.65;
    metal *= 0.35 + 0.8 * lum;
    float3 orig = float3(uImage.eval(coord).rgb);
    return half4(half3(clamp(mix(orig, metal, uParam2), 0.0, 1.0)), 1.0);
}
"""

private const val OIL_SLICK = PRELUDE + NOISE_LIB + """
float3 osPal(float t) {
    return 0.5 + 0.5 * cos(6.28318 * (t + float3(0.0, 0.33, 0.67)));
}

half4 main(float2 coord) {
    float2 uv = coord / uResolution;
    float t = uTime * mix(0.03, 0.3, uParam3);
    float k = mix(2.0, 7.0, uParam1);
    float2 q = float2(fbm(uv * k + t), fbm(uv * k + 4.2 - t));
    float n = fbm(uv * k + q * 2.2);
    float3 film = osPal(n * 2.3 + t * 0.6);
    float3 src = float3(uImage.eval(coord).rgb) * 0.72;
    float3 col = 1.0 - (1.0 - src) * (1.0 - film * mix(0.2, 0.85, uParam2));
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

private const val INK_BLEED = PRELUDE + NOISE_LIB + """
float ibLum(float2 p, float2 res) {
    p = clamp(p, float2(0.0), res - 1.0);
    return dot(float3(uImage.eval(p).rgb), float3(0.2126, 0.7152, 0.0722));
}

half4 main(float2 coord) {
    float radius = min(uResolution.x, uResolution.y) * 0.006 * mix(0.5, 3.0, uParam1);
    float mn = ibLum(coord, uResolution);
    mn = min(mn, ibLum(coord + float2( radius, 0.0), uResolution));
    mn = min(mn, ibLum(coord + float2(-radius, 0.0), uResolution));
    mn = min(mn, ibLum(coord + float2(0.0,  radius), uResolution));
    mn = min(mn, ibLum(coord + float2(0.0, -radius), uResolution));
    float dg = radius * 0.7071;
    mn = min(mn, ibLum(coord + float2( dg,  dg), uResolution));
    mn = min(mn, ibLum(coord + float2(-dg, -dg), uResolution));
    float bleed = mn + (vnoise(coord * 0.11) - 0.5) * 0.12;
    float3 src = float3(uImage.eval(coord).rgb);
    float3 paper = float3(0.94, 0.91, 0.84);
    float3 ink = mix(paper, float3(0.10, 0.09, 0.13), 1.0 - smoothstep(0.15, 0.75, bleed));
    float3 col = mix(src, ink * mix(1.0, dot(src, float3(0.5, 0.4, 0.1)) + 0.6, 0.4), uParam2);
    return half4(half3(clamp(mix(src, col, uParam3), 0.0, 1.0)), 1.0);
}
"""

private const val SILK_WAVES = PRELUDE + """
half4 main(float2 coord) {
    float t = uTime * mix(0.3, 2.2, uParam3);
    float ang = uParam2 * 3.14159;
    float2 dir = float2(cos(ang), sin(ang));
    float2 perp = float2(-dir.y, dir.x);
    float freq = 9.42478 / min(uResolution.x, uResolution.y);
    float phase = dot(coord, dir) * freq + t;
    float amp = min(uResolution.x, uResolution.y) * 0.02 * mix(0.3, 2.0, uParam1);
    float2 p = coord + perp * sin(phase) * amp;
    p = clamp(p, float2(0.0), uResolution - 1.0);
    float3 col = float3(uImage.eval(p).rgb);
    col *= 0.88 + 0.18 * cos(phase);
    col += pow(max(cos(phase - 0.6), 0.0), 12.0) * 0.10;
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

private const val LAVA_LAMP = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float2 uv = coord / uResolution;
    float t = uTime * mix(0.03, 0.3, uParam3);
    float k = mix(1.6, 4.5, uParam1);
    float blob = fbm(uv * k + float2(t, -t * 1.6));
    float mask = smoothstep(0.52, 0.60, blob);
    float edge = smoothstep(0.48, 0.56, blob) - smoothstep(0.56, 0.64, blob);
    float2 warp = (float2(vnoise(uv * k * 3.0 + t), vnoise(uv * k * 3.0 + 9.9)) - 0.5)
                * uResolution.x * 0.05 * mask;
    float2 p = clamp(coord + warp, float2(0.0), uResolution - 1.0);
    float3 inside = float3(uImage.eval(p).rgb) * float3(1.25, 0.75, 0.55) + float3(0.18, 0.02, 0.0);
    float3 outside = float3(uImage.eval(coord).rgb) * float3(0.72, 0.78, 1.0);
    float3 col = mix(outside, inside, mask);
    col += edge * float3(1.0, 0.45, 0.15) * mix(0.3, 1.2, uParam2);
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

private const val MELTING = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float2 uv = coord / uResolution;
    float t = uTime * mix(0.05, 0.45, uParam3);
    float streaks = mix(3.0, 14.0, uParam2);
    float colN = fbm(float2(uv.x * streaks, t));
    float drip = pow(colN, 1.5) * uResolution.y * 0.35 * uParam1 * pow(uv.y, 1.4);
    float2 p = float2(coord.x, coord.y - drip);
    p = clamp(p, float2(0.0), uResolution - 1.0);
    float3 col = float3(uImage.eval(p).rgb);
    float3 below = float3(uImage.eval(clamp(p - float2(0.0, drip * 0.12 + 2.0), float2(0.0), uResolution - 1.0)).rgb);
    col = mix(col, below, 0.35 * step(1.0, drip));
    return half4(half3(col), 1.0);
}
"""

private const val TURBULENCE = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float2 uv = coord / uResolution;
    float t = uTime * mix(0.2, 1.8, uParam3);
    float k = mix(2.5, 8.0, uParam2);
    float2 q = float2(fbm(uv * k + t), fbm(uv * k + 3.1 - t * 1.2));
    float2 r = float2(fbm(uv * k + q * 3.0 + t * 0.5), fbm(uv * k + q * 3.0 + 6.6));
    float2 warp = (r - 0.5) * uResolution.x * 0.14 * uParam1;
    float3 acc = float3(0.0);
    for (int i = 0; i < 4; i++) {
        float ft = float(i) / 3.0;
        float2 p = clamp(coord + warp * ft, float2(0.0), uResolution - 1.0);
        acc += float3(uImage.eval(p).rgb);
    }
    return half4(half3(clamp(acc / 4.0, 0.0, 1.0)), 1.0);
}
"""

private const val ZEN_RIPPLES = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float t = uTime * mix(0.15, 0.9, uParam3);
    float minD = min(uResolution.x, uResolution.y);
    float2 totalOff = float2(0.0);
    float shine = 0.0;
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float2 srcP = uResolution * (0.2 + 0.6 * float2(hash21(float2(fi, 3.7)), hash21(float2(fi, 8.1))));
        float2 d = coord - srcP;
        float len = length(d) + 0.001;
        float phase = fract(t * (0.5 + 0.3 * fi) + fi * 0.37);
        float ringR = phase * minD * 0.7;
        float wave = sin((len - ringR) * mix(0.15, 0.05, uParam2)) *
                     exp(-abs(len - ringR) / (minD * 0.06)) * (1.0 - phase);
        totalOff += (d / len) * wave * mix(2.0, 14.0, uParam1);
        shine += wave * 0.05;
    }
    float2 p = clamp(coord + totalOff, float2(0.0), uResolution - 1.0);
    float3 col = float3(uImage.eval(p).rgb) + shine;
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

private const val HOLOGRAM = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float t = uTime * mix(0.6, 4.0, uParam3);
    float off = mix(1.0, 10.0, uParam1) * (uResolution.x / 1400.0 + 0.4);
    float r = uImage.eval(clamp(coord + float2(off, 0.0), float2(0.0), uResolution - 1.0)).r;
    float g = uImage.eval(coord).g;
    float b = uImage.eval(clamp(coord - float2(off, 0.0), float2(0.0), uResolution - 1.0)).b;
    float3 col = float3(r, g, b) * float3(0.55, 0.95, 1.05);
    float bandH = max(3.0, uResolution.y / mix(60.0, 160.0, uParam2));
    float band = 0.78 + 0.22 * step(0.5, fract(coord.y / bandH * 0.5));
    col *= band;
    float sweepPos = fract(t * 0.25) * uResolution.y;
    col += exp(-abs(coord.y - sweepPos) / (uResolution.y * 0.01)) * float3(0.3, 0.9, 1.0) * 0.4;
    float flick = 0.92 + 0.08 * hash21(float2(floor(uTime * 18.0), 4.4));
    col *= flick;
    col += float3(0.0, 0.25, 0.3) * 0.25;
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

private const val CYBER_GRID = PRELUDE + """
half4 main(float2 coord) {
    float2 uv = coord / uResolution;
    float t = uTime * mix(0.2, 1.6, uParam3);
    float horizon = mix(0.45, 0.75, uParam1);
    float3 src = float3(uImage.eval(coord).rgb);
    float3 col = src * float3(0.8, 0.7, 1.0) * 0.85;
    float gy = uv.y - horizon;
    if (gy > 0.0) {
        float persp = 1.0 / max(gy, 0.02);
        float lx = abs(fract((uv.x - 0.5) * persp * 1.6 + 0.5) - 0.5);
        float ly = abs(fract(persp * 0.8 - t) - 0.5);
        float lines = exp(-lx * 26.0) + exp(-ly * 26.0);
        float fade = smoothstep(0.0, 0.25, gy);
        col += float3(1.0, 0.2, 0.9) * lines * fade * mix(0.2, 0.9, uParam2);
    }
    float hg = exp(-abs(uv.y - horizon) * 26.0);
    col += float3(1.0, 0.4, 0.9) * hg * mix(0.3, 1.0, uParam2);
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

private const val DATA_STREAM = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float t = uTime * mix(0.3, 2.4, uParam3);
    float colW = uResolution.x / mix(20.0, 70.0, uParam1);
    float cid = floor(coord.x / colW);
    float speed = 0.3 + 0.7 * hash21(float2(cid, 2.2));
    float shift = t * speed * uResolution.y * 0.4 * uParam2;
    float2 p = float2(coord.x, coord.y - shift);
    p.y = clamp(mod(p.y, uResolution.y), 0.0, uResolution.y - 1.0);
    float seg = floor((coord.y / uResolution.y + t * speed) * 24.0);
    float bar = step(0.55, hash21(float2(cid * 1.3, seg)));
    float3 stream = float3(uImage.eval(p).rgb);
    float3 src = float3(uImage.eval(coord).rgb);
    float3 col = mix(src, stream * float3(0.6, 1.1, 0.75) + float3(0.0, 0.12, 0.03), bar * uParam2);
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

private const val FORCE_FIELD = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float2 c = uCenter * uResolution;
    float minD = min(uResolution.x, uResolution.y);
    float r = length(coord - c) / minD;
    float radius = mix(0.18, 0.55, uParam1);
    float t = uTime * mix(0.5, 3.0, uParam3);
    float s = minD / 22.0;
    float2 grid = float2(1.5 * s, 1.7320508 * s);
    float2 a = mod(coord, grid) - grid * 0.5;
    float2 b = mod(coord - grid * 0.5, grid) - grid * 0.5;
    float2 hc = (dot(a, a) < dot(b, b)) ? coord - a : coord - b;
    float pulse = 0.4 + 0.6 * sin(t * 2.0 + hash21(floor(hc / s)) * 6.28318);
    float dome = smoothstep(radius, radius * 0.92, r);
    float rim = exp(-abs(r - radius) * 34.0);
    float hexEdge = smoothstep(s * 0.42, s * 0.5, distance(coord, hc));
    float shield = (hexEdge * 0.6 + 0.15) * pulse * dome + rim * 1.4;
    float2 p = clamp(coord + (coord - c) * 0.015 * dome * uParam2, float2(0.0), uResolution - 1.0);
    float3 src = float3(uImage.eval(p).rgb);
    float3 col = src + float3(0.25, 0.75, 1.1) * shield * mix(0.3, 1.1, uParam2);
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

private const val TELEPORT = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float rows = mix(14.0, 60.0, uParam2);
    float rowH = uResolution.y / rows;
    float rid = floor(coord.y / rowH);
    float rnd = hash21(float2(rid, 7.7));
    float prog = uParam1 * 1.25;
    float gone = smoothstep(rnd, rnd + 0.18, prog);
    float dir = step(0.5, hash21(float2(rid, 2.3))) * 2.0 - 1.0;
    float2 p = float2(coord.x + dir * gone * uResolution.x * 0.6, coord.y);
    p.x = clamp(p.x, 0.0, uResolution.x - 1.0);
    float3 src = float3(uImage.eval(p).rgb);
    float3 col = src * (1.0 - gone);
    float fringe = smoothstep(rnd - 0.06, rnd, prog) * (1.0 - gone);
    float spark = step(0.94, hash21(coord * 0.4 + floor(uTime * 12.0))) * fringe;
    col += float3(0.4, 0.9, 1.2) * (fringe * 0.35 + spark) * uParam3;
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

private const val RADAR_SWEEP = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float2 c = uCenter * uResolution;
    float minD = min(uResolution.x, uResolution.y);
    float2 d = coord - c;
    float r = length(d) / minD;
    float a = atan(d.y, d.x) / 6.28318 + 0.5;
    float t = uTime * mix(0.1, 0.8, uParam3);
    float sweep = fract(a - t);
    float trail = pow(1.0 - sweep, mix(3.0, 10.0, 1.0 - uParam1));
    float beam = exp(-sweep * 90.0);
    float rings = exp(-abs(fract(r * 4.0) - 0.5) * mix(4.0, 14.0, uParam2)) * 0.12;
    float3 src = float3(uImage.eval(coord).rgb);
    float3 col = src * float3(0.55, 0.75, 0.6);
    col += float3(0.2, 1.0, 0.4) * (trail * 0.35 + beam * 0.8) * smoothstep(0.9, 0.2, r);
    col += float3(0.2, 0.9, 0.4) * rings * smoothstep(0.9, 0.2, r);
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

private const val GLITCH_STORM = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float t = floor(uTime * mix(4.0, 16.0, uParam3));
    float2 bs = uResolution / mix(4.0, 12.0, uParam1);
    float2 bid = floor(coord / bs);
    float br = hash21(bid + t * 0.31);
    float2 off = (float2(hash21(bid + t), hash21(bid + t + 5.5)) - 0.5)
               * uResolution.x * 0.12 * step(0.6, br) * uParam2;
    float2 p = clamp(coord + off, float2(0.0), uResolution - 1.0);
    float3 col = float3(uImage.eval(p).rgb);
    float swap = step(0.9, hash21(bid + t + 9.9));
    col = mix(col, col.bgr, swap * uParam2);
    float burst = step(0.96, hash21(float2(floor(coord.y / 3.0), t))) * uParam2;
    col = mix(col, float3(hash21(coord + t)), burst * 0.6);
    float dark = 1.0 - 0.15 * step(0.85, hash21(float2(t, 1.1))) * uParam2;
    return half4(half3(clamp(col * dark, 0.0, 1.0)), 1.0);
}
"""

private const val XRAY = PRELUDE + """
float xrLum(float2 p, float2 res) {
    p = clamp(p, float2(0.0), res - 1.0);
    return dot(float3(uImage.eval(p).rgb), float3(0.2126, 0.7152, 0.0722));
}

half4 main(float2 coord) {
    float lum = xrLum(coord, uResolution);
    float body = pow(1.0 - lum, mix(0.7, 2.2, uParam1));
    float o = max(1.5, uResolution.x / 800.0);
    float gx = xrLum(coord + float2(o, 0.0), uResolution) - xrLum(coord - float2(o, 0.0), uResolution);
    float gy = xrLum(coord + float2(0.0, o), uResolution) - xrLum(coord - float2(0.0, o), uResolution);
    float edge = clamp(length(float2(gx, gy)) * mix(2.0, 8.0, uParam2), 0.0, 1.0);
    float3 xr = float3(body * 0.72, body * 0.92, body * 1.18) + edge * float3(0.5, 0.75, 1.0);
    float3 src = float3(uImage.eval(coord).rgb);
    return half4(half3(clamp(mix(src, xr, uParam3), 0.0, 1.0)), 1.0);
}
"""

private const val COMIC_NOIR = PRELUDE + """
float cnLum(float2 p, float2 res) {
    p = clamp(p, float2(0.0), res - 1.0);
    return dot(float3(uImage.eval(p).rgb), float3(0.2126, 0.7152, 0.0722));
}

half4 main(float2 coord) {
    float lum = cnLum(coord, uResolution);
    float thr = mix(0.35, 0.65, uParam1);
    float base = smoothstep(thr - 0.04, thr + 0.04, lum);
    float cell = uResolution.x / 110.0;
    float2 rc = float2(coord.x * 0.7071 - coord.y * 0.7071, coord.x * 0.7071 + coord.y * 0.7071);
    float2 center = (floor(rc / cell) + 0.5) * cell;
    float dotR = cell * 0.62 * sqrt(clamp((thr + 0.22 - lum) / 0.44, 0.0, 1.0)) * uParam2;
    float dots = 1.0 - smoothstep(dotR - 1.0, dotR + 1.0, distance(rc, center));
    float midband = smoothstep(thr - 0.22, thr - 0.05, lum) * (1.0 - smoothstep(thr + 0.02, thr + 0.2, lum));
    float o = max(1.5, uResolution.x / 800.0);
    float gx = cnLum(coord + float2(o, 0.0), uResolution) - cnLum(coord - float2(o, 0.0), uResolution);
    float gy = cnLum(coord + float2(0.0, o), uResolution) - cnLum(coord - float2(0.0, o), uResolution);
    float edge = smoothstep(0.25, 0.6, length(float2(gx, gy)) * 4.0);
    float ink = clamp((1.0 - base) + dots * midband + edge, 0.0, 1.0);
    float3 col = mix(float3(0.97, 0.95, 0.90), float3(0.06, 0.05, 0.08), ink);
    float3 src = float3(uImage.eval(coord).rgb);
    return half4(half3(clamp(mix(src, col, uParam3), 0.0, 1.0)), 1.0);
}
"""

private const val FRACTAL_ZOOM = PRELUDE + """
float3 fzPal(float t) {
    return 0.5 + 0.5 * cos(6.28318 * (t + float3(0.0, 0.15, 0.35)));
}

half4 main(float2 coord) {
    float2 c = uCenter * uResolution;
    float minD = min(uResolution.x, uResolution.y);
    float t = uTime * mix(0.05, 0.5, uParam3);
    float2 z = (coord - c) / minD * mix(3.2, 1.1, uParam1);
    float2 cc = float2(0.7885 * cos(t * 0.31), 0.7885 * sin(t * 0.23));
    float m = 0.0;
    for (int i = 0; i < 14; i++) {
        z = float2(z.x * z.x - z.y * z.y, 2.0 * z.x * z.y) + cc;
        if (dot(z, z) > 4.0) { break; }
        m += 1.0;
    }
    float f = m / 14.0;
    float3 tint = fzPal(f * 1.4 + t * 0.1);
    float3 src = float3(uImage.eval(coord).rgb);
    float3 col = src + tint * f * mix(0.3, 1.3, uParam2);
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

/** Fantasy pack 2 registry. */
object FantasyEffects2 {
    val all: List<ShaderEffect> = listOf(
        ShaderEffect(
            id = "moonlight", name = "Moonlight", tagline = "silver night",
            params = listOf(ShaderParam("Glow", 0.5f), ShaderParam("Mood", 0.6f), ShaderParam("Stars", 0.5f)),
            agsl = MOONLIGHT, animated = true, positionable = true,
            accentStart = 0xFFBFD4FF, accentEnd = 0xFF16213E,
        ),
        ShaderEffect(
            id = "goldenhour", name = "Golden Hour", tagline = "sunset kiss",
            params = listOf(ShaderParam("Warmth", 0.6f), ShaderParam("Sun", 0.55f), ShaderParam("Flare", 0.4f)),
            agsl = GOLDEN_HOUR, positionable = true,
            accentStart = 0xFFFFB03D, accentEnd = 0xFF8C3503,
        ),
        ShaderEffect(
            id = "stardusttrail", name = "Stardust Trail", tagline = "comet shower",
            params = listOf(ShaderParam("Density", 0.5f), ShaderParam("Length", 0.55f), ShaderParam("Speed", 0.45f)),
            agsl = STARDUST_TRAIL, animated = true,
            accentStart = 0xFFD9C8FF, accentEnd = 0xFF2E1A6E,
        ),
        ShaderEffect(
            id = "crystalprism", name = "Crystal Prism", tagline = "faceted dispersion",
            params = listOf(ShaderParam("Size", 0.5f), ShaderParam("Dispersion", 0.55f), ShaderParam("Blend", 1.0f)),
            agsl = CRYSTAL_PRISM,
            accentStart = 0xFFB8FFF2, accentEnd = 0xFF3D7A8C,
        ),
        ShaderEffect(
            id = "iridescence", name = "Iridescence", tagline = "thin-film sheen",
            params = listOf(ShaderParam("Sheen", 0.55f), ShaderParam("Shift", 0.4f), ShaderParam("Blend", 0.8f)),
            agsl = IRIDESCENCE, animated = true,
            accentStart = 0xFFB6FFDB, accentEnd = 0xFFCE6BFF,
        ),
        ShaderEffect(
            id = "flowfield", name = "Flow Field", tagline = "van gogh strokes",
            params = listOf(ShaderParam("Length", 0.5f), ShaderParam("Scale", 0.5f), ShaderParam("Drift", 0.35f)),
            agsl = FLOW_FIELD, animated = true,
            accentStart = 0xFF3D6BFF, accentEnd = 0xFFFFD23D,
        ),
        ShaderEffect(
            id = "marbleink", name = "Marble Ink", tagline = "suminagashi",
            params = listOf(ShaderParam("Warp", 0.5f), ShaderParam("Detail", 0.5f), ShaderParam("Speed", 0.3f)),
            agsl = MARBLE_INK, animated = true,
            accentStart = 0xFFE8E0D0, accentEnd = 0xFF3A3630,
        ),
        ShaderEffect(
            id = "liquidchrome", name = "Liquid Chrome", tagline = "molten metal",
            params = listOf(ShaderParam("Bands", 0.5f), ShaderParam("Blend", 0.85f), ShaderParam("Speed", 0.35f)),
            agsl = LIQUID_CHROME, animated = true,
            accentStart = 0xFFDDE4F0, accentEnd = 0xFF4A5568,
        ),
        ShaderEffect(
            id = "oilslick", name = "Oil Slick", tagline = "rainbow film",
            params = listOf(ShaderParam("Scale", 0.5f), ShaderParam("Opacity", 0.55f), ShaderParam("Speed", 0.35f)),
            agsl = OIL_SLICK, animated = true,
            accentStart = 0xFF52FFB8, accentEnd = 0xFF7A3DFF,
        ),
        ShaderEffect(
            id = "inkbleed", name = "Ink Bleed", tagline = "wet sumi-e",
            params = listOf(ShaderParam("Spread", 0.5f), ShaderParam("Ink", 0.65f), ShaderParam("Blend", 0.9f)),
            agsl = INK_BLEED,
            accentStart = 0xFFEDE6D6, accentEnd = 0xFF1F1B24,
        ),
        ShaderEffect(
            id = "silkwaves", name = "Silk Waves", tagline = "flowing fabric",
            params = listOf(ShaderParam("Amplitude", 0.5f), ShaderParam("Angle", 0.25f), ShaderParam("Speed", 0.45f)),
            agsl = SILK_WAVES, animated = true,
            accentStart = 0xFFFFD1DC, accentEnd = 0xFF8C3D5E,
        ),
        ShaderEffect(
            id = "lavalamp", name = "Lava Lamp", tagline = "warm blobs",
            params = listOf(ShaderParam("Blobs", 0.5f), ShaderParam("Glow", 0.55f), ShaderParam("Speed", 0.35f)),
            agsl = LAVA_LAMP, animated = true,
            accentStart = 0xFFFF6B35, accentEnd = 0xFF2E1760,
        ),
        ShaderEffect(
            id = "melting", name = "Melting", tagline = "dripping canvas",
            params = listOf(ShaderParam("Amount", 0.5f), ShaderParam("Streaks", 0.5f), ShaderParam("Speed", 0.3f)),
            agsl = MELTING, animated = true,
            accentStart = 0xFFFF8A3D, accentEnd = 0xFF6E2C00,
        ),
        ShaderEffect(
            id = "turbulence", name = "Turbulence", tagline = "churning air",
            params = listOf(ShaderParam("Strength", 0.5f), ShaderParam("Scale", 0.5f), ShaderParam("Speed", 0.5f)),
            agsl = TURBULENCE, animated = true,
            accentStart = 0xFF8AD9FF, accentEnd = 0xFF3A3D8C,
        ),
        ShaderEffect(
            id = "zenripples", name = "Zen Ripples", tagline = "pond rings",
            params = listOf(ShaderParam("Strength", 0.5f), ShaderParam("Frequency", 0.5f), ShaderParam("Speed", 0.4f)),
            agsl = ZEN_RIPPLES, animated = true,
            accentStart = 0xFFB8E8D9, accentEnd = 0xFF1E5A50,
        ),
        ShaderEffect(
            id = "hologramfx", name = "Hologram", tagline = "ghost projection",
            params = listOf(ShaderParam("Split", 0.45f), ShaderParam("Bands", 0.5f), ShaderParam("Speed", 0.5f)),
            agsl = HOLOGRAM, animated = true,
            accentStart = 0xFF52E8FF, accentEnd = 0xFF0A3D66,
        ),
        ShaderEffect(
            id = "cybergrid", name = "Cyber Grid", tagline = "synthwave floor",
            params = listOf(ShaderParam("Horizon", 0.5f), ShaderParam("Glow", 0.55f), ShaderParam("Speed", 0.5f)),
            agsl = CYBER_GRID, animated = true,
            accentStart = 0xFFFF2E9E, accentEnd = 0xFF16003D,
        ),
        ShaderEffect(
            id = "datastream", name = "Data Stream", tagline = "living code",
            params = listOf(ShaderParam("Columns", 0.5f), ShaderParam("Distort", 0.55f), ShaderParam("Speed", 0.5f)),
            agsl = DATA_STREAM, animated = true,
            accentStart = 0xFF26FF6B, accentEnd = 0xFF06280F,
        ),
        ShaderEffect(
            id = "forcefield", name = "Force Field", tagline = "hex shield",
            params = listOf(ShaderParam("Size", 0.5f), ShaderParam("Strength", 0.55f), ShaderParam("Speed", 0.5f)),
            agsl = FORCE_FIELD, animated = true, positionable = true,
            accentStart = 0xFF52C8FF, accentEnd = 0xFF10245E,
        ),
        ShaderEffect(
            id = "teleport", name = "Teleport", tagline = "slice dissolve",
            params = listOf(ShaderParam("Progress", 0.35f), ShaderParam("Slices", 0.5f), ShaderParam("Sparks", 0.6f)),
            agsl = TELEPORT, animated = true,
            accentStart = 0xFF52FFE8, accentEnd = 0xFF2E3DB3,
        ),
        ShaderEffect(
            id = "radarsweep", name = "Radar Sweep", tagline = "sonar pulse",
            params = listOf(ShaderParam("Trail", 0.55f), ShaderParam("Rings", 0.5f), ShaderParam("Speed", 0.45f)),
            agsl = RADAR_SWEEP, animated = true, positionable = true,
            accentStart = 0xFF26FF6B, accentEnd = 0xFF0A2E14,
        ),
        ShaderEffect(
            id = "glitchstorm", name = "Glitch Storm", tagline = "total corruption",
            params = listOf(ShaderParam("Blocks", 0.5f), ShaderParam("Chaos", 0.55f), ShaderParam("Speed", 0.5f)),
            agsl = GLITCH_STORM, animated = true,
            accentStart = 0xFFFF2E5E, accentEnd = 0xFF00E5C2,
        ),
        ShaderEffect(
            id = "xray", name = "X-Ray", tagline = "inverted scan",
            params = listOf(ShaderParam("Density", 0.5f), ShaderParam("Edges", 0.5f), ShaderParam("Blend", 1.0f)),
            agsl = XRAY,
            accentStart = 0xFFB8E8FF, accentEnd = 0xFF10305E,
        ),
        ShaderEffect(
            id = "comicnoir", name = "Comic Noir", tagline = "sin city ink",
            params = listOf(ShaderParam("Threshold", 0.5f), ShaderParam("Dots", 0.6f), ShaderParam("Blend", 1.0f)),
            agsl = COMIC_NOIR,
            accentStart = 0xFFF0EDE4, accentEnd = 0xFF16141A,
        ),
        ShaderEffect(
            id = "fractalzoom", name = "Fractal Zoom", tagline = "julia bloom",
            params = listOf(ShaderParam("Zoom", 0.5f), ShaderParam("Intensity", 0.55f), ShaderParam("Speed", 0.35f)),
            agsl = FRACTAL_ZOOM, animated = true, positionable = true,
            accentStart = 0xFFFF6BD1, accentEnd = 0xFF1A0B4E,
        ),
    )
}
