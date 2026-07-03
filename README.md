# Shader Studio 🎨

GPU-powered photo editor for **Android 14+**, built with **Jetpack Compose**, **Material 3 Expressive** and **AGSL runtime shaders**. Turn photos into posters and art — the same shader aesthetics you see in Framer's viral gradients, LED dot-matrix posters and fluted-glass hero sections, running in real time on your phone's GPU.

> 📦 **Download:** grab the latest APK from the [Releases](../../releases) page.

## ✨ 108 Effects

58 classic looks (below) plus **50 fluid & fantasy effects**: Inferno, Plasma Orb, Electric Storm, Solar Flare, Ember Dust, Neon Pulse, Aura Glow, Portal, Warp Speed, Galaxy, Ocean Waves, Rainfall, Snowfall, Frost, Mist, Thunder Sky, Bubbles, Whirlpool, Caustic Pool, Monsoon, Fairy Dust, Dream Blur, Astral, Spirit Veil, Enchanted, Moonlight, Golden Hour, Stardust Trail, Crystal Prism, Iridescence, Flow Field, Marble Ink, Liquid Chrome, Oil Slick, Ink Bleed, Silk Waves, Lava Lamp, Melting, Turbulence, Zen Ripples, Hologram, Cyber Grid, Data Stream, Force Field, Teleport, Radar Sweep, Glitch Storm, X-Ray, Comic Noir, Fractal Zoom.

### Classic pack (58)

| Effect | Look | Animated |
| --- | --- | --- |
| **Dot Matrix** | LED / halftone poster | – |
| **Fluted Glass** | Vertical ribbed-glass refraction | – |
| **Liquid Sky** | Flowing "viral gradient" screen-blend + warp | ✅ |
| **Glitch** | Chromatic aberration + row displacement | ✅ |
| **Mosaic** | Pixel poster with posterization | – |
| **VHS** | Tape wobble, scanlines, grain, vignette | ✅ |
| **Duotone** | Two-tone poster mapping with hue controls | – |
| **Ripple** | Radial liquid wave distortion | ✅ |
| **Kaleidoscope** | Mirror mandala segments | – |
| **Swirl** | Vortex twist around center | – |
| **Lens** | Bulge & pinch distortion | – |
| **Tilt Shift** | Miniature-style band focus | – |
| **CRT** | Curved tube, phosphor mask, scanlines | – |
| **Print Press** | Rotated newspaper halftone | – |
| **Sketch** | 4-layer cross-hatch ink drawing | – |
| **Oil Flow** | Painterly smear along noise flow | – |
| **Neon Edge** | Sobel edge glow with hue control | – |
| **Emboss** | Directional metal relief | – |
| **Thermal** | Heat-camera palette mapping | – |
| **Solarize** | Darkroom tone fold | – |
| **Poster Pop** | Hard posterize + saturation punch | – |
| **Scan Slice** | Random vertical column offsets | – |
| **Mirror** | Feathered symmetry fold | – |
| **Chroma Zoom** | Radial chromatic zoom burst | – |
| **Rain Glass** | Animated droplet refraction | ✅ |
| **Hex Pixel** | Honeycomb mosaic | – |
| **Linescreen** | Engraved luminance lines | – |
| **Weave** | Woven canvas threads | – |
| **Chroma Wave** | Sinusoidal RGB channel drift | ✅ |
| **Bloom** | Soft threshold glow | – |
| **Night Vision** | Green optic, grain, vignette | ✅ |
| **Pop Dots** | Comic CMYK dot screens | – |
| **Aurora** | Northern-lights curtains overlay | ✅ |
| **Toon** | Cel shading with dark outlines | – |
| **Anaglyph** | Red/cyan 3D-glasses stereo | – |
| **Lomo** | Cross-processed s-curve + heavy vignette | – |
| **Old Film** | Sepia, scratches, dust, flicker | ✅ |
| **Infrared** | IR channel-swap foliage glow | – |
| **Pixel Sort** | Threshold-driven bright streaks | – |
| **Stained Glass** | Voronoi cells with lead lines | – |
| **Tri Mosaic** | Faceted triangle pixelation | – |
| **Spin Blur** | Rotational smear around center | – |
| **Motion Blur** | Directional speed streak | – |
| **Little Planet** | Polar-coordinates tiny world | – |
| **Quad Mirror** | 4-way symmetry fold | – |
| **Flag Wave** | Cloth ripple with shading | ✅ |
| **Underwater** | Caustic light + blue depth warp | ✅ |
| **Heat Haze** | Rising desert shimmer | ✅ |
| **Double Ghost** | Double-exposure screen blend | – |
| **Matrix Rain** | Green digital rain overlay | ✅ |
| **Sparkle** | Twinkling glitter on highlights | ✅ |
| **Negative** | Tinted film inversion | – |
| **Game Boy** | 4-shade LCD palette | – |
| **Bit Dither** | Ordered Bayer 1-bit dithering | – |
| **Watercolor** | Soft ink wash on paper grain | – |
| **Prism Leak** | Diagonal rainbow light leak | – |
| **Time Smear** | Slit-scan progressive pinch | – |
| **Film Fade** | Lifted-black matte + grain | ✅ |

