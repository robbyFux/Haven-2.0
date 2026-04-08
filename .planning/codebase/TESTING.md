# Testing

**Analysis Date:** 2026-03-30

## Test Infrastructure

**Test dependencies declared** (`gradle/libs.versions.toml`, `app/build.gradle.kts`):
- `junit:junit:4.13.2` — JUnit 4, for local unit tests (`testImplementation`)
- `androidx.test.ext:junit:1.2.1` — AndroidX JUnit runner wrapper (`androidTestImplementation`)
- `androidx.compose.ui:ui-test-junit4` (via Compose BOM) — Compose UI testing (`androidTestImplementation`)
- `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` configured

**Not present (notable absences):**
- No MockK or Mockito dependency
- No Turbine (Flow testing)
- No Robolectric
- No Hilt testing support (`hilt-android-testing`)
- No coroutines-test dependency (`kotlinx-coroutines-test`)
- No Room in-memory test helpers declared
- No screenshot testing library

**Test source sets:**
- `app/src/test/` — does NOT exist (no local unit tests written)
- `app/src/androidTest/` — does NOT exist (no instrumented tests written)

## Test Types

**Unit Tests:** None written. No files in `src/test/`.

**Instrumented Tests:** None written. No files in `src/androidTest/`.

**UI / Compose Tests:** None written, though `compose-ui-test` dependency is declared.

**E2E Tests:** Not applicable (Android app, no E2E framework configured).

**Manual testing** is the only testing approach currently in use.

## Test Coverage

**Approximate coverage: 0%**

No automated test exists in the project. All business logic, sensor algorithms, and UI flows are untested by automated means.

**Areas with highest testability and value to cover first:**

**Pure logic (no Android dependencies — easiest to unit test):**
- `app/src/main/java/org/havenapp/main/detection/SensorFusionEngine.kt` — pure math, no Android API
- `app/src/main/java/org/havenapp/main/detection/PerceptualHashDetector.kt` — pure algorithm, ByteArray input
- `app/src/main/java/org/havenapp/main/media/LuminanceMotionDetector.kt` — pure algorithm, ByteArray input
- `app/src/main/java/org/havenapp/main/detection/DetectionZone.kt` — pure data class, serialization/deserialization, pixel bounds calculation
- `app/src/main/java/org/havenapp/main/events/TriggerType.kt` — `fromId()` companion function

**Repository / DataStore (testable with fakes):**
- `app/src/main/java/org/havenapp/main/storage/SettingsRepository.kt` — DataStore read/write, enum deserialization fallback
- `app/src/main/java/org/havenapp/main/storage/EventRepository.kt` — Room DAO wrappers

**ViewModel logic (testable with fakes + coroutines-test):**
- `app/src/main/java/org/havenapp/main/ui/settings/SettingsViewModel.kt` — `combine()` state composition
- `app/src/main/java/org/havenapp/main/ui/diagnostics/DiagnosticsViewModel.kt` — combined state from multiple sources

**Sensor detection thresholds (boundary value tests):**
- `Sensitivity` enum values and their effect on `FusedMotionMonitor` noise floor calculation
- `LightMonitor` EMA drift behavior at configured alpha

## Testing Patterns

**No established patterns yet** — the following are recommendations based on existing code structure.

**Suggested unit test structure for pure logic classes:**
```kotlin
// Example: SensorFusionEngineTest.kt
class SensorFusionEngineTest {
    private lateinit var engine: SensorFusionEngine

    @Before fun setUp() { engine = SensorFusionEngine() }

    @Test fun `fused score is zero when uninitialized`() { ... }
    @Test fun `gyro zero reduces fused score to 30 percent accel delta`() { ... }
    @Test fun `reset clears state`() { ... }
}
```

**Suggested structure for DetectionZone:**
```kotlin
class DetectionZoneTest {
    @Test fun `fromString roundtrips through serialize`() { ... }
    @Test fun `fromString returns null for malformed input`() { ... }
    @Test fun `toPixelBounds clamps to image dimensions`() { ... }
    @Test fun `init throws when left not less than right`() { ... }
}
```

**For Flow-based monitors** (`FusedMotionMonitor`, `LightMonitor`), the `callbackFlow` pattern requires either:
- Robolectric with a real `SensorManager` fake
- Extraction of the detection math into a pure function that can be tested independently of the Flow wrapper (recommended refactor path)

**For ViewModel tests**, the recommended approach once `kotlinx-coroutines-test` is added:
```kotlin
@HiltAndroidTest
class SettingsViewModelTest {
    // Use TestCoroutineDispatcher + hiltRule
    // Fake SettingsRepository via Hilt test module
}
```

**Mocking approach:**
No mocking library is present. When tests are added, MockK is preferred for Kotlin (suspend functions, coroutines, extension functions). Hilt `@TestInstallIn` modules should replace real singletons in instrumented tests.

**Test data:**
No fixture files or factory helpers exist. When added, place them in:
- `app/src/test/java/org/havenapp/main/fixtures/` for unit test helpers
- `app/src/androidTest/java/org/havenapp/main/fixtures/` for instrumented test helpers

## CI Integration

No CI pipeline configured. No `.github/workflows/`, no `Jenkinsfile`, no `bitrise.yml` found in the project root.

**To run the declared test targets manually:**
```bash
# Local unit tests (src/test/ — currently empty)
./gradlew test

# Instrumented tests on device/emulator (src/androidTest/ — currently empty)
./gradlew connectedAndroidTest

# Build only (what CI would run as a smoke check)
./gradlew assembleDebug
```

## Prioritized Test Backlog

Given the current zero-coverage state, the highest-value tests to write first (ordered by ease and impact):

1. `SensorFusionEngine` — pure math, zero dependencies, critical to motion detection correctness
2. `PerceptualHashDetector` — pure algorithm, correctness of hash distance separating flicker from real motion
3. `LuminanceMotionDetector` — pure algorithm, pixel diff fraction calculation
4. `DetectionZone` — serialization roundtrip, `fromString` null safety, `toPixelBounds` clamping
5. `TriggerType.fromId()` — boundary: valid IDs, unknown IDs, null return
6. `Sensitivity` enum — threshold values accessible, `OFF` returns `Float.MAX_VALUE`
7. `SettingsRepository` — DataStore read/write, enum fallback on unknown stored value
8. `CameraAnalyzer.deriveSeverity()` — severity tier boundaries relative to `motionThreshold`
