package com.shaderstudio.app.shaders

/**
 * Fantasy pack 1/2 — flowing, animated, cinematic effects built on
 * domain-warped fbm noise, IQ cosine palettes and additive light.
 */

private const val INFERNO = PRELUDE + NOISE_LIB + """
float3 smp(float2 p) {
    p = clamp(p, float2(0.0), uResolution - 1.0);
    return float3(uImage.eval(p).rgb);
}

half4 main(float2 coord) {
    float2 uv = coord / uResolution;
    float t = uTime * mix(0.4, 2.2, uParam3);
    float h = mix(0.25, 0.85, uParam1);
    float2 fp = float2(uv.x * 3.5, (1.0 - uv.y) * 2.2 - t);
    float n = fbm(fp) * 0.75 + 0.25 * fbm(fp * 2.7 + float2(t * 0.5, 0.0));
    float shape = clamp(((1.0 - uv.y) - (1.0 - h)) / max(h, 0.001) + (n - 0.5) * 0.7, 0.0, 1.0);
    float fire = pow(shape, 1.6);
    float3 fc = float3(1.5, 0.5, 0.05) * fire + float3(1.2, 0.9, 0.2) * pow(fire, 3.0);
    float3 src = smp(coord + float2((n - 0.5) * 18.0 * fire, -22.0 * fire * uParam2));
    float3 col = src + fc * mix(0.3, 1.6, uParam2);
    col = mix(col, col * float3(1.05, 0.9, 0.75), fire * 0.5);
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

private const val PLASMA_ORB = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float2 c = uCenter * uResolution;
    float minD = min(uResolution.x, uResolution.y);
    float2 d = (coord - c) / minD;
    float r = length(d);
    float a = atan(d.y, d.x);
    float t = uTime * mix(0.3, 2.5, uParam3);
    float radius = mix(0.15, 0.5, uParam1);
    float wob = fbm(float2(a * 2.0 + t, r * 6.0 - t)) - 0.5;
    float ring = abs(r - radius + wob * 0.08);
    float core = exp(-r * r / (radius * radius * 0.25));
    float glow = exp(-ring * 22.0) + core * 0.7;
    float3 pc = float3(0.35, 0.6, 1.6) * glow + float3(0.9, 0.3, 1.4) * pow(glow, 2.5);
    float2 warp = d * exp(-ring * 10.0) * 26.0 * uParam2;
    float2 p = clamp(coord + warp, float2(0.0), uResolution - 1.0);
    float3 src = float3(uImage.eval(p).rgb);
    float3 col = src + pc * mix(0.4, 1.6, uParam2);
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

private const val ELECTRIC_STORM = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float2 uv = coord / uResolution;
    float t = floor(uTime * mix(2.0, 10.0, uParam3));
    float seed = hash21(float2(t, 17.3));
    float on = step(hash21(float2(t, 3.1)), mix(0.35, 0.9, uParam2));
    float bx = 0.15 + 0.7 * seed;
    float wig = (fbm(float2(uv.y * 6.0, t * 7.7)) - 0.5) * 0.35 * (1.0 - uv.y * 0.4);
    float bolt = abs(uv.x - bx - wig);
    float thick = mix(0.002, 0.012, uParam1);
    float li = exp(-bolt / thick) * on;
    float halo = exp(-bolt / (thick * 14.0)) * 0.35 * on;
    float3 src = float3(uImage.eval(coord).rgb);
    float3 col = src * (0.8 + 0.6 * halo) + float3(0.75, 0.85, 1.3) * (li + halo * 0.6);
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

private const val SOLAR_FLARE = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float2 c = uCenter * uResolution;
    float2 d = coord - c;
    float r = length(d) / min(uResolution.x, uResolution.y);
    float a = atan(d.y, d.x);
    float t = uTime * mix(0.1, 1.2, uParam3);
    float rays = pow(0.5 + 0.5 * sin(a * 9.0 + t * 2.0 + fbm(float2(a * 3.0, t)) * 4.0), 3.0);
    rays += 0.6 * pow(0.5 + 0.5 * sin(a * 17.0 - t * 3.0), 5.0);
    float fall = exp(-r * mix(5.0, 1.6, uParam1));
    float sun = exp(-r * r * 40.0);
    float3 beam = (float3(1.3, 0.95, 0.55) * rays * fall + float3(1.5, 1.1, 0.7) * sun) * mix(0.3, 1.4, uParam2);
    float3 src = float3(uImage.eval(coord).rgb);
    float3 col = 1.0 - (1.0 - src) * (1.0 - clamp(beam, 0.0, 1.0));
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

private const val EMBER_DUST = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float3 col = float3(uImage.eval(coord).rgb);
    float t = uTime * mix(0.15, 0.9, uParam3);
    for (int i = 0; i < 3; i++) {
        float fl = float(i);
        float cell = uResolution.x / (mix(10.0, 30.0, uParam1) * (1.0 + fl * 0.6));
        float2 gp = coord / cell + float2(fl * 7.3, t * (1.0 + fl * 0.8) * 3.0);
        float2 id = floor(gp);
        float2 f = fract(gp);
        float2 pos = float2(0.25, 0.25) + 0.5 * float2(hash21(id), hash21(id + 4.7));
        float dd = length((f - pos) * cell);
        float sz = cell * 0.06 * (0.6 + 0.8 * hash21(id + 9.2));
        float tw = 0.55 + 0.45 * sin(uTime * 3.0 + hash21(id + 2.2) * 6.28);
        float g = exp(-dd * dd / max(sz * sz, 0.5)) * tw;
        col += float3(1.2, 0.55, 0.12) * g * mix(0.4, 1.5, uParam2) / (1.0 + fl);
    }
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

private const val NEON_PULSE = PRELUDE + """
float npLum(float2 p, float2 res) {
    p = clamp(p, float2(0.0), res - 1.0);
    return dot(float3(uImage.eval(p).rgb), float3(0.2126, 0.7152, 0.0722));
}

