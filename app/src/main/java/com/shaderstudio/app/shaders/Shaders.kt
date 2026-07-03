package com.shaderstudio.app.shaders

/**
 * AGSL effect library. Every image effect shares the same uniform interface:
 *
 *   uImage      – source content (the composable layer in preview, the bitmap on export)
 *   uResolution – size of the surface in pixels
 *   uTime       – seconds, monotonically increasing (only animated effects use it)
 *   uParam1..3  – normalized 0..1 knobs, mapped to effect-specific ranges inside AGSL
 *
 * Keeping parameters resolution-relative inside the shaders guarantees the
 * full-resolution export looks identical to the on-screen preview.
 */

data class ShaderParam(
    val label: String,
    val default: Float,
)

data class ShaderEffect(
    val id: String,
    val name: String,
    val tagline: String,
    val params: List<ShaderParam>,
    val agsl: String?,
    val animated: Boolean = false,
    val accentStart: Long = 0xFF4A5BF2,
    val accentEnd: Long = 0xFFB36BFF,
)

private const val PRELUDE = """
uniform shader uImage;
uniform float2 uResolution;
uniform float uTime;
uniform float uParam1;
uniform float uParam2;
uniform float uParam3;
"""

private const val NOISE_LIB = """
float hash21(float2 p) {
    p = fract(p * float2(123.34, 345.45));
    p += dot(p, p + 34.345);
    return fract(p.x * p.y);
}

float vnoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float a = hash21(i);
    float b = hash21(i + float2(1.0, 0.0));
    float c = hash21(i + float2(0.0, 1.0));
    float d = hash21(i + float2(1.0, 1.0));
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(float2 p) {
    float v = 0.0;
    float amp = 0.55;
    for (int i = 0; i < 5; i++) {
        v += amp * vnoise(p);
        p = p * 2.03 + float2(11.3, 7.9);
        amp *= 0.5;
    }
    return v;
}
"""

private const val DOT_MATRIX = PRELUDE + """
half4 main(float2 coord) {
    float cells = mix(26.0, 150.0, uParam1);
    float cell = uResolution.x / cells;
    float2 center = (floor(coord / cell) + 0.5) * cell;
    float4 src = float4(uImage.eval(center));
    float lum = dot(src.rgb, float3(0.2126, 0.7152, 0.0722));
    float radius = cell * (0.10 + 0.42 * sqrt(lum));
    float d = distance(coord, center);
    float aa = max(cell * 0.09, 1.0);
    float m = 1.0 - smoothstep(radius - aa, radius + aa, d);
    float3 led = src.rgb * m;
    float glow = exp(-d * d / (cell * cell * 0.85)) * lum * uParam2;
    led += src.rgb * glow * 0.9;
    float4 orig = float4(uImage.eval(coord));
    float3 outCol = mix(orig.rgb, led, uParam3);
    return half4(half3(clamp(outCol, 0.0, 1.0)), 1.0);
}
"""

private const val FLUTED_GLASS = PRELUDE + """
half4 main(float2 coord) {
    float ribs = mix(12.0, 90.0, uParam1);
    float w = uResolution.x / ribs;
    float local = fract(coord.x / w) - 0.5;
    float disp = local * w * mix(0.8, 7.0, uParam2);
    float sx = clamp(coord.x + disp, 0.0, uResolution.x - 1.0);
    float4 c = float4(uImage.eval(float2(sx, coord.y)));
    float edge = smoothstep(0.5, 0.22, abs(local));
    float shade = mix(1.0, 0.30 + 0.70 * edge, uParam3);
    c.rgb *= shade;
    float spec = smoothstep(0.16, 0.0, abs(local + 0.17)) * 0.22 * uParam3;
    c.rgb += spec;
    return half4(half3(clamp(c.rgb, 0.0, 1.0)), 1.0);
}
"""

private const val LIQUID_SKY = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float2 uv = coord / uResolution;
    float t = uTime * mix(0.03, 0.40, uParam2);
    float2 q = float2(fbm(uv * 3.0 + t), fbm(uv * 3.0 - t * 0.7 + 5.2));
    float2 r = float2(fbm(uv * 3.0 + q * 3.5 + float2(1.7, 9.2) + t * 0.4),
                      fbm(uv * 3.0 + q * 3.5 + float2(8.3, 2.8)));
    float f = fbm(uv * 3.0 + r * 3.0);
    float3 c1 = float3(0.05, 0.09, 0.45);
    float3 c2 = float3(1.00, 0.55, 0.22);
    float3 c3 = float3(0.45, 0.16, 0.85);
    float3 grad = mix(c1, c2, clamp(f * f * 2.6, 0.0, 1.0));
    grad = mix(grad, c3, clamp(q.x * q.y * 1.6, 0.0, 1.0));
    float2 warp = (q - 0.5) * uResolution.x * 0.08 * uParam1;
    float wx = clamp(coord.x + warp.x, 0.0, uResolution.x - 1.0);
    float wy = clamp(coord.y + warp.y, 0.0, uResolution.y - 1.0);
    float4 src = float4(uImage.eval(float2(wx, wy)));
    float3 screenB = 1.0 - (1.0 - src.rgb) * (1.0 - grad);
    float3 outCol = mix(src.rgb, screenB, uParam3 * 0.85);
    return half4(half3(clamp(outCol, 0.0, 1.0)), 1.0);
}
"""

private const val GLITCH = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float amt = uParam1;
    float t = floor(uTime * mix(3.0, 18.0, uParam2));
    float rowH = max(4.0, uResolution.y / 36.0);
    float row = floor(coord.y / rowH);
    float n = hash21(float2(row, t));
    float jitter = (n - 0.5) * 2.0;
    float big = step(0.82, hash21(float2(t + 3.7, row * 0.13))) * jitter;
    float dx = (jitter * 8.0 + big * uResolution.x * 0.08) * amt;
    float cx = clamp(coord.x + dx, 0.0, uResolution.x - 1.0);
    float ca = mix(1.0, 20.0, uParam3) * (0.4 + 0.6 * abs(jitter)) * (0.35 + 0.65 * amt);
    float r = uImage.eval(float2(clamp(cx + ca, 0.0, uResolution.x - 1.0), coord.y)).r;
    float g = uImage.eval(float2(cx, coord.y)).g;
    float b = uImage.eval(float2(clamp(cx - ca, 0.0, uResolution.x - 1.0), coord.y)).b;
    float flick = 1.0 - 0.08 * amt * sin(coord.y * 0.9 + uTime * 30.0);
    float3 outCol = float3(r, g, b) * flick;
    return half4(half3(clamp(outCol, 0.0, 1.0)), 1.0);
}
"""

private const val MOSAIC = PRELUDE + """
half4 main(float2 coord) {
    float cells = mix(150.0, 18.0, uParam1);
    float cell = uResolution.x / cells;
    float2 center = (floor(coord / cell) + 0.5) * cell;
    float4 c = float4(uImage.eval(center));
    float levels = mix(32.0, 3.0, uParam2);
    float3 poster = floor(c.rgb * levels + 0.5) / levels;
    float4 orig = float4(uImage.eval(coord));
    float3 outCol = mix(orig.rgb, poster, uParam3);
    return half4(half3(clamp(outCol, 0.0, 1.0)), 1.0);
}
"""

private const val VHS = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float2 c = coord;
    c.x += sin(coord.y * 0.013 + uTime * 2.4) * 3.0 * uParam1
         + sin(coord.y * 0.130 - uTime * 7.0) * 1.2 * uParam1;
    c.x = clamp(c.x, 0.0, uResolution.x - 1.0);
    float ca = 1.5 + 2.5 * uParam1;
    float r = uImage.eval(float2(clamp(c.x + ca, 0.0, uResolution.x - 1.0), c.y)).r;
    float g = uImage.eval(c).g;
    float b = uImage.eval(float2(clamp(c.x - ca, 0.0, uResolution.x - 1.0), c.y)).b;
    float3 col = float3(r, g, b);
    float lineH = max(2.0, uResolution.y / 220.0);
    float scan = 1.0 - uParam2 * 0.45 * (0.5 + 0.5 * sin(coord.y * 3.14159 / lineH));
    col *= scan;
    float grain = (hash21(coord * 0.7 + float2(fract(uTime) * 91.7, fract(uTime * 0.7) * 33.3)) - 0.5) * uParam3 * 0.35;
    col += grain;
    float2 uv = coord / uResolution - 0.5;
    col *= 1.0 - dot(uv, uv) * 0.9 * uParam2;
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

private const val DUOTONE = PRELUDE + """
float3 hue2rgb(float h) {
    h = fract(h);
    float r = abs(h * 6.0 - 3.0) - 1.0;
    float g = 2.0 - abs(h * 6.0 - 2.0);
    float b = 2.0 - abs(h * 6.0 - 4.0);
    return clamp(float3(r, g, b), 0.0, 1.0);
}

half4 main(float2 coord) {
    float4 src = float4(uImage.eval(coord));
    float lum = dot(src.rgb, float3(0.2126, 0.7152, 0.0722));
    lum = smoothstep(0.02, 0.98, lum);
    float3 shadow = hue2rgb(uParam1) * 0.22;
    float3 highTone = mix(hue2rgb(uParam2), float3(1.0), 0.35);
    float3 duo = mix(shadow, highTone, lum);
    float3 outCol = mix(src.rgb, duo, uParam3);
    return half4(half3(clamp(outCol, 0.0, 1.0)), 1.0);
}
"""

private const val RIPPLE = PRELUDE + """
half4 main(float2 coord) {
    float2 center = uResolution * 0.5;
    float2 d = coord - center;
    float len = length(d) + 0.001;
    float freq = mix(0.004, 0.045, uParam1);
    float amp = mix(2.0, 26.0, uParam2) * smoothstep(0.0, uResolution.x * 0.25, len);
    float phase = len * freq * 6.28318 - uTime * mix(1.0, 6.0, uParam3);
    float wave = sin(phase);
    float2 dir = d / len;
    float2 sc = coord + dir * wave * amp;
    sc.x = clamp(sc.x, 0.0, uResolution.x - 1.0);
    sc.y = clamp(sc.y, 0.0, uResolution.y - 1.0);
    float4 c = float4(uImage.eval(sc));
    c.rgb += wave * 0.06 * uParam2;
    return half4(half3(clamp(c.rgb, 0.0, 1.0)), 1.0);
}
"""

private const val KALEIDOSCOPE = PRELUDE + """
half4 main(float2 coord) {
    float2 c = uResolution * 0.5;
    float2 d = coord - c;
    float seg = floor(mix(3.0, 14.0, uParam1) + 0.5);
    float slice = 6.28318 / seg;
    float ang = atan(d.y, d.x) + uParam2 * 6.28318;
    float r = length(d) / mix(1.0, 2.0, uParam3);
    ang = mod(ang, slice);
    ang = abs(ang - slice * 0.5);
    float2 p = c + float2(cos(ang), sin(ang)) * r;
    p.x = clamp(p.x, 0.0, uResolution.x - 1.0);
    p.y = clamp(p.y, 0.0, uResolution.y - 1.0);
    return uImage.eval(p);
}
"""

