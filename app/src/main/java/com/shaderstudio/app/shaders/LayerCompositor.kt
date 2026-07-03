package com.shaderstudio.app.shaders

/**
 * Compiles a stack of effect layers into a single AGSL program.
 *
 * Every layer's content is its effect applied to the source photo; the stack
 * is composited bottom-up with Procreate's blend-mode set, entirely on the
 * GPU in one pass. The same generated program is used for the live preview
 * (via RenderEffect) and the full-resolution export (via BitmapShader), so
 * what you see is exactly what gets saved.
 */

/** Procreate's blend modes. Ordinals are baked into the generated AGSL. */
enum class LayerBlendMode(val label: String) {
    NORMAL("Normal"),
    DARKEN("Darken"),
    MULTIPLY("Multiply"),
    COLOR_BURN("Color Burn"),
    LINEAR_BURN("Linear Burn"),
    DARKER_COLOR("Darker Color"),
    LIGHTEN("Lighten"),
    SCREEN("Screen"),
    COLOR_DODGE("Color Dodge"),
    ADD("Add"),
    LIGHTER_COLOR("Lighter Color"),
    OVERLAY("Overlay"),
    SOFT_LIGHT("Soft Light"),
    HARD_LIGHT("Hard Light"),
    VIVID_LIGHT("Vivid Light"),
    LINEAR_LIGHT("Linear Light"),
    PIN_LIGHT("Pin Light"),
    HARD_MIX("Hard Mix"),
    DIFFERENCE("Difference"),
    EXCLUSION("Exclusion"),
    SUBTRACT("Subtract"),
    DIVIDE("Divide"),
    HUE("Hue"),
    SATURATION("Saturation"),
    COLOR("Color"),
    LUMINOSITY("Luminosity"),
}

/** Plain snapshot of one layer, decoupled from Compose state for export. */
/** Per-layer gradient mask shape that limits where the effect is applied. */
enum class MaskType(val label: String) {
    NONE("None"),
    LINEAR("Linear"),
    RADIAL("Radial"),
    MIRROR("Mirror"),
}

data class LayerSpec(
    val effect: ShaderEffect,
    val params: List<Float>,
    val blend: LayerBlendMode,
    val opacity: Float,
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val maskType: MaskType = MaskType.NONE,
    val maskX: Float = 0.5f,
    val maskY: Float = 0.5f,
    val maskSize: Float = 0.4f,
    val maskAngle: Float = 0f,
    val maskFeather: Float = 0.5f,
    val maskInvert: Boolean = false,
)

object LayerCompositor {

    const val MAX_LAYERS = 5

    /**
     * Matches top-level function definitions inside an effect body (e.g.
     * "float3 pal(", "half4 main("). Every defined function is renamed with a
     * per-layer prefix so the same effect can appear on several layers and
     * different effects never collide. Calls to the shared NOISE_LIB
     * functions are untouched because their definitions live outside bodies.
     */
    private val functionDef = Regex("""\b(?:float[234]?|half4|int)\s+([A-Za-z_]\w*)\s*\(""")

    /** PDF/Photoshop-spec blend math shared by all generated programs. */
    private const val BLEND_LIB = """
float pLum(float3 c) { return dot(c, float3(0.3, 0.59, 0.11)); }

float3 pClipColor(float3 c) {
    float l = pLum(c);
    float n = min(min(c.r, c.g), c.b);
    float x = max(max(c.r, c.g), c.b);
    float3 r = c;
    if (n < 0.0) { r = l + (r - l) * l / max(l - n, 0.0001); }
    if (x > 1.0) { r = l + (r - l) * (1.0 - l) / max(x - l, 0.0001); }
    return r;
}

float3 pSetLum(float3 c, float l) { return pClipColor(c + (l - pLum(c))); }

float pSat(float3 c) {
    return max(max(c.r, c.g), c.b) - min(min(c.r, c.g), c.b);
}

float3 pSetSat(float3 c, float s) {
    float mn = min(min(c.r, c.g), c.b);
    float mx = max(max(c.r, c.g), c.b);
    float3 r = float3(0.0);
    if (mx > mn) { r = (c - mn) * s / (mx - mn); }
    return r;
}

float3 blendPx(float3 b, float3 s, int mode) {
    if (mode == 0) { return s; }
    if (mode == 1) { return min(b, s); }
    if (mode == 2) { return b * s; }
    if (mode == 3) { return 1.0 - min(float3(1.0), (1.0 - b) / max(s, float3(0.0001))); }
    if (mode == 4) { return clamp(b + s - 1.0, 0.0, 1.0); }
    if (mode == 5) { return pLum(s) < pLum(b) ? s : b; }
    if (mode == 6) { return max(b, s); }
    if (mode == 7) { return 1.0 - (1.0 - b) * (1.0 - s); }
    if (mode == 8) { return min(float3(1.0), b / max(1.0 - s, float3(0.0001))); }
    if (mode == 9) { return min(b + s, float3(1.0)); }
    if (mode == 10) { return pLum(s) > pLum(b) ? s : b; }
    if (mode == 11) {
        float3 lo = 2.0 * b * s;
        float3 hi = 1.0 - 2.0 * (1.0 - b) * (1.0 - s);
        return mix(lo, hi, step(float3(0.5), b));
    }
    if (mode == 12) {
        float3 d = mix(sqrt(b), ((16.0 * b - 12.0) * b + 4.0) * b, step(b, float3(0.25)));
        float3 lo = b - (1.0 - 2.0 * s) * b * (1.0 - b);
        float3 hi = b + (2.0 * s - 1.0) * (d - b);
        return mix(lo, hi, step(float3(0.5), s));
    }
    if (mode == 13) {
        float3 lo = 2.0 * b * s;
        float3 hi = 1.0 - 2.0 * (1.0 - b) * (1.0 - s);
        return mix(lo, hi, step(float3(0.5), s));
    }
    if (mode == 14) {
        float3 burn = 1.0 - min(float3(1.0), (1.0 - b) / max(2.0 * s, float3(0.0001)));
        float3 dodge = min(float3(1.0), b / max(2.0 * (1.0 - s), float3(0.0001)));
        return mix(burn, dodge, step(float3(0.5), s));
    }
    if (mode == 15) { return clamp(b + 2.0 * s - 1.0, 0.0, 1.0); }
    if (mode == 16) {
        float3 lo = min(b, 2.0 * s);
        float3 hi = max(b, 2.0 * s - 1.0);
        return mix(lo, hi, step(float3(0.5), s));
    }
    if (mode == 17) { return step(float3(1.0), b + s); }
    if (mode == 18) { return abs(b - s); }
    if (mode == 19) { return b + s - 2.0 * b * s; }
    if (mode == 20) { return clamp(b - s, 0.0, 1.0); }
    if (mode == 21) { return clamp(b / max(s, float3(0.0001)), 0.0, 1.0); }
    if (mode == 22) { return pSetLum(pSetSat(s, pSat(b)), pLum(b)); }
    if (mode == 23) { return pSetLum(pSetSat(b, pSat(s)), pLum(b)); }
    if (mode == 24) { return pSetLum(s, pLum(b)); }
    return pSetLum(b, pLum(s));
}
"""