Every effect exposes up to three parameters driven by Material 3 sliders, previews in real time via `RenderEffect` + `RuntimeShader`, and exports at the photo's own resolution (up to 8192 px, JPEG 98) through a GPU pass (`HardwareRenderer` + `ImageReader`) straight into your gallery (`Pictures/Shader Studio`).

## 🎬 Export: stills, GIF & video

Tap **Save** to open the export sheet:

- **Format** — JPEG · PNG (lossless) · **GIF** (looping) · **MP4** (H.264 video)
- **Resolution** — Original (a 4K photo renders at 4K, an 8K photo at 8K) · 4K · 2K · 1080p
- **Quality** — High / Maximum for JPEG and MP4

Stills go through the GPU pass at the photo's own resolution (`util/ImageIO.kt`, `FrameRenderer`). Animated effects are rendered frame-by-frame from the same `FrameRenderer`: GIF via a pure-Kotlin GIF89a encoder (NeuQuant quantization + LZW, `util/GifEncoder.kt`) and MP4 via `MediaCodec` + `MediaMuxer` (`util/VideoEncoder.kt`). Everything saves to `Pictures/Shader Studio` (stills, GIF) or `Movies/Shader Studio` (video).

## 🎭 Masking

Each layer can carry a **gradient mask** that limits where its effect shows: **Linear**, **Radial** or **Mirror**, with size, feather, angle and invert controls. Drag on the photo to reposition the mask. Masks are computed per-pixel in the composite shader (`maskFactor` in `shaders/LayerCompositor.kt`) so they cost nothing extra and export identically to the preview.

## 🖼️ Home album

The home screen shows an **album of your past exports** (queried from `Pictures/Shader Studio` via MediaStore). Tap any tile to reopen that image in the editor — the photo animates into the pick button on the way in. The animated hero background is picked at random from **four abstract shaders** (liquid, plasma, aurora, nebula) with a **randomized colour palette on every launch**.

## 🧅 Layers & Blend Modes

Stack up to **5 effect layers** over the photo, Procreate-style. Each layer has its own effect, parameters, opacity, visibility toggle and one of **26 blend modes** — the full Procreate set: Normal, Darken, Multiply, Color Burn, Linear Burn, Darker Color, Lighten, Screen, Color Dodge, Add, Lighter Color, Overlay, Soft Light, Hard Light, Vivid Light, Linear Light, Pin Light, Hard Mix, Difference, Exclusion, Subtract, Divide, Hue, Saturation, Color, Luminosity.

The whole stack compiles into a **single generated AGSL program** (`shaders/LayerCompositor.kt`) used identically for the live preview and the full-resolution export — what you see is exactly what gets saved. Blend math follows the PDF/Photoshop separable + non-separable spec, implemented per-pixel in AGSL.

Center-based effects (Kaleidoscope, Swirl, Lens, Ripple, Spin Blur, Chroma Zoom, Little Planet, Quad Mirror, Mirror, Tilt Shift, Prism Leak) are **positionable**: drag on the preview to move the effect's center / axis / focus line.

## 🧱 Tech

- **AGSL** (Android Graphics Shading Language) — every effect is a single fragment shader with a shared uniform interface (`uImage`, `uResolution`, `uTime`, `uParam1..3`)
- **Material 3 Expressive** — `MaterialExpressiveTheme`, expressive motion scheme, springy effect cards, `LoadingIndicator`
- **Jetpack Compose** single-activity app, dynamic color (Material You)
- **Photo Picker** (`PickVisualMedia`) — zero runtime permissions
- minSdk **34** (Android 14), target/compile SDK 36

## 🏗️ Build

```bash
./gradlew assembleRelease
```

CI builds the APK on every push and publishes it to GitHub Releases (see `.github/workflows/android-release.yml`).

## 📁 Structure

```
app/src/main/java/com/shaderstudio/app/
├── MainActivity.kt          # single activity + screen switching
├── shaders/Shaders.kt       # AGSL effect library (the fun part)
├── ui/HomeScreen.kt         # animated shader hero + photo picker
├── ui/EditorScreen.kt       # live preview, effect carousel, param sliders
├── ui/theme/                # M3 Expressive theme
└── util/ImageIO.kt          # decode, GPU export, MediaStore save
```