private const val SWIRL = PRELUDE + """
half4 main(float2 coord) {
    float2 c = uResolution * 0.5;
    float2 d = coord - c;
    float r = length(d);
    float maxR = min(uResolution.x, uResolution.y) * mix(0.25, 0.85, uParam2);
    float strength = (uParam1 * 2.0 - 1.0) * 4.0;
    float ang = atan(d.y, d.x) + strength * smoothstep(maxR, 0.0, r);
    float2 p = c + float2(cos(ang), sin(ang)) * r;
    p.x = clamp(p.x, 0.0, uResolution.x - 1.0);
    p.y = clamp(p.y, 0.0, uResolution.y - 1.0);
    float4 fx = float4(uImage.eval(p));
    float4 orig = float4(uImage.eval(coord));
    return half4(half3(mix(orig.rgb, fx.rgb, uParam3)), 1.0);
}
"""

private const val LENS = PRELUDE + """
half4 main(float2 coord) {
    float2 c = uResolution * 0.5;
    float half_min = min(uResolution.x, uResolution.y) * 0.5;
    float2 d = (coord - c) / half_min;
    float r = length(d) + 0.0001;
    float k = mix(-0.75, 1.4, uParam1);
    float radius = mix(0.5, 1.6, uParam2);
    float bulge = exp(-(r * r) / (radius * radius));
    float newR = r * (1.0 - k * bulge * 0.45);
    float2 p = c + (d / r) * newR * half_min;
    p.x = clamp(p.x, 0.0, uResolution.x - 1.0);
    p.y = clamp(p.y, 0.0, uResolution.y - 1.0);
    float4 fx = float4(uImage.eval(p));
    float4 orig = float4(uImage.eval(coord));
    return half4(half3(mix(orig.rgb, fx.rgb, uParam3)), 1.0);
}
"""

private const val TILT_SHIFT = PRELUDE + """
half4 main(float2 coord) {
    float focusY = uResolution.y * uParam2;
    float band = uResolution.y * 0.16;
    float t = smoothstep(0.0, band * 2.2, abs(coord.y - focusY));
    float radius = t * min(uResolution.x, uResolution.y) * 0.018 * mix(0.2, 2.2, uParam1);
    float3 acc = float3(uImage.eval(coord).rgb);
    acc += float3(uImage.eval(coord + float2( radius, 0.0)).rgb);
    acc += float3(uImage.eval(coord + float2(-radius, 0.0)).rgb);
    acc += float3(uImage.eval(coord + float2(0.0,  radius)).rgb);
    acc += float3(uImage.eval(coord + float2(0.0, -radius)).rgb);
    float dg = radius * 0.7071;
    acc += float3(uImage.eval(coord + float2( dg,  dg)).rgb);
    acc += float3(uImage.eval(coord + float2(-dg,  dg)).rgb);
    acc += float3(uImage.eval(coord + float2( dg, -dg)).rgb);
    acc += float3(uImage.eval(coord + float2(-dg, -dg)).rgb);
    float3 blur = acc / 9.0;
    blur *= 1.0 + 0.12 * uParam3 * t;
    float4 orig = float4(uImage.eval(coord));
    float3 outCol = mix(orig.rgb, blur, step(0.001, radius));
    return half4(half3(clamp(outCol, 0.0, 1.0)), 1.0);
}
"""

private const val CRT = PRELUDE + """
half4 main(float2 coord) {
    float2 uv = coord / uResolution * 2.0 - 1.0;
    float curve = mix(0.0, 0.35, uParam1);
    uv *= 1.0 + curve * dot(uv, uv) * 0.35;
    float2 p = (uv * 0.5 + 0.5) * uResolution;
    float inside = step(0.0, p.x) * step(p.x, uResolution.x - 1.0)
                 * step(0.0, p.y) * step(p.y, uResolution.y - 1.0);
    p.x = clamp(p.x, 0.0, uResolution.x - 1.0);
    p.y = clamp(p.y, 0.0, uResolution.y - 1.0);
    float3 col = float3(uImage.eval(p).rgb);
    float m = mod(floor(coord.x), 3.0);
    float3 mask = float3(step(m, 0.5),
                         step(0.5, m) * step(m, 1.5),
                         step(1.5, m));
    col *= mix(float3(1.0), mask * 2.4 + 0.15, uParam2 * 0.8);
    float scan = 1.0 - 0.28 * uParam2 * (0.5 + 0.5 * sin(p.y * 3.14159 / 2.0));
    col *= scan;
    float vig = 1.0 - dot(uv, uv) * 0.35 * uParam3;
    col *= vig * inside;
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

private const val HALFTONE_PRINT = PRELUDE + """
half4 main(float2 coord) {
    float cell = uResolution.x / mix(40.0, 160.0, uParam1);
    float ang = uParam2 * 1.5708;
    float cs = cos(ang);
    float sn = sin(ang);
    float2 rc = float2(coord.x * cs - coord.y * sn, coord.x * sn + coord.y * cs);
    float2 rCenter = (floor(rc / cell) + 0.5) * cell;
    float2 center = float2(rCenter.x * cs + rCenter.y * sn, -rCenter.x * sn + rCenter.y * cs);
    center.x = clamp(center.x, 0.0, uResolution.x - 1.0);
    center.y = clamp(center.y, 0.0, uResolution.y - 1.0);
    float lum = dot(float3(uImage.eval(center).rgb), float3(0.2126, 0.7152, 0.0722));
    lum = clamp((lum - 0.5) * mix(0.8, 1.8, uParam3) + 0.5, 0.0, 1.0);
    float radius = cell * 0.68 * sqrt(1.0 - lum);
    float d = distance(rc, rCenter);
    float aa = max(cell * 0.08, 1.0);
    float ink = 1.0 - smoothstep(radius - aa, radius + aa, d);
    float3 paper = float3(0.97, 0.95, 0.90);
    float3 inkCol = float3(0.10, 0.09, 0.11);
    return half4(half3(mix(paper, inkCol, ink)), 1.0);
}
"""

private const val CROSSHATCH = PRELUDE + """
half4 main(float2 coord) {
    float lum = dot(float3(uImage.eval(coord).rgb), float3(0.2126, 0.7152, 0.0722));
    lum = clamp((lum - 0.5) * mix(0.8, 1.6, uParam3) + 0.5, 0.0, 1.0);
    float spacing = uResolution.x / mix(60.0, 180.0, uParam1);
    float width = spacing * mix(0.14, 0.4, uParam2);
    float aa = max(1.0, width * 0.5);
    float l1 = abs(fract((coord.x + coord.y) / spacing) - 0.5) * spacing;
    float l2 = abs(fract((coord.x - coord.y) / spacing) - 0.5) * spacing;
    float l3 = abs(fract(coord.x / spacing) - 0.5) * spacing;
    float l4 = abs(fract(coord.y / spacing) - 0.5) * spacing;
    float ink = 0.0;
    ink += (1.0 - smoothstep(width - aa, width + aa, l1)) * step(lum, 0.8);
    ink += (1.0 - smoothstep(width - aa, width + aa, l2)) * step(lum, 0.6);
    ink += (1.0 - smoothstep(width - aa, width + aa, l3)) * step(lum, 0.4);
    ink += (1.0 - smoothstep(width - aa, width + aa, l4)) * step(lum, 0.22);
    ink = clamp(ink, 0.0, 1.0);
    float3 paper = float3(0.96, 0.94, 0.88);
    float3 inkCol = float3(0.13, 0.11, 0.10);
    return half4(half3(mix(paper, inkCol, ink)), 1.0);
}
"""

private const val OIL_FLOW = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float scale = mix(0.02, 0.006, uParam2);
    float2 flow = float2(vnoise(coord * scale) - 0.5, vnoise(coord * scale + 31.7) - 0.5) * 2.0;
    float len = min(uResolution.x, uResolution.y) * 0.02 * mix(0.3, 2.5, uParam1);
    float3 acc = float3(0.0);
    for (int i = 0; i < 8; i++) {
        float t = (float(i) / 7.0 - 0.5) * 2.0;
        float2 p = coord + flow * len * t;
        p.x = clamp(p.x, 0.0, uResolution.x - 1.0);
        p.y = clamp(p.y, 0.0, uResolution.y - 1.0);
        acc += float3(uImage.eval(p).rgb);
    }
    float3 smear = acc / 8.0;
    float4 orig = float4(uImage.eval(coord));
    return half4(half3(mix(orig.rgb, smear, uParam3)), 1.0);
}
"""

private const val NEON_EDGE = PRELUDE + """
float lumAt(float2 p, float2 res) {
    p.x = clamp(p.x, 0.0, res.x - 1.0);
    p.y = clamp(p.y, 0.0, res.y - 1.0);
    return dot(float3(uImage.eval(p).rgb), float3(0.2126, 0.7152, 0.0722));
}

float3 hueColor(float h) {
    h = fract(h);
    float r = abs(h * 6.0 - 3.0) - 1.0;
    float g = 2.0 - abs(h * 6.0 - 2.0);
    float b = 2.0 - abs(h * 6.0 - 4.0);
    return clamp(float3(r, g, b), 0.0, 1.0);
}

half4 main(float2 coord) {
    float o = max(1.0, uResolution.x / 900.0);
    float tl = lumAt(coord + float2(-o, -o), uResolution);
    float tc = lumAt(coord + float2(0.0, -o), uResolution);
    float tr = lumAt(coord + float2(o, -o), uResolution);
    float ml = lumAt(coord + float2(-o, 0.0), uResolution);
    float mr = lumAt(coord + float2(o, 0.0), uResolution);
    float bl = lumAt(coord + float2(-o, o), uResolution);
    float bc = lumAt(coord + float2(0.0, o), uResolution);
    float br = lumAt(coord + float2(o, o), uResolution);
    float gx = (tr + 2.0 * mr + br) - (tl + 2.0 * ml + bl);
    float gy = (bl + 2.0 * bc + br) - (tl + 2.0 * tc + tr);
    float g = length(float2(gx, gy)) * mix(0.8, 4.0, uParam1);
    float edge = smoothstep(0.15, 0.75, g);
    float3 neon = hueColor(uParam3);
    float3 src = float3(uImage.eval(coord).rgb);
    float3 outCol = src * 0.12 * (1.0 - uParam2)
                  + neon * (edge + pow(edge, 0.4) * 0.55 * uParam2);
    return half4(half3(clamp(outCol, 0.0, 1.0)), 1.0);
}
"""

