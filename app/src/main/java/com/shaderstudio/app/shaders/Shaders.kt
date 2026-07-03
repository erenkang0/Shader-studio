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
    )
}
