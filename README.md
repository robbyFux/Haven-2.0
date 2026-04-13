<!-- GSD:generated -->
<!-- generated-by: gsd-doc-writer -->
# Haven 2.0

Privacy-zentrierte, hochsichere Android-Überwachungsapp (Kotlin).  
Kombiniert sensorische Tiefe (Beschleunigungssensor, Gyroskop, Mikrofon, Licht, Kamera) mit KI-basierter Objekterkennung (MediaPipe EfficientDet) und verschlüsselter lokaler Speicherung — ohne Cloud-Abhängigkeit, ohne Tracking.

**Leitprinzipien:** Local-first · On-device AI · Self-hosted Backend · Sensorfusion

---

## Features

- **Sensorfusion** — Beschleunigungssensor + Gyroskop mit Complementary Filter (α=0,7); Graceful Degradation ohne Gyroskop
- **3-Stufen-Kamera-Pipeline** — Luminanz-Diff → Perceptual Hash (aHash, Hamming) → MediaPipe EfficientDet Lite 0 (Person / Tier / Fahrzeug)
- **Erkennungszonen (ROI)** — Interaktiver Zone-Editor mit Live-Kamera-Preview; normalisierte Koordinaten in DataStore
- **Experten-Schwellwerte** — Feinanpassung der Medium-Stufe pro Sensor via `ExpertSettingsScreen` (`ExpertThresholds`)
- **Verschlüsselte Medien** — AES-256-GCM via Android Keystore (`MediaEncryptionManager`), hardware-backed; Format: `[12-byte IV][ciphertext + GCM tag]`
- **PIN-Sperre** — SHA-256+Salt (`PinHashManager`), konstant-zeitiger Vergleich; `AppLockState` als process-weiter `StateFlow`
- **Kalibrierungs-Wizard** — Automatische Noise-Floor-Bestimmung (90. Perzentil der Warmup-Samples) mit UI-Feedback pro Sensor
- **Stop-Cooldown** — Letzte 30 Sekunden nach Beenden werden retroaktiv aus der DB gelöscht (kein Fehlalarm beim Herangehen)
- **Videoclips** — CameraX `VideoCapture<Recorder>` via `ClipRecorder`; Graceful Degradation auf LEGACY-Hardware

### Benachrichtigungskanäle

| Kanal | Beschreibung |
|---|---|
| `SignalRestChannel` | signal-cli REST API (`POST /v2/send`, Bearer JWT, Base64-Anhänge) |
| `MattermostChannel` | Mattermost Incoming Webhook |
| `PushoverChannel` | Pushover API (Priority-Mapping: LOW/MEDIUM→0, HIGH→1, CRITICAL→2 mit retry+expire) |
| `CloudChannel` | Self-hosted Haven Server (`POST /api/v1/devices/{appKey}/events`, `X-App-Key`-Header) |

### Trigger-Typen

| ID | Typ | Quelle |
|---|---|---|
| 0 | `ACCELEROMETER` | Sensorfusion (FusedMotionMonitor) |
| 1 | `CAMERA` | Luminanz-Diff |
| 2 | `MICROPHONE` | Dezibel-Schwellwert |
| 4 | `LIGHT` | EMA-Baseline |
| 6 | `BUMP` | Significant Motion |
| 7 | `CAMERA_VIDEO` | CameraX VideoCapture |
| 8 | `HEART` | Heartbeat-Ping |
| 9 | `CAMERA_PERSON` | MediaPipe EfficientDet |
| 10 | `CAMERA_PET` | MediaPipe EfficientDet |
| 11 | `CAMERA_VEHICLE` | MediaPipe EfficientDet |
| 14 | `SOUND_DECIBEL` | Absoluter dB-Schwellwert |
| 15 | `GYROSCOPE` | Gyroskop |

---

## Schnellstart

### Voraussetzungen

- Android Studio (aktuell)
- JDK 17
- Android SDK — compileSdk 35
- Android 8.0+ (API 26+) auf dem Zielgerät

### Bauen & Installieren

```bash
# Syntaxcheck (~35 s mit Daemon)
./gradlew :app:compileDebugKotlin

# Debug-APK bauen
./gradlew :app:assembleDebug

# Direkt auf angeschlossenem Gerät installieren
./gradlew :app:installDebug
```

### TFLite-Modell (optional)

Ohne das Modell läuft die App mit reiner Bewegungserkennung (`MOTION_ONLY` — kein Crash):

```bash
curl -L -o app/src/main/assets/efficientdet_lite0.tflite \
  "https://storage.googleapis.com/download.tensorflow.org/models/tflite/task_library/object_detection/android/lite-model_efficientdet_lite0_detection_metadata_1.tflite"
```

### Erste Nutzung