private const val EMBOSS = PRELUDE + """
half4 main(float2 coord) {
    float ang = uParam2 * 6.28318;
    float depth = mix(1.0, 6.0, uParam1) * max(1.0, uResolution.x / 1200.0);
    float2 dir = float2(cos(ang), sin(ang)) * depth;
    float2 p1 = coord + dir;
    float2 p2 = coord - dir;
    p1.x = clamp(p1.x, 0.0, uResolution.x - 1.0);
    p1.y = clamp(p1.y, 0.0, uResolution.y - 1.0);
    p2.x = clamp(p2.x, 0.0, uResolution.x - 1.0);
    p2.y = clamp(p2.y, 0.0, uResolution.y - 1.0);
    float w1 = dot(float3(uImage.eval(p1).rgb), float3(0.2126, 0.7152, 0.0722));
    float w2 = dot(float3(uImage.eval(p2).rgb), float3(0.2126, 0.7152, 0.0722));
    float e = clamp(0.5 + (w1 - w2) * 2.4, 0.0, 1.0);
    float3 relief = float3(e);
    float4 orig = float4(uImage.eval(coord));
    return half4(half3(mix(orig.rgb, relief, uParam3)), 1.0);
}
"""

private const val THERMAL = PRELUDE + """
half4 main(float2 coord) {
    float3 src = float3(uImage.eval(coord).rgb);
    float lum = dot(src, float3(0.2126, 0.7152, 0.0722));
    lum = clamp((lum - 0.5) * mix(0.8, 2.0, uParam1) + 0.5 + (uParam2 - 0.5) * 0.6, 0.0, 1.0);
    float3 c0 = float3(0.01, 0.0, 0.15);
    float3 c1 = float3(0.35, 0.0, 0.55);
    float3 c2 = float3(0.90, 0.15, 0.15);
    float3 c3 = float3(1.00, 0.65, 0.05);
    float3 c4 = float3(1.00, 1.00, 0.75);
    float3 heat = mix(c0, c1, smoothstep(0.00, 0.30, lum));
    heat = mix(heat, c2, smoothstep(0.30, 0.55, lum));
    heat = mix(heat, c3, smoothstep(0.55, 0.78, lum));
    heat = mix(heat, c4, smoothstep(0.78, 1.00, lum));
    return half4(half3(mix(src, heat, uParam3)), 1.0);
}
"""

private const val SOLARIZE = PRELUDE + """
half4 main(float2 coord) {
    float3 src = float3(uImage.eval(coord).rgb);
    float t = mix(0.2, 0.8, uParam1);
    float soft = mix(0.02, 0.3, 1.0 - uParam2);
    float3 fold = mix(src, 1.0 - src, smoothstep(t - soft, t + soft, src));
    return half4(half3(mix(src, fold, uParam3)), 1.0);
}
"""

private const val POSTER_POP = PRELUDE + """
half4 main(float2 coord) {
    float3 src = float3(uImage.eval(coord).rgb);
    float levels = mix(7.0, 2.6, uParam1);
    float3 poster = floor(src * levels + 0.5) / levels;
    float lum = dot(poster, float3(0.2126, 0.7152, 0.0722));
    float sat = mix(1.0, 2.4, uParam2);
    float3 pop = clamp(float3(lum) + (poster - float3(lum)) * sat, 0.0, 1.0);
    return half4(half3(mix(src, pop, uParam3)), 1.0);
}
"""

private const val SCAN_SLICE = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float slices = floor(mix(6.0, 44.0, uParam1) + 0.5);
    float sw = uResolution.x / slices;
    float idx = floor(coord.x / sw);
    float seed = floor(uParam3 * 24.0);
    float n = hash21(float2(idx * 1.31, seed + 7.7)) - 0.5;
    float off = n * uResolution.y * 0.30 * uParam2;
    float y = clamp(coord.y + off, 0.0, uResolution.y - 1.0);
    return uImage.eval(float2(coord.x, y));
}
"""

private const val MIRROR = PRELUDE + """
half4 main(float2 coord) {
    float axis = uResolution.x * mix(0.25, 0.75, uParam1);
    float feather = uResolution.x * mix(0.001, 0.12, uParam2);
    float mx = 2.0 * axis - coord.x;
    mx = clamp(mx, 0.0, uResolution.x - 1.0);
    float4 mirrored = float4(uImage.eval(float2(mx, coord.y)));
    float4 orig = float4(uImage.eval(coord));
    float m = smoothstep(axis - feather, axis + feather, coord.x);
    float3 folded = mix(orig.rgb, mirrored.rgb, m);
    return half4(half3(mix(orig.rgb, folded, uParam3)), 1.0);
}
"""

private const val CHROMA_ZOOM = PRELUDE + """
half4 main(float2 coord) {
    float2 c = uResolution * 0.5;
    float2 d = coord - c;
    float falloff = smoothstep(0.0, min(uResolution.x, uResolution.y) * mix(0.2, 0.7, uParam2), length(d));
    float s = mix(0.0, 0.09, uParam1) * falloff;
    float3 acc = float3(0.0);
    for (int i = 0; i < 6; i++) {
        float t = float(i) / 5.0;
        float2 pr = c + d * (1.0 - s * (0.5 + t));
        float2 pg = c + d * (1.0 - s * t * 0.8);
        float2 pb = c + d * (1.0 - s * t * 0.4);
        acc.r += uImage.eval(clamp(pr, float2(0.0), uResolution - 1.0)).r;
        acc.g += uImage.eval(clamp(pg, float2(0.0), uResolution - 1.0)).g;
        acc.b += uImage.eval(clamp(pb, float2(0.0), uResolution - 1.0)).b;
    }
    float3 fx = acc / 6.0;
    float3 orig = float3(uImage.eval(coord).rgb);
    return half4(half3(mix(orig, fx, uParam3)), 1.0);
}
"""

private const val RAIN_GLASS = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float cell = min(uResolution.x, uResolution.y) / mix(6.0, 22.0, uParam2);
    float2 gp = coord / cell;
    float2 id = floor(gp);
    float2 f = fract(gp);
    float speed = mix(0.02, 0.35, uParam3);
    float2 drop = float2(
        0.2 + 0.6 * hash21(id),
        fract(hash21(id * 1.7 + 3.3) - uTime * speed * (0.3 + 0.7 * hash21(id + 7.3)))
    );
    float2 delta = f - drop;
    float d = length(delta);
    float r = 0.10 + 0.16 * hash21(id + 13.1);
    float inDrop = smoothstep(r, r * 0.25, d);
    float2 offset = -delta * inDrop * cell * 2.2 * mix(0.2, 1.4, uParam1);
    float2 frost = (float2(vnoise(coord * 0.35), vnoise(coord * 0.35 + 17.0)) - 0.5) * 3.0 * uParam1;
    float2 p = coord + offset + frost * (1.0 - inDrop);
    p.x = clamp(p.x, 0.0, uResolution.x - 1.0);
    p.y = clamp(p.y, 0.0, uResolution.y - 1.0);
    float3 col = float3(uImage.eval(p).rgb);
    col += inDrop * (1.0 - smoothstep(r * 0.25, r, d)) * 0.08;
    col *= 1.0 - 0.10 * smoothstep(r, r * 0.8, d) * inDrop;
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

private const val HEX_PIXEL = PRELUDE + """
half4 main(float2 coord) {
    float s = uResolution.x / mix(90.0, 16.0, uParam1);
    float2 grid = float2(1.5 * s, 1.7320508 * s);
    float2 a = mod(coord, grid) - grid * 0.5;
    float2 b = mod(coord - grid * 0.5, grid) - grid * 0.5;
    float2 center = (dot(a, a) < dot(b, b)) ? coord - a : coord - b;
    float2 cc = center;
    cc.x = clamp(cc.x, 0.0, uResolution.x - 1.0);
    cc.y = clamp(cc.y, 0.0, uResolution.y - 1.0);
    float3 col = float3(uImage.eval(cc).rgb);
    float d = distance(coord, center);
    float border = smoothstep(s * (0.92 - uParam2 * 0.45), s * (1.0 - uParam2 * 0.45), d);
    col *= 1.0 - border * 0.85;
    float3 orig = float3(uImage.eval(coord).rgb);
    return half4(half3(mix(orig, col, uParam3)), 1.0);
}
"""

private const val LINESCREEN = PRELUDE + """
half4 main(float2 coord) {
    float lines = mix(30.0, 140.0, uParam1);
    float lh = uResolution.y / lines;
    float2 sp = float2(coord.x, (floor(coord.y / lh) + 0.5) * lh);
    sp.y = clamp(sp.y, 0.0, uResolution.y - 1.0);
    float3 src = float3(uImage.eval(sp).rgb);
    float lum = dot(src, float3(0.2126, 0.7152, 0.0722));
    lum = clamp((lum - 0.5) * mix(0.8, 1.8, uParam2) + 0.5, 0.0, 1.0);
    float th = (1.0 - lum) * lh * 0.48;
    float dy = abs(fract(coord.y / lh) - 0.5) * lh;
    float aa = max(1.0, lh * 0.10);
    float ink = 1.0 - smoothstep(th - aa, th + aa, dy);
    float3 paper = float3(0.97, 0.95, 0.91);
    float3 inkCol = mix(float3(0.08, 0.07, 0.09), src * 0.45, uParam3);
    return half4(half3(mix(paper, inkCol, ink)), 1.0);
}
"""

private const val WEAVE = PRELUDE + """
half4 main(float2 coord) {
    float s = uResolution.x / mix(120.0, 30.0, uParam1);
    float2 cellId = floor(coord / s);
    float checker = mod(cellId.x + cellId.y, 2.0);
    float2 f = fract(coord / s);
    float acrossH = abs(f.y - 0.5) * 2.0;
    float acrossV = abs(f.x - 0.5) * 2.0;
    float across = mix(acrossV, acrossH, checker);
    float thread = cos(across * 1.5708);
    float shade = mix(1.0, 0.45 + 0.75 * thread, uParam2);
    float2 sc = (cellId + 0.5) * s;
    sc.x = clamp(sc.x, 0.0, uResolution.x - 1.0);
    sc.y = clamp(sc.y, 0.0, uResolution.y - 1.0);
    float3 cellCol = float3(uImage.eval(sc).rgb);
    float3 woven = cellCol * shade;
    float3 orig = float3(uImage.eval(coord).rgb);
    return half4(half3(mix(orig, woven, uParam3)), 1.0);
}
"""

private const val CHROMA_WAVE = PRELUDE + """
half4 main(float2 coord) {
    float amp = mix(1.0, 20.0, uParam1) * (uResolution.x / 1200.0 + 0.5);
    float freq = mix(0.004, 0.03, uParam2);
    float t = uTime * mix(0.5, 4.5, uParam3);
    float xr = coord.x + sin(coord.y * freq + t) * amp;
    float xg = coord.x + sin(coord.y * freq + t + 2.094) * amp;
    float xb = coord.x + sin(coord.y * freq + t + 4.188) * amp;
    float r = uImage.eval(float2(clamp(xr, 0.0, uResolution.x - 1.0), coord.y)).r;
    float g = uImage.eval(float2(clamp(xg, 0.0, uResolution.x - 1.0), coord.y)).g;
    float b = uImage.eval(float2(clamp(xb, 0.0, uResolution.x - 1.0), coord.y)).b;
    return half4(half3(clamp(float3(r, g, b), 0.0, 1.0)), 1.0);
}
"""

private const val BLOOM = PRELUDE + """
half4 main(float2 coord) {
    float radius = mix(3.0, 42.0, uParam1) * (uResolution.x / 1400.0 + 0.4);
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
    float3 blur = acc / 8.0;
    float thr = mix(0.15, 0.75, uParam3);
    float3 bright = max(blur - thr, 0.0) / max(1.0 - thr, 0.001);
    float3 src = float3(uImage.eval(coord).rgb);
    float3 outCol = src + bright * mix(0.3, 2.0, uParam2);
    return half4(half3(clamp(outCol, 0.0, 1.0)), 1.0);
}
"""

private const val NIGHT_VISION = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float3 src = float3(uImage.eval(coord).rgb);
    float lum = dot(src, float3(0.2126, 0.7152, 0.0722));
    lum = pow(clamp(lum * mix(1.2, 3.0, uParam1), 0.0, 1.0), 0.75);
    float3 green = float3(0.15, 1.0, 0.30) * lum;
    float grain = (hash21(coord * 0.9 + float2(fract(uTime) * 73.1, fract(uTime * 1.3) * 41.7)) - 0.5)
                * mix(0.0, 0.35, uParam2);
    green += grain;
    float scan = 1.0 - 0.12 * (0.5 + 0.5 * sin(coord.y * 1.2));
    green *= scan;
    float2 uv = coord / uResolution - 0.5;
    float vig = 1.0 - smoothstep(0.25, 0.72, length(uv)) * mix(0.3, 1.0, uParam3);
    green *= vig;
    return half4(half3(clamp(green, 0.0, 1.0)), 1.0);
}
"""

