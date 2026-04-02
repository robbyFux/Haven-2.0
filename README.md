# Haven 2.0

Privacy-zentrierte, hochsichere Android-Überwachungsapp.
Kombiniert die sensorische Tiefe der originalen Haven-App mit modernen UX-Konzepten und neuen Funktionen (lokale KI, Change Detection, Gerätemanipulations-Erkennung).

**Leitprinzipien:** Local-first · On-device AI · Self-hosted Backend · Sensorfusion

---

## Features (Phase 1 – MVP)

- Foreground-Service mit WakeLock
- Accelerometer- und Gyroskop-Erkennung mit adaptivem Noise-Floor (10s Warmup)
- Lichtsensor-Erkennung (Lux-Delta)
- Mikrofon-Erkennung (absoluter dB-Schwellwert, nicht relativ)
- Kamera-Bewegungserkennung via Luminanz-Differenz (CameraX ImageAnalysis)
- Room-Datenbank: Events + EventTrigger mit Kaskaden-Loeschung
- Compose-UI: Monitor-Screen (Start/Stop) + Timeline-Screen
- Sensitivitaetsstufen: OFF / LOW / MEDIUM / HIGH

## Geplante Features

| Feature | Phase |
|---|---|
| Sensorfusion (Complementary Filter) | 2 |
| TFLite Person/Objekt-Erkennung | 2 |
| Erkennungszonen (ROI) | 2 |
| Change Detection (pHash/SSIM) | 2 |
| Zeitplaene / Schedules | 3 |
| NotificationEngine (Signal, Mattermost, Push) | 3 |
| WebRTC Live-Stream | 4 |
| Multi-Device Pairing (QR-Code) | 4 |
| Android Keystore Verschluesselung | 5 |
| Geraetemanipulations-Erkennung | 5 |

---

## Tech-Stack

| Komponente | Technologie |
|---|---|
| Sprache | Kotlin |
| UI | Jetpack Compose (Material3) |
| Architektur | MVVM + Clean Architecture |
| DI | Hilt (KSP) |
| Datenbank | Room |
| Kamera | CameraX |
| Async | Coroutines + Flow |
| ML (Phase 2) | TensorFlow Lite |
| Verschluesselung (Phase 5) | Android Keystore + AES-GCM |
| minSdk | 26 (Android 8.0) |
| targetSdk | 35 |

---

## Schnellstart

### Voraussetzungen

- Android Studio Ladybug (2024.2) oder neuer
- JDK 17
- Android-Geraet oder Emulator mit API 26+

### Bauen & Starten

```bash
# Projekt klonen
git clone <repo-url>
cd "Haven 2.0"

# Debug-APK bauen
./gradlew assembleDebug

# Direkt auf angeschlossenem Geraet installieren und starten
./gradlew installDebug
```

### Berechtigungen

Beim ersten Start werden folgende Berechtigungen angefragt:

- **Kamera** – fuer Bewegungserkennung via CameraX
- **Mikrofon** – fuer dB-basierte Geraeuscheerkennung
- **Benachrichtigungen** – fuer den Foreground-Service-Status (Android 13+)

WAKE_LOCK und FOREGROUND_SERVICE werden automatisch vom System erteilt.

### Monitoring starten

1. App oeffnen
2. Auf **Starten** tippen
3. Der Foreground-Service laeuft nun im Hintergrund
4. Ueber **Ereignisse** die Timeline der ausgeloesten Trigger einsehen
5. Auf **Stoppen** tippen (oder ueber die Benachrichtigung) zum Beenden

---

## Projektstruktur

```
app/src/main/java/org/havenapp/main/
├── di/                  # Hilt-Module (DatabaseModule, AppModule)
├── events/              # Domain-Typen: TriggerType, Severity, TriggerEvent
├── media/               # CameraX: LuminanceMotionDetector, CameraAnalyzer
├── sensor/              # SensorMonitor-Interface + Accelerometer/Gyro/Light/Mic
├── storage/             # Room: Entities, DAOs, HavenDatabase, EventRepository
├── ui/
│   ├── theme/           # Compose-Theme (Dark Mode)
│   ├── monitor/         # MonitorScreen + MonitorViewModel
│   └── timeline/        # TimelineScreen + TimelineViewModel
├── HavenApplication.kt  # @HiltAndroidApp
├── MainActivity.kt      # Einstiegspunkt, Permission-Request
└── MonitorService.kt    # LifecycleService: alle Sensor-Flows + CameraX + Room
```

---

## Sensor-Kalibrierung

Accelerometer und Gyroskop messen beim Service-Start **10 Sekunden lang den Noise-Floor**
(Hintergrundrauschen des Geraets) und leiten daraus einen geraeteadaptiven Schwellwert ab:

| Stufe | Multiplikator |
|---|---|
| HIGH | 1.5× Noise-Floor |
| MEDIUM | 2.0× Noise-Floor |
| LOW | 3.0× Noise-Floor |

Das Mikrofon nutzt absolute dB-Schwellwerte (wie Alfred Camera):

| Stufe | Schwellwert |
|---|---|
| HIGH | 50 dB |
| MEDIUM | 60 dB |
| LOW | 70 dB |

---

## Lizenz

Dieses Projekt ist ein Open-Source-Neuaufbau auf Basis von
[Haven](https://github.com/guardianproject/haven) (Apache 2.0).