    /** Gradient-mask factor in [0,1] for a normalized pixel coordinate. */
    private const val MASK_LIB = """
float maskFactor(float2 uv, int type, float2 pos, float size, float ang, float feather, float invert) {
    float m = 1.0;
    float f = max(feather * size, 0.001);
    if (type == 1) {
        float2 dir = float2(cos(ang), sin(ang));
        float proj = dot(uv - pos, dir);
        m = smoothstep(size, size - f, proj);
    } else if (type == 2) {
        float d = length((uv - pos) * float2(1.0, 1.0));
        m = 1.0 - smoothstep(size - f, size, d);
    } else if (type == 3) {
        float2 dir = float2(cos(ang), sin(ang));
        float proj = abs(dot(uv - pos, dir));
        m = 1.0 - smoothstep(size - f, size, proj);
    }
    return mix(m, 1.0 - m, invert);
}
"""

    /**
     * Generates a single AGSL program for the given effect stack.
     * Uniform interface:
     *   uImage, uResolution, uTime            – shared
     *   uL{i}P1..3, uL{i}Mode, uL{i}Opacity   – per layer i (bottom to top)
     */
    fun generateSource(effects: List<ShaderEffect>): String {
        require(effects.isNotEmpty()) { "Layer stack must not be empty" }
        require(effects.size <= MAX_LAYERS) { "At most $MAX_LAYERS layers" }
        require(effects.all { it.agsl != null }) { "All layers need an AGSL effect" }

        val sb = StringBuilder()
        sb.append("uniform shader uImage;\n")
        sb.append("uniform float2 uResolution;\n")
        sb.append("uniform float uTime;\n")
        effects.forEachIndexed { i, _ ->
            sb.append("uniform float uL${i}P1;\n")
            sb.append("uniform float uL${i}P2;\n")
            sb.append("uniform float uL${i}P3;\n")
            sb.append("uniform float2 uL${i}Center;\n")
            sb.append("uniform int uL${i}Mode;\n")
            sb.append("uniform float uL${i}Opacity;\n")
            sb.append("uniform int uL${i}Mask;\n")
            sb.append("uniform float2 uL${i}MaskPos;\n")
            sb.append("uniform float uL${i}MaskSize;\n")
            sb.append("uniform float uL${i}MaskAngle;\n")
            sb.append("uniform float uL${i}MaskFeather;\n")
            sb.append("uniform float uL${i}MaskInvert;\n")
        }
        if (effects.any { it.agsl!!.contains(NOISE_LIB) }) {
            sb.append(NOISE_LIB)
        }
        sb.append(BLEND_LIB)
        sb.append(MASK_LIB)
        effects.forEachIndexed { i, e ->
            var body = e.agsl!!
                .replace(PRELUDE, "")
                .replace(NOISE_LIB, "")
                .replace("uParam1", "uL${i}P1")
                .replace("uParam2", "uL${i}P2")
                .replace("uParam3", "uL${i}P3")
                .replace("uCenter", "uL${i}Center")
            val defined = functionDef.findAll(body).map { it.groupValues[1] }.toSet()
            for (h in defined) {
                body = body.replace(Regex("\\b$h\\b"), "l${i}_$h")
            }
            sb.append(body)
        }
        sb.append("\nhalf4 main(float2 coord) {\n")
        sb.append("    float2 uv = coord / uResolution;\n")
        sb.append("    float3 acc = float3(uImage.eval(coord).rgb);\n")
        sb.append("    float3 s;\n")
        sb.append("    float mk;\n")
        effects.forEachIndexed { i, _ ->
            sb.append("    s = float3(l${i}_main(coord).rgb);\n")
            sb.append(
                "    mk = maskFactor(uv, uL${i}Mask, uL${i}MaskPos, uL${i}MaskSize, " +
                    "uL${i}MaskAngle, uL${i}MaskFeather, uL${i}MaskInvert);\n",
            )
            sb.append("    acc = clamp(mix(acc, blendPx(acc, s, uL${i}Mode), uL${i}Opacity * mk), 0.0, 1.0);\n")
        }
        sb.append("    return half4(half3(acc), 1.0);\n")
        sb.append("}\n")
        return sb.toString()
    }
}