private const val POP_DOTS = PRELUDE + """
float dotMask(float2 coord, float ang, float cell, float2 res, float value) {
    float cs = cos(ang);
    float sn = sin(ang);
    float2 rc = float2(coord.x * cs - coord.y * sn, coord.x * sn + coord.y * cs);
    float2 rCenter = (floor(rc / cell) + 0.5) * cell;
    float radius = cell * 0.62 * sqrt(clamp(value, 0.0, 1.0));
    float d = distance(rc, rCenter);
    float aa = max(cell * 0.09, 1.0);
    return 1.0 - smoothstep(radius - aa, radius + aa, d);
}

float2 gridSample(float2 coord, float ang, float cell) {
    float cs = cos(ang);
    float sn = sin(ang);
    float2 rc = float2(coord.x * cs - coord.y * sn, coord.x * sn + coord.y * cs);
    float2 rCenter = (floor(rc / cell) + 0.5) * cell;
    return float2(rCenter.x * cs + rCenter.y * sn, -rCenter.x * sn + rCenter.y * cs);
}

half4 main(float2 coord) {
    float cell = uResolution.x / mix(30.0, 110.0, uParam1);
    float spread = mix(0.1, 0.6, uParam2);
    float a1 = 0.262 + spread * 0.0;
    float a2 = 0.262 + spread * 0.5;
    float a3 = 0.262 + spread * 1.0;
    float2 s1 = clamp(gridSample(coord, a1, cell), float2(0.0), uResolution - 1.0);
    float2 s2 = clamp(gridSample(coord, a2, cell), float2(0.0), uResolution - 1.0);
    float2 s3 = clamp(gridSample(coord, a3, cell), float2(0.0), uResolution - 1.0);
    float cVal = 1.0 - uImage.eval(s1).r;
    float mVal = 1.0 - uImage.eval(s2).g;
    float yVal = 1.0 - uImage.eval(s3).b;
    float mc = dotMask(coord, a1, cell, uResolution, cVal);
    float mm = dotMask(coord, a2, cell, uResolution, mVal);
    float my = dotMask(coord, a3, cell, uResolution, yVal);
    float3 col = float3(1.0);
    col *= 1.0 - mc * (1.0 - float3(0.0, 0.68, 0.94));
    col *= 1.0 - mm * (1.0 - float3(0.93, 0.10, 0.55));
    col *= 1.0 - my * (1.0 - float3(1.00, 0.90, 0.05));
    float3 orig = float3(uImage.eval(coord).rgb);
    return half4(half3(mix(orig, col, uParam3)), 1.0);
}
"""

private const val AURORA = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float2 uv = coord / uResolution;
    float t = uTime * mix(0.05, 0.5, uParam3);
    float curtain = fbm(float2(uv.x * 4.0 + t, uv.y * 1.2 - t * 0.6));
    float band = fbm(float2(uv.x * 2.0 - t * 0.7, 3.7));
    float height = exp(-uv.y * mix(5.0, 1.2, uParam1));
    float glow = curtain * band * height * 2.4;
    float3 aur = float3(0.05, 0.9, 0.45) * glow
               + float3(0.15, 0.25, 0.9) * glow * glow * 0.9
               + float3(0.7, 0.15, 0.8) * pow(glow, 3.0) * 0.5;
    float3 src = float3(uImage.eval(coord).rgb);
    float3 outCol = src * (1.0 - 0.25 * clamp(glow, 0.0, 1.0) * uParam2) + aur * mix(0.2, 1.4, uParam2);
    return half4(half3(clamp(outCol, 0.0, 1.0)), 1.0);
}
"""

private const val TOON = PRELUDE + """
float toonLum(float2 p, float2 res) {
    p.x = clamp(p.x, 0.0, res.x - 1.0);
    p.y = clamp(p.y, 0.0, res.y - 1.0);
    return dot(float3(uImage.eval(p).rgb), float3(0.2126, 0.7152, 0.0722));
}

half4 main(float2 coord) {
    float3 src = float3(uImage.eval(coord).rgb);
    float levels = mix(8.0, 3.0, uParam1);
    float3 cel = floor(src * levels + 0.5) / levels;
    float lum0 = dot(cel, float3(0.2126, 0.7152, 0.0722));
    cel = clamp(float3(lum0) + (cel - float3(lum0)) * 1.25, 0.0, 1.0);
    float o = max(1.0, uResolution.x / 900.0);
    float gl = toonLum(coord + float2(-o, 0.0), uResolution);
    float gr = toonLum(coord + float2(o, 0.0), uResolution);
    float gu = toonLum(coord + float2(0.0, -o), uResolution);
    float gd = toonLum(coord + float2(0.0, o), uResolution);
    float g = length(float2(gr - gl, gd - gu)) * mix(2.0, 8.0, uParam2);
    float edge = smoothstep(0.25, 0.6, g);
    float3 outCol = cel * (1.0 - edge * 0.85);
    return half4(half3(mix(src, outCol, uParam3)), 1.0);
}
"""

private const val ANAGLYPH = PRELUDE + """
half4 main(float2 coord) {
    float ang = uParam2 * 3.14159;
    float2 dir = float2(cos(ang), sin(ang)) * mix(2.0, 26.0, uParam1) * (uResolution.x / 1400.0 + 0.4);
    float2 pl = clamp(coord - dir, float2(0.0), uResolution - 1.0);
    float2 pr = clamp(coord + dir, float2(0.0), uResolution - 1.0);
    float r = uImage.eval(pl).r;
    float g = uImage.eval(pr).g;
    float b = uImage.eval(pr).b;
    float3 src = float3(uImage.eval(coord).rgb);
    return half4(half3(mix(src, float3(r, g, b), uParam3)), 1.0);
}
"""

private const val LOMO = PRELUDE + """
half4 main(float2 coord) {
    float3 src = float3(uImage.eval(coord).rgb);
    float3 c = mix(src, src * src * (3.0 - 2.0 * src), mix(0.3, 1.0, uParam1));
    float lum = dot(c, float3(0.2126, 0.7152, 0.0722));
    c = clamp(float3(lum) + (c - float3(lum)) * 1.35, 0.0, 1.0);
    c *= float3(1.06, 1.03, 0.90);
    float2 uv = coord / uResolution - 0.5;
    float vig = 1.0 - dot(uv, uv) * mix(0.4, 1.6, uParam2);
    c *= clamp(vig, 0.0, 1.0);
    return half4(half3(clamp(mix(src, c, uParam3), 0.0, 1.0)), 1.0);
}
"""

private const val OLD_FILM = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float3 src = float3(uImage.eval(coord).rgb);
    float lum = dot(src, float3(0.2126, 0.7152, 0.0722));
    float3 sepia = float3(1.0, 0.84, 0.62) * lum;
    float3 c = mix(src, sepia, mix(0.4, 1.0, uParam1));
    float tframe = floor(uTime * 9.0);
    float flick = 0.95 + 0.05 * hash21(float2(tframe, 3.7));
    c *= flick;
    float xq = floor(coord.x / max(2.0, uResolution.x / 480.0));
    float s = hash21(float2(xq * 1.13, tframe * 13.7));
    float scratch = step(1.0 - 0.006 * uParam2, s);
    c += scratch * 0.35;
    float dark = step(s, 0.004 * uParam2);
    c -= dark * 0.3;
    float speck = step(1.0 - 0.002 * uParam2, hash21(floor(coord / 9.0) + tframe * 0.31));
    c += speck * 0.5;
    float grain = (hash21(coord * 0.8 + float2(fract(uTime) * 67.3, fract(uTime * 1.7) * 41.1)) - 0.5) * uParam3 * 0.3;
    c += grain;
    float2 uv = coord / uResolution - 0.5;
    c *= 1.0 - dot(uv, uv) * 0.7;
    return half4(half3(clamp(c, 0.0, 1.0)), 1.0);
}
"""

private const val INFRARED = PRELUDE + """
half4 main(float2 coord) {
    float3 src = float3(uImage.eval(coord).rgb);
    float3 ir = float3(src.g * 1.35, src.r * 0.55, src.b * 0.75);
    float irLum = dot(ir, float3(0.2126, 0.7152, 0.0722));
    ir = mix(ir, float3(irLum * 1.2), 0.15);
    ir += pow(src.g, 2.0) * uParam2 * float3(0.9, 0.75, 0.7);
    float3 swapped = mix(src, ir, uParam1);
    return half4(half3(clamp(mix(src, swapped, uParam3), 0.0, 1.0)), 1.0);
}
"""

private const val PIXEL_SORT = PRELUDE + """
half4 main(float2 coord) {
    float3 src = float3(uImage.eval(coord).rgb);
    float thr = mix(0.78, 0.25, uParam1);
    float len = uResolution.y * 0.28 * uParam2;
    float3 best = src;
    for (int i = 1; i <= 8; i++) {
        float2 p = coord - float2(0.0, len * float(i) / 8.0);
        p.y = clamp(p.y, 0.0, uResolution.y - 1.0);
        float3 c = float3(uImage.eval(p).rgb);
        float l = dot(c, float3(0.2126, 0.7152, 0.0722));
        float m = step(thr, l) * (1.0 - float(i) / 10.0);
        best = max(best, c * m);
    }
    return half4(half3(mix(src, max(src, best), uParam3)), 1.0);
}
"""