1. App öffnen — bei aktivierter PIN-Sperre erscheint `PinLockScreen`
2. Auf **Starten** tippen → Foreground-Service startet, Kalibrierungs-Wizard läuft
3. **Timeline** zeigt ausgelöste Trigger mit Typ, Severity und optionalem Videoclip
4. Benachrichtigungskanäle unter **Einstellungen** konfigurieren
5. Auf **Stoppen** tippen (oder via Benachrichtigung) — Stop-Cooldown löscht letzte 30 s

---

## Berechtigungen

```
CAMERA, RECORD_AUDIO
FOREGROUND_SERVICE, FOREGROUND_SERVICE_CAMERA, FOREGROUND_SERVICE_MICROPHONE
POST_NOTIFICATIONS, WAKE_LOCK, RECEIVE_BOOT_COMPLETED
INTERNET, ACCESS_NETWORK_STATE
```

Alle Hardware-Features (`camera`, `microphone`, `accelerometer`, `gyroscope`) sind mit `android:required="false"` deklariert.

---

## Sensor-Kalibrierung

### Sensitivity-Enum (Single Source of Truth)

| Stufe | Accel-Multiplikator | Mikrofon | Licht | Kamera (Luma-Diff) |
|---|---|---|---|---|
| `OFF` | ∞ | ∞ | ∞ | ∞ |
| `LOW` | 4,0× | 65 dB | 80 lux | 0,18 |
| `MEDIUM` | 2,0× | 55 dB | 40 lux | 0,08 |
| `HIGH` | 1,2× | 45 dB | 20 lux | 0,04 |

Medium-Stufe ist per `ExpertThresholds` individuell überschreibbar.

### Algorithmen

| Sensor | Algorithmus |
|---|---|
| Accel/Gyro | Warmup 10 s → 90. Perzentil als Noise-Floor; `threshold = noiseFloor × multiplier` |
| Licht | EMA-Baseline (α=0,02); EMA-Snap nach Trigger verhindert Kaskaden |
| Mikrofon | Absoluter dB-Schwellwert |
| Kamera Stufe 1 | Anteil veränderter Pixel (Luma-Diff) |
| Kamera Stufe 2 | 8×8 Average Hash, Hamming-Distanz ≥ 4 |
| Kamera Stufe 3 | MediaPipe EfficientDet Lite 0 (nur bei bestätigter Bewegung) |

---

## Architektur

```
app/src/main/java/org/havenapp/main/
├── sensor/          # SensorCore: FusedMotionMonitor, LightMonitor, MicrophoneMonitor, ExpertThresholds
├── media/           # CameraAnalyzer (3-Stufen), ClipRecorder (VideoCapture)
├── detection/       # SensorFusionEngine, PerceptualHashDetector, HavenObjectDetector (MediaPipe)
├── events/          # TriggerEvent, TriggerType (IDs 0–15), Severity
├── storage/         # HavenDatabase (Room), SettingsRepository (DataStore), EventRepository, AppLogger
├── notify/          # NotificationRouter, SignalRestChannel, MattermostChannel, PushoverChannel, CloudChannel
├── security/        # MediaEncryptionManager (AES-256-GCM/Keystore), PinHashManager (SHA-256+Salt)
├── di/              # Hilt-Module: AppModule, DatabaseModule, DataStoreModule, NetworkModule
└── ui/              # Compose: Monitor · Timeline · Settings · ZoneEditor · ExpertSettings · Diagnostics · Lock
```

**Datenfluss:** Sensoren → `Flow<TriggerEvent>` → `MonitorService` → Room + `StateFlow` → ViewModel → Compose UI

**Navigation:** Bottom Nav (Monitor · Timeline · Settings); sekundäre Screens (Diagnostics, ZoneEditor, ExpertSettings, EventDetail) via `NavHost`; `PinLockScreen` als Gate vor dem gesamten Nav-Graph.

---

## Tech Stack

| Komponente | Version |
|---|---|
| Kotlin | 2.0.20 |
| AGP | 8.6.0 |
| Compose BOM | 2024.09.03 |
| CameraX | 1.5.2 |
| MediaPipe Tasks Vision | 0.10.29 |
| Hilt | 2.51.1 |
| Room | 2.6.1 |
| DataStore Preferences | 1.1.1 |
| Media3 ExoPlayer | 1.6.0 |
| OkHttp | 4.12.0 |
| minSdk | 26 (Android 8.0) |
| targetSdk / compileSdk | 35 |

---

## Server (optional)

Unter `server/` liegt ein self-hosted Backend (Python/FastAPI + Django WebUI + PostgreSQL) für Cloud-Benachrichtigungen und Event-Archivierung. Quickstart: `server/QUICKSTART.md` bzw. `Cloud Server QUICKSTART.md`.

---

## Lizenz

Dieses Projekt basiert auf [Haven](https://github.com/guardianproject/haven) (Apache 2.0).
