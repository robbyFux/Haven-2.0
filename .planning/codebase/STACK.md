# Tech Stack

**Analysis Date:** 2026-03-30

## Languages

**Primary:**
- Kotlin 2.0.20 - All application code under `app/src/main/java/org/havenapp/main/`

**Secondary:**
- None (Java fully replaced; no new Java files)

## Runtime

**Environment:**
- Android SDK, minSdk 26 (Android 8.0 Oreo), targetSdk 35, compileSdk 35
- JVM target: Java 17 (`sourceCompatibility = JavaVersion.VERSION_17`)

**Package Manager:**
- Gradle 8.9 (via Gradle Wrapper at `gradle/wrapper/gradle-wrapper.properties`)
- Version catalog: `gradle/libs.versions.toml`
- Lockfile: None (no `gradle.lockfile`)

## Frameworks

**Core Application:**
- AppCompat 1.7.0 - `AppCompatActivity` in `MainActivity.kt` (required for per-app locale switching)
- AndroidX Core KTX 1.15.0 - Kotlin extensions throughout
- AndroidX Activity Compose 1.9.3 - Compose integration in `MainActivity.kt`

**UI:**
- Jetpack Compose BOM 2024.09.03 - All UI under `app/src/main/java/org/havenapp/main/ui/`
- Material 3 (compose-material3) - `HavenTheme` in `ui/theme/Theme.kt`, Dynamic Color on Android 12+
- Material Icons Extended (compose-material-icons) - Icons in all screens
- Navigation Compose 2.8.3 - `HavenNavGraph.kt` defines routes: MONITOR, TIMELINE, SETTINGS, DIAGNOSTICS, ZONE_EDITOR, EVENT_DETAIL

**Lifecycle:**
- Lifecycle ViewModel KTX + Compose 2.8.6 - All ViewModels (`MonitorViewModel`, `TimelineViewModel`, `SettingsViewModel`, `DiagnosticsViewModel`, `ZoneEditorViewModel`, `EventDetailViewModel`)
- Lifecycle Runtime KTX + Compose 2.8.6 - `collectAsStateWithLifecycle()` in Composables
- Lifecycle Service 2.8.6 - `MonitorService` extends `LifecycleService`

**Dependency Injection:**
- Hilt 2.51.1 - `@HiltAndroidApp` in `HavenApplication.kt`, `@AndroidEntryPoint` in `MonitorService.kt`
- Hilt Navigation Compose 1.2.0 - `hiltViewModel()` in nav graph
- KSP 2.0.20-1.0.25 - Code generation for Hilt and Room

**Database:**
- Room 2.6.1 - `HavenDatabase` in `storage/HavenDatabase.kt`, two entities: `EventEntity`, `EventTriggerEntity`

**Camera:**
- CameraX Core 1.4.0 - `ImageAnalysis` in `MonitorService.kt`
- CameraX Camera2 1.4.0 - Camera2 backend implementation
- CameraX Lifecycle 1.4.0 - `ProcessCameraProvider` lifecycle binding
- CameraX View 1.4.0 - `PreviewView` in `ZoneEditorScreen.kt`

**Machine Learning:**
- TensorFlow Lite Task Vision 0.4.4 - `HavenObjectDetector` in `detection/HavenObjectDetector.kt`
  - Model: EfficientDet Lite 0 (COCO, ~4 MB), loaded from `assets/efficientdet_lite0.tflite`
  - Note: model file must be downloaded separately (not committed); graceful degradation without it

**Preferences/Settings:**
- DataStore Preferences 1.1.1 - `SettingsRepository` in `storage/SettingsRepository.kt`

**Async:**
- Kotlinx Coroutines Android 1.8.1 - `callbackFlow`, `StateFlow`, `lifecycleScope` throughout sensors and service

## Build System

**Build Tool:** Gradle 8.9 with Kotlin DSL
**Config files:**
- `build.gradle.kts` - root project config (plugins only)
- `app/build.gradle.kts` - application module (AGP 8.5.2, dependencies)
- `gradle/libs.versions.toml` - version catalog (single source of truth for all versions)
- `settings.gradle.kts` - repository declarations, single `:app` module
- `gradle.properties` - JVM heap (`-Xmx2048m`), AndroidX enabled, parallel builds, official Kotlin code style
- `app/proguard-rules.pro` - ProGuard rules for release builds

**Build types:**
- `debug`: no minification, applicationId suffix `.debug`
- `release`: minify + shrink resources enabled, ProGuard applied

**Special build config:**
- `buildConfig = true` - generates `BuildConfig.VERSION_NAME` / `VERSION_CODE`
- `noCompress += "tflite"` - TFLite model files excluded from compression

## Runtime Requirements

**Development:**
- Android Studio (current)
- JDK 17
- Android SDK with compileSdk 35 tools

**Production:**
- Android 8.0+ (API 26+)
- Camera hardware: optional (`android:required="false"`)
- Gyroscope: optional, graceful degradation to accelerometer-only in `FusedMotionMonitor.kt`
- TFLite model: optional, graceful degradation to motion-only detection

## Key Dependencies (with versions)

| Dependency | Version | Location |
|---|---|---|
| AGP (Android Gradle Plugin) | 8.5.2 | `libs.versions.toml` |
| Kotlin | 2.0.20 | `libs.versions.toml` |
| KSP | 2.0.20-1.0.25 | `libs.versions.toml` |
| Compose BOM | 2024.09.03 | `libs.versions.toml` |
| Hilt | 2.51.1 | `libs.versions.toml` |
| Room | 2.6.1 | `libs.versions.toml` |
| CameraX | 1.4.0 | `libs.versions.toml` |
| TFLite Task Vision | 0.4.4 | `libs.versions.toml` |
| DataStore Preferences | 1.1.1 | `libs.versions.toml` |
| Coroutines | 1.8.1 | `libs.versions.toml` |
| Lifecycle | 2.8.6 | `libs.versions.toml` |
| Navigation Compose | 2.8.3 | `libs.versions.toml` |
| AppCompat | 1.7.0 | `libs.versions.toml` |
| Core KTX | 1.15.0 | `libs.versions.toml` |
| JUnit | 4.13.2 | `libs.versions.toml` |
| AndroidX Test Ext JUnit | 1.2.1 | `libs.versions.toml` |

---

*Stack analysis: 2026-03-30*