private const val STAINED_GLASS = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float cell = uResolution.x / mix(8.0, 36.0, uParam1);
    float2 gp = coord / cell;
    float2 gi = floor(gp);
    float2 gf = fract(gp);
    float f1 = 8.0;
    float f2 = 8.0;
    float2 bestPt = gi;
    for (int dy = -1; dy <= 1; dy++) {
        for (int dx = -1; dx <= 1; dx++) {
            float2 nb = float2(float(dx), float(dy));
            float2 rnd = float2(hash21(gi + nb), hash21(gi + nb + 17.3));
            float2 pt = nb + rnd - gf;
            float d = dot(pt, pt);
            if (d < f1) {
                f2 = f1;
                f1 = d;
                bestPt = gi + nb + rnd;
            } else if (d < f2) {
                f2 = d;
            }
        }
    }
    float2 sp = clamp(bestPt * cell, float2(0.0), uResolution - 1.0);
    float3 col = float3(uImage.eval(sp).rgb);
    col *= 0.9 + 0.2 * hash21(bestPt * 3.1);
    float lead = 1.0 - smoothstep(0.0, mix(0.3, 0.06, uParam2), sqrt(f2) - sqrt(f1));
    col *= 1.0 - lead * 0.85;
    float3 src = float3(uImage.eval(coord).rgb);
    return half4(half3(mix(src, col, uParam3)), 1.0);
}
"""

private const val TRI_MOSAIC = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float s = uResolution.x / mix(60.0, 13.0, uParam1);
    float2 g = coord / s;
    float2 gi = floor(g);
    float2 gf = fract(g);
    float upper = step(gf.x + gf.y, 1.0);
    float2 triC = gi + mix(float2(0.6667, 0.6667), float2(0.3333, 0.3333), upper);
    float2 sp = clamp(triC * s, float2(0.0), uResolution - 1.0);
    float3 col = float3(uImage.eval(sp).rgb);
    float facet = 0.92 + 0.16 * hash21(gi * 3.7 + upper * 11.1);
    col *= mix(1.0, facet, uParam2);
    float3 src = float3(uImage.eval(coord).rgb);
    return half4(half3(clamp(mix(src, col, uParam3), 0.0, 1.0)), 1.0);
}
"""

private const val SPIN_BLUR = PRELUDE + """
half4 main(float2 coord) {
    float2 c = uResolution * 0.5;
    float2 d = coord - c;
    float fall = smoothstep(0.0, min(uResolution.x, uResolution.y) * 0.5, length(d));
    float total = mix(0.01, 0.16, uParam1) * mix(0.4, 1.0, fall) * mix(0.5, 1.5, uParam2);
    float3 acc = float3(0.0);
    for (int i = 0; i < 8; i++) {
        float a = (float(i) / 7.0 - 0.5) * total;
        float cs = cos(a);
        float sn = sin(a);
        float2 rd = float2(d.x * cs - d.y * sn, d.x * sn + d.y * cs);
        float2 p = clamp(c + rd, float2(0.0), uResolution - 1.0);
        acc += float3(uImage.eval(p).rgb);
    }
    float3 blur = acc / 8.0;
    float3 src = float3(uImage.eval(coord).rgb);
    return half4(half3(mix(src, blur, uParam3)), 1.0);
}
"""

private const val MOTION_BLUR = PRELUDE + """
half4 main(float2 coord) {
    float ang = uParam2 * 3.14159;
    float len = mix(3.0, 70.0, uParam1) * (uResolution.x / 1400.0 + 0.4);
    float2 dir = float2(cos(ang), sin(ang)) * len;
    float3 acc = float3(0.0);
    for (int i = 0; i < 8; i++) {
        float t = float(i) / 7.0 - 0.5;
        float2 p = clamp(coord + dir * t, float2(0.0), uResolution - 1.0);
        acc += float3(uImage.eval(p).rgb);
    }
    float3 blur = acc / 8.0;
    float3 src = float3(uImage.eval(coord).rgb);
    return half4(half3(mix(src, blur, uParam3)), 1.0);
}
"""

private const val LITTLE_PLANET = PRELUDE + """
half4 main(float2 coord) {
    float2 c = uResolution * 0.5;
    float2 d = coord - c;
    float r = length(d) / (min(uResolution.x, uResolution.y) * 0.5 * mix(1.5, 0.7, uParam1));
    float a = atan(d.y, d.x) / 6.28318 + 0.5 + uParam2;
    float sx = fract(a) * (uResolution.x - 1.0);
    float sy = clamp((1.0 - clamp(r, 0.0, 1.0)) * (uResolution.y - 1.0), 0.0, uResolution.y - 1.0);
    float3 col = float3(uImage.eval(float2(sx, sy)).rgb);
    float3 src = float3(uImage.eval(coord).rgb);
    return half4(half3(mix(src, col, uParam3)), 1.0);
}
"""

private const val QUAD_MIRROR = PRELUDE + """
half4 main(float2 coord) {
    float cx = uResolution.x * mix(0.25, 0.75, uParam1);
    float cy = uResolution.y * mix(0.25, 0.75, uParam2);
    float sx = clamp(cx - abs(coord.x - cx), 0.0, uResolution.x - 1.0);
    float sy = clamp(cy - abs(coord.y - cy), 0.0, uResolution.y - 1.0);
    float3 col = float3(uImage.eval(float2(sx, sy)).rgb);
    float3 src = float3(uImage.eval(coord).rgb);
    return half4(half3(mix(src, col, uParam3)), 1.0);
}
"""

private const val FLAG_WAVE = PRELUDE + """
half4 main(float2 coord) {
    float t = uTime * mix(0.5, 4.5, uParam3);
    float amp = uResolution.y * 0.02 * mix(0.2, 2.0, uParam1);
    float freq = mix(1.5, 8.0, uParam2) * 6.28318 / uResolution.x;
    float pin = 0.25 + 0.75 * coord.x / uResolution.x;
    float phase = coord.x * freq + t;
    float dy = sin(phase) * amp * pin;
    float dx = cos(phase * 0.7 + 1.3) * amp * 0.35 * pin;
    float2 p = clamp(coord + float2(dx, dy), float2(0.0), uResolution - 1.0);
    float3 col = float3(uImage.eval(p).rgb);
    col *= 0.85 + 0.22 * cos(phase) * pin;
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

private const val UNDERWATER = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float t = uTime * mix(0.1, 1.2, uParam3);
    float2 warp = float2(
        vnoise(coord * 0.008 + float2(t, t * 0.6)) - 0.5,
        vnoise(coord * 0.008 + float2(7.3 - t * 0.8, t)) - 0.5
    ) * uResolution.x * 0.035 * uParam1;
    float2 p = clamp(coord + warp, float2(0.0), uResolution - 1.0);
    float3 col = float3(uImage.eval(p).rgb);
    float n = vnoise(coord * 0.02 + float2(t * 2.0, t));
    float ridge = 1.0 - abs(2.0 * n - 1.0);
    float ca = pow(ridge, 4.0) * uParam2;
    col += ca * float3(0.35, 0.75, 0.85);
    col *= float3(0.72, 0.94, 1.06);
    col *= 1.0 - 0.35 * (coord.y / uResolution.y);
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

private const val HEAT_HAZE = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float t = uTime * mix(1.0, 6.0, uParam3);
    float scale = mix(0.05, 0.012, uParam2);
    float n1 = vnoise(coord * scale + float2(0.0, -t)) - 0.5;
    float n2 = vnoise(coord * scale + float2(31.7, -t * 1.3)) - 0.5;
    float amp = mix(1.0, 12.0, uParam1) * (uResolution.x / 1400.0 + 0.4);
    float2 p = clamp(coord + float2(n1, n2 * 0.5) * amp, float2(0.0), uResolution - 1.0);
    return uImage.eval(p);
}
"""

private const val DOUBLE_GHOST = PRELUDE + """
half4 main(float2 coord) {
    float ang = uParam2 * 6.28318;
    float dist = mix(4.0, 90.0, uParam1) * (uResolution.x / 1400.0 + 0.4);
    float2 p = clamp(coord + float2(cos(ang), sin(ang)) * dist, float2(0.0), uResolution - 1.0);
    float3 src = float3(uImage.eval(coord).rgb);
    float3 ghost = float3(uImage.eval(p).rgb);
    float3 screenB = 1.0 - (1.0 - src) * (1.0 - ghost * 0.85);
    return half4(half3(clamp(mix(src, screenB, uParam3), 0.0, 1.0)), 1.0);
}
"""

private const val MATRIX_RAIN = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float colW = uResolution.x / mix(16.0, 52.0, uParam1);
    float glyphH = colW * 1.35;
    float colId = floor(coord.x / colW);
    float speed = (0.15 + 0.85 * hash21(float2(colId, 3.3))) * mix(0.08, 0.7, uParam3);
    float head = fract(hash21(float2(colId, 7.7)) + uTime * speed);
    float ypos = coord.y / uResolution.y;
    float behind = fract(head - ypos);
    float trail = pow(1.0 - behind, 3.5);
    float flick = step(0.42, hash21(float2(colId * 1.7, floor(coord.y / glyphH) + floor(uTime * 8.0) * 0.31)));
    float cellEdge = smoothstep(0.0, 0.15, fract(coord.x / colW)) * smoothstep(1.0, 0.85, fract(coord.x / colW));
    float rain = trail * flick * cellEdge;
    float headGlow = smoothstep(0.045, 0.0, behind) * cellEdge;
    float3 src = float3(uImage.eval(coord).rgb);
    float3 rainCol = float3(0.2, 1.0, 0.42) * rain * mix(0.7, 1.8, uParam2)
                   + float3(0.75, 1.0, 0.82) * headGlow;
    float3 outCol = src * 0.30 + rainCol;
    return half4(half3(clamp(outCol, 0.0, 1.0)), 1.0);
}
"""

private const val SPARKLE = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float cell = uResolution.x / mix(12.0, 44.0, uParam1);
    float2 gi = floor(coord / cell);
    float2 gf = fract(coord / cell);
    float2 pos = float2(0.2, 0.2) + 0.6 * float2(hash21(gi), hash21(gi + 9.1));
    float phase = hash21(gi + 4.7) * 6.28318;
    float tw = 0.5 + 0.5 * sin(uTime * mix(1.0, 7.0, uParam3) + phase);
    float2 d = (gf - pos) * cell;
    float size = cell * mix(0.04, 0.16, uParam2) * (0.35 + 0.65 * tw);
    float cross1 = smoothstep(size, 0.0, abs(d.x)) * smoothstep(size * 7.0, 0.0, abs(d.y));
    float cross2 = smoothstep(size, 0.0, abs(d.y)) * smoothstep(size * 7.0, 0.0, abs(d.x));
    float sp = clamp(cross1 + cross2, 0.0, 1.0) * tw;
    float2 cp = clamp((gi + pos) * cell, float2(0.0), uResolution - 1.0);
    float lum = dot(float3(uImage.eval(cp).rgb), float3(0.2126, 0.7152, 0.0722));
    float gate = smoothstep(0.35, 0.85, lum);
    float3 src = float3(uImage.eval(coord).rgb);
    float3 outCol = src + float3(1.0, 0.98, 0.92) * sp * gate * 1.3;
    return half4(half3(clamp(outCol, 0.0, 1.0)), 1.0);
}
"""