float3 npPal(float t) {
    return 0.5 + 0.5 * cos(6.28318 * (t + float3(0.0, 0.33, 0.67)));
}

half4 main(float2 coord) {
    float o = max(1.5, uResolution.x / 700.0);
    float gx = npLum(coord + float2(o, 0.0), uResolution) - npLum(coord - float2(o, 0.0), uResolution);
    float gy = npLum(coord + float2(0.0, o), uResolution) - npLum(coord - float2(0.0, o), uResolution);
    float e = length(float2(gx, gy)) * mix(1.5, 6.0, uParam1);
    float pulse = 0.55 + 0.45 * sin(uTime * mix(1.0, 8.0, uParam3));
    float3 neon = npPal(fract(uTime * 0.05));
    float3 src = float3(uImage.eval(coord).rgb);
    float3 col = src * mix(1.0, 0.35, uParam2 * 0.6)
               + neon * smoothstep(0.2, 0.9, e) * pulse * mix(0.5, 1.8, uParam2);
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

private const val AURA_GLOW = PRELUDE + """
float3 agPal(float t) {
    return 0.5 + 0.5 * cos(6.28318 * (t + float3(0.0, 0.33, 0.67)));
}

half4 main(float2 coord) {
    float radius = min(uResolution.x, uResolution.y) * 0.02 * mix(0.5, 2.5, uParam1);
    float3 acc = float3(0.0);
    acc += float3(uImage.eval(clamp(coord + float2( radius, 0.0), float2(0.0), uResolution - 1.0)).rgb);
    acc += float3(uImage.eval(clamp(coord + float2(-radius, 0.0), float2(0.0), uResolution - 1.0)).rgb);
    acc += float3(uImage.eval(clamp(coord + float2(0.0,  radius), float2(0.0), uResolution - 1.0)).rgb);
    acc += float3(uImage.eval(clamp(coord + float2(0.0, -radius), float2(0.0), uResolution - 1.0)).rgb);
    float dg = radius * 0.7071;
    acc += float3(uImage.eval(clamp(coord + float2( dg,  dg), float2(0.0), uResolution - 1.0)).rgb);
    acc += float3(uImage.eval(clamp(coord + float2(-dg,  dg), float2(0.0), uResolution - 1.0)).rgb);
    acc += float3(uImage.eval(clamp(coord + float2( dg, -dg), float2(0.0), uResolution - 1.0)).rgb);
    acc += float3(uImage.eval(clamp(coord + float2(-dg, -dg), float2(0.0), uResolution - 1.0)).rgb);
    float3 halo = max(acc / 8.0 - 0.32, 0.0);
    float3 tint = agPal(fract(uTime * mix(0.02, 0.25, uParam3)));
    float3 src = float3(uImage.eval(coord).rgb);
    float3 col = src + halo * tint * mix(0.5, 2.2, uParam2);
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

private const val PORTAL = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float2 c = uCenter * uResolution;
    float minD = min(uResolution.x, uResolution.y);
    float2 d = (coord - c) / minD;
    float r = length(d) + 0.0001;
    float a = atan(d.y, d.x);
    float t = uTime * mix(0.4, 2.4, uParam3);
    float radius = mix(0.12, 0.42, uParam1);
    float ring = abs(r - radius);
    float inner = exp(-ring * 8.0) * uParam2;
    float na = a + inner * 5.0 + t * step(r, radius) * 0.6;
    float rr = r * mix(1.0, 0.6, inner);
    float2 p = c + float2(cos(na), sin(na)) * rr * minD;
    p = clamp(p, float2(0.0), uResolution - 1.0);
    float3 src = float3(uImage.eval(p).rgb);
    float glow = exp(-ring * 30.0);
    float n = fbm(float2(a * 3.0 + t * 2.0, r * 10.0 - t * 3.0));
    float3 col = src + float3(0.5, 0.3, 1.5) * glow * (0.7 + 0.6 * n) * 1.4;
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

private const val WARP_SPEED = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float2 c = uCenter * uResolution;
    float2 d = coord - c;
    float r = length(d) / min(uResolution.x, uResolution.y);
    float a = atan(d.y, d.x);
    float t = uTime * mix(0.4, 3.0, uParam3);
    float3 col = float3(uImage.eval(coord).rgb) * mix(1.0, 0.5, uParam2 * clamp(r, 0.0, 1.0));
    float streaks = mix(24.0, 80.0, uParam1);
    float pos = (a / 6.28318 + 0.5) * streaks;
    float cellA = floor(pos);
    float fa = fract(pos) - 0.5;
    float sd = hash21(float2(cellA, 11.7));
    float head = fract(sd * 5.0 + t * (0.4 + 0.8 * sd));
    float tail = fract(r * 1.3 - head);
    float lineM = exp(-fa * fa * 34.0);
    float streak = lineM * pow(1.0 - tail, 6.0) * smoothstep(0.04, 0.3, r);
    col += float3(0.8, 0.9, 1.3) * streak * mix(0.5, 1.9, uParam2);
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

private const val GALAXY = PRELUDE + NOISE_LIB + """
float2 gxRot(float2 p, float ang) {
    float cs = cos(ang);
    float sn = sin(ang);
    return float2(p.x * cs - p.y * sn, p.x * sn + p.y * cs);
}

float3 gxPal(float t) {
    return 0.5 + 0.5 * cos(6.28318 * (t + float3(0.0, 0.4, 0.7)));
}

half4 main(float2 coord) {
    float2 c = uCenter * uResolution;
    float minD = min(uResolution.x, uResolution.y);
    float2 d = (coord - c) / minD;
    float t = uTime * mix(0.02, 0.25, uParam3);
    float2 q = gxRot(d, t * 3.0 + length(d) * mix(2.0, 7.0, uParam1));
    float neb = fbm(q * 3.0 + float2(t * 2.0, 0.0));
    float neb2 = fbm(q * 6.0 - float2(0.0, t * 3.0));
    float mask = exp(-dot(d, d) * 3.0);
    float3 nc = gxPal(neb * 1.4) * neb * mask;
    nc += float3(1.2, 1.1, 0.9) * pow(neb2, 6.0) * mask * 2.0;
    float2 sp = floor(coord / max(uResolution.x * 0.012, 4.0));
    float star = step(0.985, hash21(sp)) * pow(hash21(sp + 7.7), 2.0);
    nc += float3(star) * mask * (0.5 + 0.5 * sin(uTime * 2.0 + hash21(sp) * 6.28));
    float3 src = float3(uImage.eval(coord).rgb);
    float3 overlay = clamp(nc * mix(0.4, 1.5, uParam2), 0.0, 1.0);
    float3 col = 1.0 - (1.0 - src) * (1.0 - overlay);
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

private const val OCEAN_WAVES = PRELUDE + NOISE_LIB + """
float3 owSmp(float2 p) {
    p = clamp(p, float2(0.0), uResolution - 1.0);
    return float3(uImage.eval(p).rgb);
}

half4 main(float2 coord) {
    float t = uTime * mix(0.3, 2.0, uParam3);
    float2 uv = coord / uResolution;
    float amp = uResolution.y * 0.02 * mix(0.3, 2.0, uParam1);
    float dy = sin(uv.x * 9.0 + t) * 0.5
             + sin(uv.x * 17.0 - t * 1.4) * 0.3
             + (fbm(float2(uv.x * 5.0, t * 0.5)) - 0.5);
    float2 p = coord + float2(sin(uv.y * 14.0 + t) * amp * 0.4, dy * amp);
    float3 col = owSmp(p);
    float foam = smoothstep(0.75, 1.0, fbm(float2(uv.x * 12.0, uv.y * 12.0 - t)) + dy * 0.3) * uParam2;
    col += foam * float3(0.5, 0.65, 0.7);
    col *= float3(0.85, 0.98, 1.05);
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

private const val RAINFALL = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float t = uTime * mix(2.0, 9.0, uParam3);
    float3 col = float3(uImage.eval(coord).rgb) * float3(0.85, 0.9, 1.0);
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float scale = mix(60.0, 160.0, uParam1) * (1.0 + fi * 0.5);
        float2 rp = float2(
            coord.x / uResolution.x * scale + fi * 13.7,
            coord.y / uResolution.y * scale * 0.35 + t * (1.2 + fi * 0.4)
        );
        float2 id = floor(rp);
        float2 f = fract(rp);
        float on = step(0.82, hash21(id));
        float drop = exp(-pow((f.x - 0.5) * 9.0, 2.0))
                   * smoothstep(0.0, 0.25, f.y) * smoothstep(1.0, 0.6, f.y) * on;
        col += drop * 0.22 * mix(0.4, 1.4, uParam2) / (1.0 + fi * 0.7);
    }
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

private const val SNOWFALL = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float t = uTime * mix(0.2, 1.4, uParam3);
    float3 col = float3(uImage.eval(coord).rgb);
    for (int i = 0; i < 3; i++) {
        float fl = float(i);
        float cell = uResolution.x / (mix(8.0, 26.0, uParam1) * (1.0 + fl * 0.7));
        float sway = sin(t * (1.3 + fl * 0.4) + fl * 2.1) * 0.3;
        float2 gp = coord / cell + float2(sway + fl * 5.3, t * (2.0 + fl) * 1.4);
        float2 id = floor(gp);
        float2 f = fract(gp);
        float2 pos = float2(0.2, 0.2) + 0.6 * float2(hash21(id), hash21(id + 6.1));
        float dd = length((f - pos) * cell);
        float sz = cell * mix(0.04, 0.12, uParam2) * (0.5 + 0.8 * hash21(id + 3.3));
        float flake = exp(-dd * dd / max(sz * sz, 0.5));
        col += float3(0.95, 0.97, 1.0) * flake * 0.9 / (1.0 + fl * 0.6);
    }
    col *= float3(0.94, 0.97, 1.04);
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

private const val FROST = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float2 uv = coord / uResolution;
    float border = min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y));
    float edge = 1.0 - smoothstep(0.0, mix(0.12, 0.5, uParam1), border);
    float n = fbm(coord / uResolution.x * mix(14.0, 46.0, uParam2));
    float crystal = smoothstep(0.42, 0.72, n * (0.55 + edge * 0.8));
    float mask = clamp(edge * (0.35 + 0.65 * crystal) * mix(0.4, 1.0, uParam3), 0.0, 1.0);
    float2 refr = (float2(vnoise(coord * 0.05), vnoise(coord * 0.05 + 21.3)) - 0.5) * 14.0 * mask;
    float2 p = clamp(coord + refr, float2(0.0), uResolution - 1.0);
    float3 col = float3(uImage.eval(p).rgb);
    col = mix(col, col * float3(0.82, 0.92, 1.12) + float3(0.22, 0.28, 0.35) * mask, mask);
    col += crystal * edge * 0.15;
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

private const val MIST = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float2 uv = coord / uResolution;
    float t = uTime * mix(0.05, 0.5, uParam3);
    float m = fbm(uv * 3.0 + float2(t * 2.0, t * 0.4));
    m += 0.5 * fbm(uv * 7.0 - float2(t * 1.2, 0.0));
    m = smoothstep(0.5, 1.3, m) * mix(0.25, 0.9, uParam1);
    float weight = mix(1.0, uv.y, uParam2);
    float3 src = float3(uImage.eval(coord).rgb);
    float3 col = mix(src, float3(0.82, 0.86, 0.92), clamp(m * weight, 0.0, 1.0));
    return half4(half3(col), 1.0);
}
"""

private const val THUNDER_SKY = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float spd = mix(1.0, 6.0, uParam3);
    float tick = floor(uTime * spd);
    float sub = fract(uTime * spd);
    float on = step(0.8, hash21(float2(tick, 5.5)));
    float fl = on * (0.4 + 0.6 * hash21(float2(tick, 9.1))) * exp(-sub * 7.0);
    float3 src = float3(uImage.eval(coord).rgb);
    float3 grade = src * mix(float3(1.0), float3(0.55, 0.62, 0.78), uParam1);
    float3 col = grade * (1.0 + fl * 1.7) + fl * 0.18;
    float2 uv = coord / uResolution - 0.5;
    col *= 1.0 - dot(uv, uv) * 1.1 * uParam2;
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

private const val BUBBLES = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float t = uTime * mix(0.1, 0.8, uParam3);
    float2 totalOff = float2(0.0);
    float highlight = 0.0;
    for (int i = 0; i < 2; i++) {
        float fl = float(i);
        float cell = min(uResolution.x, uResolution.y) / (mix(4.0, 14.0, uParam1) * (1.0 + fl * 0.5));
        float2 gp = coord / cell + float2(fl * 3.7, t * (1.5 + fl));
        float2 id = floor(gp);
        float2 f = fract(gp);
        float2 pos = float2(0.3, 0.3) + 0.4 * float2(hash21(id), hash21(id + 8.8));
        float2 delta = f - pos;
        float dd = length(delta);
        float rad = mix(0.12, 0.3, hash21(id + 2.9));
        float rim = smoothstep(rad, rad * 0.55, dd) * smoothstep(rad * 0.2, rad * 0.7, dd);
        totalOff += -delta * rim * cell * 1.6 * mix(0.3, 1.2, uParam2);
        highlight += exp(-pow((dd - rad * 0.75) * 14.0 / rad, 2.0)) * 0.10
                   + exp(-length((delta + float2(rad * 0.3, rad * 0.35)) * 9.0 / rad)) * 0.22 * step(dd, rad);
    }
    float2 p = clamp(coord + totalOff, float2(0.0), uResolution - 1.0);
    float3 col = float3(uImage.eval(p).rgb) + highlight;
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

private const val WHIRLPOOL = PRELUDE + """
half4 main(float2 coord) {
    float2 c = uCenter * uResolution;
    float2 d = coord - c;
    float r = length(d);
    float maxR = min(uResolution.x, uResolution.y) * 0.75;
    float fall = smoothstep(maxR, 0.0, r);
    float ang = atan(d.y, d.x)
              + fall * mix(1.0, 6.0, uParam1) * 0.4
              + uTime * mix(0.2, 2.2, uParam3) * fall;
    float rr = r * (1.0 - 0.25 * uParam2 * fall);
    float2 p = c + float2(cos(ang), sin(ang)) * rr;
    p = clamp(p, float2(0.0), uResolution - 1.0);
    float3 col = float3(uImage.eval(p).rgb);
    col *= 1.0 - 0.2 * fall * uParam2;
    return half4(half3(col), 1.0);
}
"""

private const val CAUSTIC_POOL = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float t = uTime * mix(0.15, 1.4, uParam3);
    float sc = mix(0.006, 0.03, uParam1);
    float n1 = vnoise(coord * sc + float2(t, t * 0.7));
    float n2 = vnoise(coord * sc * 1.7 - float2(t * 0.8, t * 0.5) + 13.7);
    float r1 = 1.0 - abs(2.0 * n1 - 1.0);
    float r2 = 1.0 - abs(2.0 * n2 - 1.0);
    float ca = pow(r1 * r2, 3.0);
    float2 warp = (float2(n1, n2) - 0.5) * 10.0 * uParam2;
    float2 p = clamp(coord + warp, float2(0.0), uResolution - 1.0);
    float3 col = float3(uImage.eval(p).rgb);
    col += ca * float3(1.0, 0.98, 0.9) * mix(0.3, 1.3, uParam2);
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

private const val MONSOON = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float t = uTime * mix(3.0, 10.0, uParam3);
    float2 uv = coord / uResolution;
    float3 col = float3(uImage.eval(coord).rgb) * float3(0.72, 0.78, 0.88);
    float fog = smoothstep(0.4, 1.2, fbm(uv * 2.5 + float2(t * 0.15, 0.0))) * uParam2 * 0.7;
    col = mix(col, float3(0.7, 0.75, 0.82), fog);
    float slant = 0.35;
    for (int i = 0; i < 2; i++) {
        float fi = float(i);
        float scale = mix(80.0, 180.0, uParam1) * (1.0 + fi * 0.6);
        float2 rp = float2(
            (uv.x + uv.y * slant) * scale + fi * 17.1,
            uv.y * scale * 0.3 + t * (1.6 + fi * 0.5)
        );
        float2 id = floor(rp);
        float2 f = fract(rp);
        float on = step(0.7, hash21(id));
        float drop = exp(-pow((f.x - 0.5) * 8.0, 2.0))
                   * smoothstep(0.0, 0.2, f.y) * smoothstep(1.0, 0.55, f.y) * on;
        col += drop * 0.20 / (1.0 + fi * 0.6);
    }
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

private const val FAIRY_DUST = PRELUDE + NOISE_LIB + """
float3 fdPal(float t) {
    return 0.5 + 0.5 * cos(6.28318 * (t + float3(0.0, 0.33, 0.67)));
}

half4 main(float2 coord) {
    float t = uTime * mix(0.1, 0.8, uParam3);
    float3 col = float3(uImage.eval(coord).rgb);
    for (int i = 0; i < 2; i++) {
        float fl = float(i);
        float cell = uResolution.x / (mix(10.0, 34.0, uParam1) * (1.0 + fl * 0.5));
        float2 gp = coord / cell + float2(t * (1.0 + fl * 0.7), -t * (0.6 + fl * 0.4));
        float2 id = floor(gp);
        float2 f = fract(gp);
        float2 pos = float2(0.25, 0.25) + 0.5 * float2(hash21(id), hash21(id + 5.5));
        float2 dd = (f - pos) * cell;
        float tw = 0.4 + 0.6 * sin(uTime * 4.0 + hash21(id + 1.1) * 6.28);
        float sz = cell * 0.05 * (0.5 + hash21(id + 8.4));
        float star = exp(-(abs(dd.x) + abs(dd.y)) / max(sz, 0.5)) * tw;
        float3 tint = fdPal(hash21(id + 3.6) + uTime * mix(0.02, 0.2, uParam2));
        col += tint * star * 1.4;
    }
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

private const val DREAM_BLUR = PRELUDE + """
half4 main(float2 coord) {
    float breath = 0.6 + 0.4 * sin(uTime * mix(0.4, 2.4, uParam3));
    float radius = min(uResolution.x, uResolution.y) * 0.018 * mix(0.3, 2.2, uParam1) * breath;
    float3 acc = float3(uImage.eval(coord).rgb);
    acc += float3(uImage.eval(clamp(coord + float2( radius, 0.0), float2(0.0), uResolution - 1.0)).rgb);
    acc += float3(uImage.eval(clamp(coord + float2(-radius, 0.0), float2(0.0), uResolution - 1.0)).rgb);
    acc += float3(uImage.eval(clamp(coord + float2(0.0,  radius), float2(0.0), uResolution - 1.0)).rgb);
    acc += float3(uImage.eval(clamp(coord + float2(0.0, -radius), float2(0.0), uResolution - 1.0)).rgb);
    float dg = radius * 0.7071;
    acc += float3(uImage.eval(clamp(coord + float2( dg,  dg), float2(0.0), uResolution - 1.0)).rgb);
    acc += float3(uImage.eval(clamp(coord + float2(-dg,  dg), float2(0.0), uResolution - 1.0)).rgb);
    acc += float3(uImage.eval(clamp(coord + float2( dg, -dg), float2(0.0), uResolution - 1.0)).rgb);
    acc += float3(uImage.eval(clamp(coord + float2(-dg, -dg), float2(0.0), uResolution - 1.0)).rgb);
    float3 soft = acc / 9.0;
    float3 src = float3(uImage.eval(coord).rgb);
    float3 col = mix(src, soft, 0.65);
    col += max(soft - 0.55, 0.0) * mix(0.4, 1.6, uParam2) * breath;
    col = col * 0.96 + 0.04;
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

private const val ASTRAL = PRELUDE + """
half4 main(float2 coord) {
    float t = uTime * mix(0.3, 2.0, uParam3);
    float dist = mix(4.0, 46.0, uParam1) * (uResolution.x / 1400.0 + 0.4);
    float2 o1 = float2(cos(t), sin(t)) * dist;
    float2 o2 = float2(cos(t + 2.094), sin(t + 2.094)) * dist;
    float2 o3 = float2(cos(t + 4.188), sin(t + 4.188)) * dist;
    float r = uImage.eval(clamp(coord + o1, float2(0.0), uResolution - 1.0)).r;
    float g = uImage.eval(clamp(coord + o2, float2(0.0), uResolution - 1.0)).g;
    float b = uImage.eval(clamp(coord + o3, float2(0.0), uResolution - 1.0)).b;
    float3 ghost = float3(r, g, b);
    float3 src = float3(uImage.eval(coord).rgb);
    float3 col = mix(src, max(src, ghost), uParam2);
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

private const val SPIRIT_VEIL = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float2 uv = coord / uResolution;
    float t = uTime * mix(0.08, 0.7, uParam3);
    float flow = fbm(uv * 3.0 + float2(t, -t * 0.6));
    float band = sin((uv.x * mix(2.0, 7.0, uParam1) + flow * 2.5 + uv.y * 0.8 + t) * 6.28318);
    float veil = smoothstep(0.35, 1.0, band) * mix(0.15, 0.6, uParam2);
    float2 warp = float2(flow - 0.5, fbm(uv * 3.0 + 9.1) - 0.5) * 12.0;
    float2 p = clamp(coord + warp * veil * 2.0, float2(0.0), uResolution - 1.0);
    float3 src = float3(uImage.eval(p).rgb);
    float3 pearl = float3(0.85, 0.9, 1.05);
    float3 col = 1.0 - (1.0 - src) * (1.0 - pearl * veil);
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

private const val ENCHANTED = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float2 uv = coord / uResolution;
    float t = uTime * mix(0.05, 0.5, uParam3);
    float3 src = float3(uImage.eval(coord).rgb);
    float3 col = src * mix(float3(1.0), float3(0.85, 1.0, 0.8), 0.4);
    float beamP = pow(0.5 + 0.5 * sin((uv.x * 4.0 - uv.y * 1.6 + fbm(uv * 2.0 + t) * 1.5) * 3.14159), 4.0);
    float beams = beamP * (1.0 - uv.y * 0.7) * mix(0.2, 1.0, uParam1);
    col += beams * float3(1.0, 0.95, 0.6);
    float cell = uResolution.x / 16.0;
    float2 gp = coord / cell + float2(t * 1.5, -t);
    float2 id = floor(gp);
    float2 f = fract(gp);
    float2 pos = float2(0.3, 0.3) + 0.4 * float2(hash21(id), hash21(id + 7.2));
    float dd = length((f - pos) * cell);
    float mote = exp(-dd * dd / 8.0) * (0.4 + 0.6 * sin(uTime * 2.5 + hash21(id) * 6.28));
    col += mote * float3(1.0, 0.9, 0.5) * mix(0.3, 1.2, uParam2);
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

/** Fantasy pack 1 registry. */
object FantasyEffects1 {
    val all: List<ShaderEffect> = listOf(
        ShaderEffect(
            id = "inferno", name = "Inferno", tagline = "rising flames",
            params = listOf(ShaderParam("Height", 0.5f), ShaderParam("Intensity", 0.6f), ShaderParam("Speed", 0.5f)),
            agsl = INFERNO, animated = true,
            accentStart = 0xFFFF6B00, accentEnd = 0xFF8B0000,
        ),
        ShaderEffect(
            id = "plasmaorb", name = "Plasma Orb", tagline = "energy sphere",
            params = listOf(ShaderParam("Size", 0.45f), ShaderParam("Energy", 0.6f), ShaderParam("Speed", 0.5f)),
            agsl = PLASMA_ORB, animated = true, positionable = true,
            accentStart = 0xFF6A5CFF, accentEnd = 0xFFD14BFF,
        ),
        ShaderEffect(
            id = "electricstorm", name = "Electric Storm", tagline = "lightning strikes",
            params = listOf(ShaderParam("Thickness", 0.4f), ShaderParam("Frequency", 0.6f), ShaderParam("Speed", 0.5f)),
            agsl = ELECTRIC_STORM, animated = true,
            accentStart = 0xFFB7C8FF, accentEnd = 0xFF2B3A9E,
        ),
        ShaderEffect(
            id = "solarflare", name = "Solar Flare", tagline = "god rays",
            params = listOf(ShaderParam("Reach", 0.55f), ShaderParam("Intensity", 0.55f), ShaderParam("Speed", 0.4f)),
            agsl = SOLAR_FLARE, animated = true, positionable = true,
            accentStart = 0xFFFFC53D, accentEnd = 0xFFFF6B00,
        ),
        ShaderEffect(
            id = "emberdust", name = "Ember Dust", tagline = "floating sparks",
            params = listOf(ShaderParam("Density", 0.5f), ShaderParam("Glow", 0.6f), ShaderParam("Speed", 0.45f)),
            agsl = EMBER_DUST, animated = true,
            accentStart = 0xFFFF8A3D, accentEnd = 0xFF3D1A05,
        ),
        ShaderEffect(
            id = "neonpulse", name = "Neon Pulse", tagline = "breathing contours",
            params = listOf(ShaderParam("Sensitivity", 0.55f), ShaderParam("Glow", 0.6f), ShaderParam("Pulse", 0.5f)),
            agsl = NEON_PULSE, animated = true,
            accentStart = 0xFF00FFC2, accentEnd = 0xFFFF00E5,
        ),
        ShaderEffect(
            id = "auraglow", name = "Aura Glow", tagline = "kirlian halo",
            params = listOf(ShaderParam("Radius", 0.5f), ShaderParam("Strength", 0.55f), ShaderParam("Cycle", 0.4f)),
            agsl = AURA_GLOW, animated = true,
            accentStart = 0xFF9BE15D, accentEnd = 0xFF7A3DFF,
        ),
        ShaderEffect(
            id = "portal", name = "Portal", tagline = "arcane gateway",
            params = listOf(ShaderParam("Size", 0.5f), ShaderParam("Twist", 0.6f), ShaderParam("Speed", 0.5f)),
            agsl = PORTAL, animated = true, positionable = true,
            accentStart = 0xFF7A3DFF, accentEnd = 0xFF1A0B4E,
        ),
        ShaderEffect(
            id = "warpspeed", name = "Warp Speed", tagline = "hyperspace jump",
            params = listOf(ShaderParam("Density", 0.5f), ShaderParam("Brightness", 0.6f), ShaderParam("Speed", 0.55f)),
            agsl = WARP_SPEED, animated = true, positionable = true,
            accentStart = 0xFFB7C8FF, accentEnd = 0xFF0A0F3D,
        ),
        ShaderEffect(
            id = "galaxy", name = "Galaxy", tagline = "spiral nebula",
            params = listOf(ShaderParam("Spin", 0.5f), ShaderParam("Intensity", 0.55f), ShaderParam("Speed", 0.35f)),
            agsl = GALAXY, animated = true, positionable = true,
            accentStart = 0xFF6A5CFF, accentEnd = 0xFFFF8AD1,
        ),
        ShaderEffect(
            id = "oceanwaves", name = "Ocean Waves", tagline = "rolling sea",
            params = listOf(ShaderParam("Waves", 0.5f), ShaderParam("Foam", 0.5f), ShaderParam("Speed", 0.5f)),
            agsl = OCEAN_WAVES, animated = true,
            accentStart = 0xFF00B4D8, accentEnd = 0xFF023E8A,
        ),
        ShaderEffect(
            id = "rainfall", name = "Rainfall", tagline = "falling rain",
            params = listOf(ShaderParam("Density", 0.5f), ShaderParam("Visibility", 0.55f), ShaderParam("Speed", 0.5f)),
            agsl = RAINFALL, animated = true,
            accentStart = 0xFF88A9C3, accentEnd = 0xFF2B3A4E,
        ),
        ShaderEffect(
            id = "snowfall", name = "Snowfall", tagline = "gentle flurry",
            params = listOf(ShaderParam("Density", 0.5f), ShaderParam("Size", 0.5f), ShaderParam("Speed", 0.4f)),
            agsl = SNOWFALL, animated = true,
            accentStart = 0xFFE8F1FF, accentEnd = 0xFF7A94B8,
        ),
        ShaderEffect(
            id = "frost", name = "Frost", tagline = "frozen window",
            params = listOf(ShaderParam("Spread", 0.5f), ShaderParam("Detail", 0.5f), ShaderParam("Opacity", 0.6f)),
            agsl = FROST,
            accentStart = 0xFFBFE3FF, accentEnd = 0xFF3D6B8C,
        ),
        ShaderEffect(
            id = "mist", name = "Mist", tagline = "drifting fog",
            params = listOf(ShaderParam("Density", 0.5f), ShaderParam("Ground", 0.5f), ShaderParam("Speed", 0.35f)),
            agsl = MIST, animated = true,
            accentStart = 0xFFC9D2DE, accentEnd = 0xFF5A6B7E,
        ),
        ShaderEffect(
            id = "thundersky", name = "Thunder Sky", tagline = "storm flashes",
            params = listOf(ShaderParam("Mood", 0.55f), ShaderParam("Vignette", 0.5f), ShaderParam("Frequency", 0.5f)),
            agsl = THUNDER_SKY, animated = true,
            accentStart = 0xFF4E5A78, accentEnd = 0xFF10141F,
        ),
        ShaderEffect(
            id = "bubbles", name = "Bubbles", tagline = "rising spheres",
            params = listOf(ShaderParam("Amount", 0.5f), ShaderParam("Refraction", 0.55f), ShaderParam("Speed", 0.4f)),
            agsl = BUBBLES, animated = true,
            accentStart = 0xFF8AD9FF, accentEnd = 0xFF1E5A8C,
        ),
        ShaderEffect(
            id = "whirlpool", name = "Whirlpool", tagline = "spinning vortex",
            params = listOf(ShaderParam("Twist", 0.5f), ShaderParam("Pull", 0.5f), ShaderParam("Speed", 0.45f)),
            agsl = WHIRLPOOL, animated = true, positionable = true,
            accentStart = 0xFF48CAE4, accentEnd = 0xFF03045E,
        ),
        ShaderEffect(
            id = "causticpool", name = "Caustic Pool", tagline = "dancing light",
            params = listOf(ShaderParam("Scale", 0.5f), ShaderParam("Intensity", 0.55f), ShaderParam("Speed", 0.5f)),
            agsl = CAUSTIC_POOL, animated = true,
            accentStart = 0xFFFFF3B0, accentEnd = 0xFF0096C7,
        ),
        ShaderEffect(
            id = "monsoon", name = "Monsoon", tagline = "storm curtain",
            params = listOf(ShaderParam("Rain", 0.55f), ShaderParam("Fog", 0.5f), ShaderParam("Speed", 0.5f)),
            agsl = MONSOON, animated = true,
            accentStart = 0xFF6B8CA8, accentEnd = 0xFF1F2D3D,
        ),
        ShaderEffect(
            id = "fairydust", name = "Fairy Dust", tagline = "drifting twinkles",
            params = listOf(ShaderParam("Density", 0.5f), ShaderParam("Magic", 0.5f), ShaderParam("Speed", 0.4f)),
            agsl = FAIRY_DUST, animated = true,
            accentStart = 0xFFFFD1F2, accentEnd = 0xFF8A3DFF,
        ),
        ShaderEffect(
            id = "dreamblur", name = "Dream Blur", tagline = "breathing haze",
            params = listOf(ShaderParam("Softness", 0.5f), ShaderParam("Glow", 0.55f), ShaderParam("Breath", 0.5f)),
            agsl = DREAM_BLUR, animated = true,
            accentStart = 0xFFF2D5FF, accentEnd = 0xFF9A7AA0,
        ),
        ShaderEffect(
            id = "astral", name = "Astral", tagline = "orbiting ghosts",
            params = listOf(ShaderParam("Distance", 0.45f), ShaderParam("Mix", 0.6f), ShaderParam("Speed", 0.4f)),
            agsl = ASTRAL, animated = true,
            accentStart = 0xFFB39DDB, accentEnd = 0xFF311B92,
        ),
        ShaderEffect(
            id = "spiritveil", name = "Spirit Veil", tagline = "silk apparitions",
            params = listOf(ShaderParam("Bands", 0.5f), ShaderParam("Opacity", 0.5f), ShaderParam("Speed", 0.4f)),
            agsl = SPIRIT_VEIL, animated = true,
            accentStart = 0xFFE8EAF6, accentEnd = 0xFF5C6BC0,
        ),
        ShaderEffect(
            id = "enchanted", name = "Enchanted", tagline = "forest light",
            params = listOf(ShaderParam("Beams", 0.5f), ShaderParam("Motes", 0.5f), ShaderParam("Speed", 0.35f)),
            agsl = ENCHANTED, animated = true,
            accentStart = 0xFFB8E15D, accentEnd = 0xFF1B4332,
        ),
    )
}
