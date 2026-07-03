# Shader Studio 🎨

GPU-powered photo editor for **Android 14+**, built with **Jetpack Compose**, **Material 3 Expressive** and **AGSL runtime shaders**. Turn photos into posters and art — the same shader aesthetics you see in Framer's viral gradients, LED dot-matrix posters and fluted-glass hero sections, running in real time on your phone's GPU.

> 📦 **Download:** grab the latest APK from the [Releases](../../releases) page.

## ✨ Effects

| Effect | Look | Animated |
| --- | --- | --- |
| **Dot Matrix** | LED / halftone poster (density, glow, blend) | – |
| **Fluted Glass** | Vertical ribbed-glass refraction (ribs, refraction, shading) | – |
| **Liquid Sky** | Flowing "viral gradient" screen-blend + warp | ✅ |
| **Glitch** | Chromatic aberration + row displacement | ✅ |
| **Mosaic** | Pixel poster with posterization | – |
| **VHS** | Tape wobble, scanlines, grain, vignette | ✅ |
| **Duotone** | Two-tone poster mapping with hue controls | – |
| **Ripple** | Radial liquid wave distortion | ✅ |

Every effect exposes up to three parameters driven by Material 3 sliders, previews in real time via `RenderEffect` + `RuntimeShader`, and exports at full resolution through a GPU pass (`HardwareRenderer` + `ImageReader`) straight into your gallery (`Pictures/Shader Studio`).

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