private const val NEGATIVE = PRELUDE + """
half4 main(float2 coord) {
    float3 src = float3(uImage.eval(coord).rgb);
    float3 inv = 1.0 - src;
    float3 tinted = inv * mix(float3(1.0), float3(1.08, 0.95, 0.82), uParam2);
    float3 folded = mix(src, tinted, uParam1);
    return half4(half3(clamp(mix(src, folded, uParam3), 0.0, 1.0)), 1.0);
}
"""

private const val GAMEBOY = PRELUDE + """
half4 main(float2 coord) {
    float cell = uResolution.x / mix(200.0, 56.0, uParam1);
    float2 sp = clamp((floor(coord / cell) + 0.5) * cell, float2(0.0), uResolution - 1.0);
    float3 c = float3(uImage.eval(sp).rgb);
    float lum = dot(c, float3(0.2126, 0.7152, 0.0722));
    lum = clamp((lum - 0.5) * mix(0.9, 1.9, uParam2) + 0.5, 0.0, 1.0);
    float3 p0 = float3(0.06, 0.22, 0.06);
    float3 p1 = float3(0.19, 0.38, 0.19);
    float3 p2 = float3(0.55, 0.67, 0.06);
    float3 p3 = float3(0.61, 0.74, 0.06);
    float3 pal = mix(p0, p1, step(0.25, lum));
    pal = mix(pal, p2, step(0.5, lum));
    pal = mix(pal, p3, step(0.75, lum));
    float3 src = float3(uImage.eval(coord).rgb);
    return half4(half3(mix(src, pal, uParam3)), 1.0);
}
"""

private const val BIT_DITHER = PRELUDE + """
float bayer2(float2 m) {
    return 3.0 * m.y + 2.0 * m.x - 4.0 * m.x * m.y;
}

half4 main(float2 coord) {
    float s = mix(1.0, 7.0, uParam1) * max(1.0, uResolution.x / 1200.0);
    float2 p = floor(coord / s);
    float2 m1 = mod(p, 2.0);
    float2 m2 = mod(floor(p * 0.5), 2.0);
    float bayer = (4.0 * bayer2(m2) + bayer2(m1)) / 16.0;
    float2 sp = clamp((p + 0.5) * s, float2(0.0), uResolution - 1.0);
    float lum = dot(float3(uImage.eval(sp).rgb), float3(0.2126, 0.7152, 0.0722));
    float bw = step(bayer * 0.9 + 0.05 + (uParam2 - 0.5) * 0.5, lum);
    float3 col = mix(float3(0.05, 0.05, 0.07), float3(0.93, 0.93, 0.88), bw);
    float3 src = float3(uImage.eval(coord).rgb);
    return half4(half3(mix(src, col, uParam3)), 1.0);
}
"""

private const val WATERCOLOR = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float radius = uResolution.x * 0.004 * mix(0.5, 3.0, uParam1);
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
    float3 wash = acc / 9.0;
    float q = 6.0;
    wash = mix(wash, floor(wash * q + 0.5) / q, 0.55);
    float paper = 0.90 + 0.10 * vnoise(coord * 0.18);
    wash *= mix(1.0, paper, uParam2);
    float3 src = float3(uImage.eval(coord).rgb);
    float e = length(wash - src);
    wash *= 1.0 - clamp(e * 2.2, 0.0, 0.45) * uParam3;
    return half4(half3(clamp(wash, 0.0, 1.0)), 1.0);
}
"""

private const val PRISM_LEAK = PRELUDE + """
float3 leakHue(float h) {
    h = fract(h);
    float r = abs(h * 6.0 - 3.0) - 1.0;
    float g = 2.0 - abs(h * 6.0 - 2.0);
    float b = 2.0 - abs(h * 6.0 - 4.0);
    return clamp(float3(r, g, b), 0.0, 1.0);
}

half4 main(float2 coord) {
    float d = (coord.x + coord.y) / (uResolution.x + uResolution.y);
    float center = mix(0.15, 0.85, uParam1);
    float width = 0.04 + 0.22 * uParam2;
    float band = (d - center) / width;
    float leak = exp(-band * band);
    float3 rainbow = leakHue(clamp(band * 0.5 + 0.5, 0.0, 1.0) * 0.83);
    float3 src = float3(uImage.eval(coord).rgb);
    float3 outCol = 1.0 - (1.0 - src) * (1.0 - rainbow * leak * uParam3);
    return half4(half3(clamp(outCol, 0.0, 1.0)), 1.0);
}
"""

private const val TIME_SMEAR = PRELUDE + """
half4 main(float2 coord) {
    float prog = coord.y / uResolution.y;
    float pinch = uParam1 * prog;
    float sx = mix(coord.x, uResolution.x * 0.5, pinch * 0.85);
    sx += sin(coord.y * mix(0.004, 0.05, uParam2)) * uResolution.x * 0.025 * uParam1;
    sx = clamp(sx, 0.0, uResolution.x - 1.0);
    float3 col = float3(uImage.eval(float2(sx, coord.y)).rgb);
    float3 src = float3(uImage.eval(coord).rgb);
    return half4(half3(mix(src, col, uParam3)), 1.0);
}
"""

private const val FILM_FADE = PRELUDE + NOISE_LIB + """
half4 main(float2 coord) {
    float3 src = float3(uImage.eval(coord).rgb);
    float3 c = src * mix(1.0, 0.80, uParam1) + 0.12 * uParam1;
    float lum = dot(c, float3(0.2126, 0.7152, 0.0722));
    c = mix(c, float3(lum), 0.20 * uParam1);
    c *= mix(float3(1.0), float3(1.05, 1.0, 0.90), uParam1);
    float grain = (hash21(coord * 0.85 + float2(fract(uTime) * 53.7, fract(uTime * 1.9) * 29.3)) - 0.5) * uParam2 * 0.28;
    c += grain;
    float2 uv = coord / uResolution - 0.5;
    c *= 1.0 - dot(uv, uv) * 1.1 * uParam3;
    return half4(half3(clamp(c, 0.0, 1.0)), 1.0);
}
"""

/** Standalone animated background for the home screen hero (no input image). */
const val HERO_AGSL = """
uniform float2 uResolution;
uniform float uTime;
""" + NOISE_LIB + """
half4 main(float2 coord) {
    float2 uv = coord / uResolution;
    float t = uTime * 0.07;
    float2 q = float2(fbm(uv * 2.4 + t), fbm(uv * 2.4 - t + 3.1));
    float2 r = float2(fbm(uv * 2.4 + q * 3.0 + float2(1.7, 9.2) + t),
                      fbm(uv * 2.4 + q * 3.0 + float2(8.3, 2.8)));
    float f = fbm(uv * 2.4 + r * 2.6);
    float3 base = float3(0.016, 0.018, 0.045);
    float3 c1 = float3(0.10, 0.16, 0.75);
    float3 c2 = float3(1.00, 0.52, 0.20);
    float3 c3 = float3(0.52, 0.20, 0.95);
    float3 col = mix(base, c1, smoothstep(0.15, 0.85, f));
    col = mix(col, c2, smoothstep(0.45, 0.95, q.x * f) * 0.85);
    col = mix(col, c3, smoothstep(0.35, 0.9, r.y) * 0.6);
    col *= 0.55 + 0.45 * smoothstep(1.4, 0.2, length(uv - 0.5) * 2.0);
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

object ShaderEffects {
    val original = ShaderEffect(
        id = "original",
        name = "Original",
        tagline = "untouched",
        params = emptyList(),
        agsl = null,
        accentStart = 0xFF3A3F4C,
        accentEnd = 0xFF565D70,
    )

    val all: List<ShaderEffect> = listOf(
        original,
        ShaderEffect(
            id = "dotmatrix",
            name = "Dot Matrix",
            tagline = "LED halftone",
            params = listOf(
                ShaderParam("Density", 0.55f),
                ShaderParam("Glow", 0.55f),
                ShaderParam("Blend", 1.0f),
            ),
            agsl = DOT_MATRIX,
            accentStart = 0xFF19C2FF,
            accentEnd = 0xFF3B4FE0,
        ),
        ShaderEffect(
            id = "flutedglass",
            name = "Fluted Glass",
            tagline = "ribbed refraction",
            params = listOf(
                ShaderParam("Ribs", 0.45f),
                ShaderParam("Refraction", 0.5f),
                ShaderParam("Shading", 0.7f),
            ),
            agsl = FLUTED_GLASS,
            accentStart = 0xFFFF6A3D,
            accentEnd = 0xFFB3232A,
        ),
        ShaderEffect(
            id = "liquidsky",
            name = "Liquid Sky",
            tagline = "viral gradient",
            params = listOf(
                ShaderParam("Warp", 0.5f),
                ShaderParam("Speed", 0.35f),
                ShaderParam("Blend", 0.7f),
            ),
            agsl = LIQUID_SKY,
            animated = true,
            accentStart = 0xFF2331B8,
            accentEnd = 0xFFFF8A3D,
        ),
        ShaderEffect(
            id = "glitch",
            name = "Glitch",
            tagline = "chromatic rows",
            params = listOf(
                ShaderParam("Strength", 0.5f),
                ShaderParam("Speed", 0.4f),
                ShaderParam("Chroma", 0.5f),
            ),
            agsl = GLITCH,
            animated = true,
            accentStart = 0xFF00E5A0,
            accentEnd = 0xFFB36BFF,
        ),
        ShaderEffect(
            id = "mosaic",
            name = "Mosaic",
            tagline = "pixel poster",
            params = listOf(
                ShaderParam("Size", 0.5f),
                ShaderParam("Posterize", 0.35f),
                ShaderParam("Blend", 1.0f),
            ),
            agsl = MOSAIC,
            accentStart = 0xFFFFC53D,
            accentEnd = 0xFFFF5E7A,
        ),
        ShaderEffect(
            id = "vhs",
            name = "VHS",
            tagline = "retro tape",
            params = listOf(
                ShaderParam("Wobble", 0.45f),
                ShaderParam("Scanlines", 0.5f),
                ShaderParam("Grain", 0.4f),
            ),
            agsl = VHS,
            animated = true,
            accentStart = 0xFF7A5CFF,
            accentEnd = 0xFF00C2D7,
        ),
        ShaderEffect(
            id = "duotone",
            name = "Duotone",
            tagline = "two-tone poster",
            params = listOf(
                ShaderParam("Shadow", 0.68f),
                ShaderParam("Highlight", 0.08f),
                ShaderParam("Blend", 0.85f),
            ),
            agsl = DUOTONE,
            accentStart = 0xFF2E2A72,
            accentEnd = 0xFFFF9A3D,
        ),
        ShaderEffect(
            id = "ripple",
            name = "Ripple",
            tagline = "liquid waves",
            params = listOf(
                ShaderParam("Frequency", 0.4f),
                ShaderParam("Strength", 0.45f),
                ShaderParam("Speed", 0.5f),
            ),
            agsl = RIPPLE,
            animated = true,
            accentStart = 0xFF19A7FF,
            accentEnd = 0xFF6BE3FF,
        ),
        ShaderEffect(
            id = "kaleido",
            name = "Kaleidoscope",
            tagline = "mirror mandala",
            params = listOf(
                ShaderParam("Segments", 0.4f),
                ShaderParam("Rotate", 0.0f),
                ShaderParam("Zoom", 0.25f),
            ),
            agsl = KALEIDOSCOPE,
            accentStart = 0xFFFF3D8A,
            accentEnd = 0xFF8A3DFF,
        ),
        ShaderEffect(
            id = "swirl",
            name = "Swirl",
            tagline = "vortex twist",
            params = listOf(
                ShaderParam("Twist", 0.75f),
                ShaderParam("Radius", 0.6f),
                ShaderParam("Blend", 1.0f),
            ),
            agsl = SWIRL,
            accentStart = 0xFF3DDCFF,
            accentEnd = 0xFF2E5BFF,
        ),
        ShaderEffect(
            id = "lens",
            name = "Lens",
            tagline = "bulge & pinch",
            params = listOf(
                ShaderParam("Bulge", 0.75f),
                ShaderParam("Radius", 0.5f),
                ShaderParam("Blend", 1.0f),
            ),
            agsl = LENS,
            accentStart = 0xFF9BE15D,
            accentEnd = 0xFF00B588,
        ),
        ShaderEffect(
            id = "tiltshift",
            name = "Tilt Shift",
            tagline = "miniature focus",
            params = listOf(
                ShaderParam("Blur", 0.55f),
                ShaderParam("Focus", 0.5f),
                ShaderParam("Boost", 0.4f),
            ),
            agsl = TILT_SHIFT,
            accentStart = 0xFFFFB03D,
            accentEnd = 0xFFFF5E3D,
        ),
        ShaderEffect(
            id = "crt",
            name = "CRT",
            tagline = "phosphor tube",
            params = listOf(
                ShaderParam("Curve", 0.5f),
                ShaderParam("Phosphor", 0.55f),
                ShaderParam("Vignette", 0.5f),
            ),
            agsl = CRT,
            accentStart = 0xFF00E56E,
            accentEnd = 0xFF00838C,
        ),
        ShaderEffect(
            id = "printpress",
            name = "Print Press",
            tagline = "newspaper halftone",
            params = listOf(
                ShaderParam("Size", 0.5f),
                ShaderParam("Angle", 0.5f),
                ShaderParam("Contrast", 0.5f),
            ),
            agsl = HALFTONE_PRINT,
            accentStart = 0xFF8C93A8,
            accentEnd = 0xFF3E4356,
        ),
        ShaderEffect(
            id = "sketch",
            name = "Sketch",
            tagline = "cross-hatch ink",
            params = listOf(
                ShaderParam("Density", 0.5f),
                ShaderParam("Ink", 0.5f),
                ShaderParam("Contrast", 0.5f),
            ),
            agsl = CROSSHATCH,
            accentStart = 0xFFE8DCC0,
            accentEnd = 0xFF6B5C45,
        ),
        ShaderEffect(
            id = "oilflow",
            name = "Oil Flow",
            tagline = "painterly smear",
            params = listOf(
                ShaderParam("Length", 0.5f),
                ShaderParam("Scale", 0.5f),
                ShaderParam("Blend", 0.9f),
            ),
            agsl = OIL_FLOW,
            accentStart = 0xFFFF7A3D,
            accentEnd = 0xFF7A3DFF,
        ),
        ShaderEffect(
            id = "neonedge",
            name = "Neon Edge",
            tagline = "electric contours",
            params = listOf(
                ShaderParam("Sensitivity", 0.55f),
                ShaderParam("Glow", 0.6f),
                ShaderParam("Hue", 0.5f),
            ),
            agsl = NEON_EDGE,
            accentStart = 0xFF00FFC2,
            accentEnd = 0xFF0077FF,
        ),
        ShaderEffect(
            id = "emboss",
            name = "Emboss",
            tagline = "metal relief",
            params = listOf(
                ShaderParam("Depth", 0.5f),
                ShaderParam("Angle", 0.15f),
                ShaderParam("Blend", 1.0f),
            ),
            agsl = EMBOSS,
            accentStart = 0xFFB8BDC9,
            accentEnd = 0xFF585E6E,
        ),
        ShaderEffect(
            id = "thermal",
            name = "Thermal",
            tagline = "heat vision",
            params = listOf(
                ShaderParam("Contrast", 0.5f),
                ShaderParam("Shift", 0.5f),
                ShaderParam("Blend", 1.0f),
            ),
            agsl = THERMAL,
            accentStart = 0xFFFFD23D,
            accentEnd = 0xFFB3232A,
        ),
        ShaderEffect(
            id = "solarize",
            name = "Solarize",
            tagline = "darkroom fold",
            params = listOf(
                ShaderParam("Threshold", 0.5f),
                ShaderParam("Hardness", 0.6f),
                ShaderParam("Blend", 1.0f),
            ),
            agsl = SOLARIZE,
            accentStart = 0xFFFFE53D,
            accentEnd = 0xFF7A3DFF,
        ),
        ShaderEffect(
            id = "posterpop",
            name = "Poster Pop",
            tagline = "warhol punch",
            params = listOf(
                ShaderParam("Levels", 0.5f),
                ShaderParam("Punch", 0.6f),
                ShaderParam("Blend", 1.0f),
            ),
            agsl = POSTER_POP,
            accentStart = 0xFFFF3DD8,
            accentEnd = 0xFFFFB03D,
        ),
        ShaderEffect(
            id = "scanslice",
            name = "Scan Slice",
            tagline = "shifted columns",
            params = listOf(
                ShaderParam("Slices", 0.5f),
                ShaderParam("Offset", 0.45f),
                ShaderParam("Seed", 0.3f),
            ),
            agsl = SCAN_SLICE,
            accentStart = 0xFF3DFFB0,
            accentEnd = 0xFF3D6BFF,
        ),
        ShaderEffect(
            id = "mirror",
            name = "Mirror",
            tagline = "symmetry fold",
            params = listOf(
                ShaderParam("Axis", 0.5f),
                ShaderParam("Feather", 0.25f),
                ShaderParam("Blend", 1.0f),
            ),
            agsl = MIRROR,
            accentStart = 0xFF6BC8FF,
            accentEnd = 0xFFB36BFF,
        ),
        ShaderEffect(
            id = "chromazoom",
            name = "Chroma Zoom",
            tagline = "radial burst",
            params = listOf(
                ShaderParam("Strength", 0.5f),
                ShaderParam("Falloff", 0.5f),
                ShaderParam("Blend", 1.0f),
            ),
            agsl = CHROMA_ZOOM,
            accentStart = 0xFFFF3D5E,
            accentEnd = 0xFF3D9BFF,
        ),
        ShaderEffect(
            id = "rainglass",
            name = "Rain Glass",
            tagline = "wet window",
            params = listOf(
                ShaderParam("Refraction", 0.55f),
                ShaderParam("Cells", 0.5f),
                ShaderParam("Speed", 0.35f),
            ),
            agsl = RAIN_GLASS,
            animated = true,
            accentStart = 0xFF4A90D9,
            accentEnd = 0xFF1E3A5F,
        ),
        ShaderEffect(
            id = "hexpixel",
            name = "Hex Pixel",
            tagline = "honeycomb mosaic",
            params = listOf(
                ShaderParam("Size", 0.5f),
                ShaderParam("Gap", 0.35f),
                ShaderParam("Blend", 1.0f),
            ),
            agsl = HEX_PIXEL,
            accentStart = 0xFFFFC53D,
            accentEnd = 0xFF8C5E00,
        ),
        ShaderEffect(
            id = "linescreen",
            name = "Linescreen",
            tagline = "engraved lines",
            params = listOf(
                ShaderParam("Lines", 0.5f),
                ShaderParam("Contrast", 0.55f),
                ShaderParam("Tint", 0.4f),
            ),
            agsl = LINESCREEN,
            accentStart = 0xFFDCD6C8,
            accentEnd = 0xFF44403A,
        ),
        ShaderEffect(
            id = "weave",
            name = "Weave",
            tagline = "woven canvas",
            params = listOf(
                ShaderParam("Scale", 0.5f),
                ShaderParam("Depth", 0.6f),
                ShaderParam("Blend", 1.0f),
            ),
            agsl = WEAVE,
            accentStart = 0xFFC98A5E,
            accentEnd = 0xFF6E432A,
        ),
        ShaderEffect(
            id = "chromawave",
            name = "Chroma Wave",
            tagline = "rgb drift",
            params = listOf(
                ShaderParam("Amplitude", 0.45f),
                ShaderParam("Frequency", 0.5f),
                ShaderParam("Speed", 0.4f),
            ),
            agsl = CHROMA_WAVE,
            animated = true,
            accentStart = 0xFFFF3D3D,
            accentEnd = 0xFF3D3DFF,
        ),
        ShaderEffect(
            id = "bloom",
            name = "Bloom",
            tagline = "dreamy glow",
            params = listOf(
                ShaderParam("Radius", 0.5f),
                ShaderParam("Intensity", 0.55f),
                ShaderParam("Threshold", 0.45f),
            ),
            agsl = BLOOM,
            accentStart = 0xFFFFE9B0,
            accentEnd = 0xFFFF8A3D,
        ),
        ShaderEffect(
            id = "nightvision",
            name = "Night Vision",
            tagline = "military optic",
            params = listOf(
                ShaderParam("Gain", 0.55f),
                ShaderParam("Grain", 0.45f),
                ShaderParam("Vignette", 0.6f),
            ),
            agsl = NIGHT_VISION,
            animated = true,
            accentStart = 0xFF26FF6B,
            accentEnd = 0xFF0A4A1E,
        ),
        ShaderEffect(
            id = "popdots",
            name = "Pop Dots",
            tagline = "comic cmyk",
            params = listOf(
                ShaderParam("Size", 0.5f),
                ShaderParam("Spread", 0.5f),
                ShaderParam("Blend", 1.0f),
            ),
            agsl = POP_DOTS,
            accentStart = 0xFF00C2FF,
            accentEnd = 0xFFFF2E9E,
        ),
        ShaderEffect(
            id = "aurora",
            name = "Aurora",
            tagline = "northern lights",
            params = listOf(
                ShaderParam("Height", 0.5f),
                ShaderParam("Intensity", 0.55f),
                ShaderParam("Speed", 0.4f),
            ),
            agsl = AURORA,
            animated = true,
            accentStart = 0xFF0BD98A,
            accentEnd = 0xFF243BB3,
        ),
        ShaderEffect(
            id = "toon",
            name = "Toon",
            tagline = "cel shading",
            params = listOf(
                ShaderParam("Levels", 0.5f),
                ShaderParam("Edge", 0.5f),
                ShaderParam("Blend", 1.0f),
            ),
            agsl = TOON,
            accentStart = 0xFFFFB03D,
            accentEnd = 0xFFE84A5F,
        ),
        ShaderEffect(
            id = "anaglyph",
            name = "Anaglyph",
            tagline = "3d glasses",
            params = listOf(
                ShaderParam("Shift", 0.45f),
                ShaderParam("Angle", 0.0f),
                ShaderParam("Blend", 1.0f),
            ),
            agsl = ANAGLYPH,
            accentStart = 0xFFFF3D3D,
            accentEnd = 0xFF00C2D7,
        ),
        ShaderEffect(
            id = "lomo",
            name = "Lomo",
            tagline = "cross process",
            params = listOf(
                ShaderParam("Curve", 0.6f),
                ShaderParam("Vignette", 0.55f),
                ShaderParam("Blend", 1.0f),
            ),
            agsl = LOMO,
            accentStart = 0xFF7FB069,
            accentEnd = 0xFF1B4332,
        ),
        ShaderEffect(
            id = "oldfilm",
            name = "Old Film",
            tagline = "silent movie",
            params = listOf(
                ShaderParam("Age", 0.7f),
                ShaderParam("Scratches", 0.55f),
                ShaderParam("Grain", 0.45f),
            ),
            agsl = OLD_FILM,
            animated = true,
            accentStart = 0xFFC9A66B,
            accentEnd = 0xFF4A3B28,
        ),
        ShaderEffect(
            id = "infrared",
            name = "Infrared",
            tagline = "ir foliage",
            params = listOf(
                ShaderParam("Swap", 0.75f),
                ShaderParam("Glow", 0.5f),
                ShaderParam("Blend", 1.0f),
            ),
            agsl = INFRARED,
            accentStart = 0xFFFF6B6B,
            accentEnd = 0xFF8B0000,
        ),
        ShaderEffect(
            id = "pixelsort",
            name = "Pixel Sort",
            tagline = "data streaks",
            params = listOf(
                ShaderParam("Threshold", 0.5f),
                ShaderParam("Length", 0.5f),
                ShaderParam("Blend", 1.0f),
            ),
            agsl = PIXEL_SORT,
            accentStart = 0xFF00FFD1,
            accentEnd = 0xFFFF00AA,
        ),
        ShaderEffect(
            id = "stainedglass",
            name = "Stained Glass",
            tagline = "voronoi leads",
            params = listOf(
                ShaderParam("Cells", 0.5f),
                ShaderParam("Leads", 0.55f),
                ShaderParam("Blend", 1.0f),
            ),
            agsl = STAINED_GLASS,
            accentStart = 0xFFE63946,
            accentEnd = 0xFF457B9D,
        ),
        ShaderEffect(
            id = "trimosaic",
            name = "Tri Mosaic",
            tagline = "faceted glass",
            params = listOf(
                ShaderParam("Size", 0.5f),
                ShaderParam("Facets", 0.55f),
                ShaderParam("Blend", 1.0f),
            ),
            agsl = TRI_MOSAIC,
            accentStart = 0xFF9D4EDD,
            accentEnd = 0xFF3C096C,
        ),
        ShaderEffect(
            id = "spinblur",
            name = "Spin Blur",
            tagline = "rotation smear",
            params = listOf(
                ShaderParam("Amount", 0.5f),
                ShaderParam("Falloff", 0.5f),
                ShaderParam("Blend", 1.0f),
            ),
            agsl = SPIN_BLUR,
            accentStart = 0xFF48CAE4,
            accentEnd = 0xFF023E8A,
        ),
        ShaderEffect(
            id = "motionblur",
            name = "Motion Blur",
            tagline = "speed streak",
            params = listOf(
                ShaderParam("Length", 0.5f),
                ShaderParam("Angle", 0.0f),
                ShaderParam("Blend", 1.0f),
            ),
            agsl = MOTION_BLUR,
            accentStart = 0xFFFFA62B,
            accentEnd = 0xFF582F0E,
        ),
        ShaderEffect(
            id = "littleplanet",
            name = "Little Planet",
            tagline = "polar world",
            params = listOf(
                ShaderParam("Zoom", 0.5f),
                ShaderParam("Rotate", 0.0f),
                ShaderParam("Blend", 1.0f),
            ),
            agsl = LITTLE_PLANET,
            accentStart = 0xFF06D6A0,
            accentEnd = 0xFF118AB2,
        ),
        ShaderEffect(
            id = "quadmirror",
            name = "Quad Mirror",
            tagline = "4-way fold",
            params = listOf(
                ShaderParam("Axis X", 0.5f),
                ShaderParam("Axis Y", 0.5f),
                ShaderParam("Blend", 1.0f),
            ),
            agsl = QUAD_MIRROR,
            accentStart = 0xFFB5179E,
            accentEnd = 0xFF3A0CA3,
        ),
        ShaderEffect(
            id = "flagwave",
            name = "Flag Wave",
            tagline = "textile ripple",
            params = listOf(
                ShaderParam("Amplitude", 0.5f),
                ShaderParam("Frequency", 0.45f),
                ShaderParam("Speed", 0.5f),
            ),
            agsl = FLAG_WAVE,
            animated = true,
            accentStart = 0xFFE63946,
            accentEnd = 0xFFF1FAEE,
        ),
        ShaderEffect(
            id = "underwater",
            name = "Underwater",
            tagline = "caustic depths",
            params = listOf(
                ShaderParam("Warp", 0.5f),
                ShaderParam("Caustics", 0.55f),
                ShaderParam("Speed", 0.4f),
            ),
            agsl = UNDERWATER,
            animated = true,
            accentStart = 0xFF00B4D8,
            accentEnd = 0xFF03045E,
        ),
        ShaderEffect(
            id = "heathaze",
            name = "Heat Haze",
            tagline = "desert shimmer",
            params = listOf(
                ShaderParam("Strength", 0.5f),
                ShaderParam("Scale", 0.5f),
                ShaderParam("Speed", 0.5f),
            ),
            agsl = HEAT_HAZE,
            animated = true,
            accentStart = 0xFFFFBA08,
            accentEnd = 0xFFD00000,
        ),
        ShaderEffect(
            id = "ghost",
            name = "Double Ghost",
            tagline = "double exposure",
            params = listOf(
                ShaderParam("Offset", 0.4f),
                ShaderParam("Angle", 0.12f),
                ShaderParam("Mix", 0.7f),
            ),
            agsl = DOUBLE_GHOST,
            accentStart = 0xFFCBC0D3,
            accentEnd = 0xFF56445D,
        ),
        ShaderEffect(
            id = "matrixrain",
            name = "Matrix Rain",
            tagline = "digital downpour",
            params = listOf(
                ShaderParam("Columns", 0.5f),
                ShaderParam("Glow", 0.6f),
                ShaderParam("Speed", 0.45f),
            ),
            agsl = MATRIX_RAIN,
            animated = true,
            accentStart = 0xFF00FF41,
            accentEnd = 0xFF003B00,
        ),
        ShaderEffect(
            id = "sparkle",
            name = "Sparkle",
            tagline = "glitter bomb",
            params = listOf(
                ShaderParam("Density", 0.55f),
                ShaderParam("Size", 0.5f),
                ShaderParam("Speed", 0.5f),
            ),
            agsl = SPARKLE,
            animated = true,
            accentStart = 0xFFFFF3B0,
            accentEnd = 0xFFE09F3E,
        ),
        ShaderEffect(
            id = "negative",
            name = "Negative",
            tagline = "film invert",
            params = listOf(
                ShaderParam("Invert", 1.0f),
                ShaderParam("Tint", 0.4f),
                ShaderParam("Blend", 1.0f),
            ),
            agsl = NEGATIVE,
            accentStart = 0xFF2B2D42,
            accentEnd = 0xFFEDF2F4,
        ),
        ShaderEffect(
            id = "gameboy",
            name = "Game Boy",
            tagline = "4-shade lcd",
            params = listOf(
                ShaderParam("Pixel", 0.5f),
                ShaderParam("Contrast", 0.5f),
                ShaderParam("Blend", 1.0f),
            ),
            agsl = GAMEBOY,
            accentStart = 0xFF9BBC0F,
            accentEnd = 0xFF0F380F,
        ),
        ShaderEffect(
            id = "bitdither",
            name = "Bit Dither",
            tagline = "ordered 1-bit",
            params = listOf(
                ShaderParam("Size", 0.35f),
                ShaderParam("Threshold", 0.5f),
                ShaderParam("Blend", 1.0f),
            ),
            agsl = BIT_DITHER,
            accentStart = 0xFFF5F5F5,
            accentEnd = 0xFF1A1A2E,
        ),
        ShaderEffect(
            id = "watercolor",
            name = "Watercolor",
            tagline = "ink wash",
            params = listOf(
                ShaderParam("Flow", 0.5f),
                ShaderParam("Paper", 0.55f),
                ShaderParam("Edges", 0.5f),
            ),
            agsl = WATERCOLOR,
            accentStart = 0xFF83C5BE,
            accentEnd = 0xFF006D77,
        ),
        ShaderEffect(
            id = "prismleak",
            name = "Prism Leak",
            tagline = "rainbow flare",
            params = listOf(
                ShaderParam("Position", 0.5f),
                ShaderParam("Width", 0.5f),
                ShaderParam("Strength", 0.65f),
            ),
            agsl = PRISM_LEAK,
            accentStart = 0xFFFF6D00,
            accentEnd = 0xFF7B2CBF,
        ),
        ShaderEffect(
            id = "timesmear",
            name = "Time Smear",
            tagline = "slit-scan pinch",
            params = listOf(
                ShaderParam("Stretch", 0.5f),
                ShaderParam("Waves", 0.4f),
                ShaderParam("Blend", 1.0f),
            ),
            agsl = TIME_SMEAR,
            accentStart = 0xFF4CC9F0,
            accentEnd = 0xFF7209B7,
        ),
        ShaderEffect(
            id = "filmfade",
            name = "Film Fade",
            tagline = "faded matte",
            params = listOf(
                ShaderParam("Fade", 0.6f),
                ShaderParam("Grain", 0.4f),
                ShaderParam("Vignette", 0.5f),
            ),
            agsl = FILM_FADE,
            animated = true,
            accentStart = 0xFFD5BDAF,
            accentEnd = 0xFF6B4F3A,
        ),
    )
}
