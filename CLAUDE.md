# CLAUDE.md – Haven 2.0 Projektkontext

## Projektziel

Haven 2.0 ist eine **privacy-zentrierte, hochsichere Android-Überwachungsapp** (Kotlin).
Kombiniert die sensorische Tiefe der originalen Haven-App mit modernen UX-Konzepten aus Alfred Camera,
plus neuen differenzierenden Funktionen (lokale KI, Change Detection, Gerätemanipulations-Erkennung).

**Leitprinzipien:** Local-first · On-device AI · Self-hosted Backend · Sensorfusion

---

## Zielplattform

- **Android**, Kotlin, targetSdk 35, minSdk 26
- Kamera: **CameraX** (Jetpack)
- ML: **TensorFlow Lite** (quantisierte Modelle, on-device)
- Streaming: **WebRTC**
- Verschlüsselung: **Android Keystore + AES-GCM**
- Architektur: **MVVM + Clean Architecture**, Jetpack Compose UI
- DI: Hilt
- DB: Room
- Async: Kotlin Coroutines + Flow

---

## Modulare Architektur

```
app/
├── sensor/          # SensorCore: Accelerometer, Gyro, Magnetometer, Light, Audio
├── media/           # MediaCore: CameraX, AudioRecorder
├── detection/       # DetectionEngine: Heuristiken + TFLite ML
├── events/          # EventEngine: Regelwerk, Priorisierung, Eskalation
├── storage/         # SecureStorage: Room + verschlüsselte Medien
├── network/         # RTC/Networking: WebRTC, Signaling, TURN
├── notify/          # NotificationEngine: abstrahierte Benachrichtigungskanäle
└── ui/              # Jetpack Compose: Monitoring, Timeline, Settings
```

---

## Referenz-Apps – Erkenntnisse

### Haven 0.2.1 (referenzen/haven-0.2.1/)
**Übernehmen:**
- EventTrigger-Typen: ACCELEROMETER(0), CAMERA(1), MICROPHONE(2), PRESSURE(3), LIGHT(4), POWER(5), BUMP(6), CAMERA_VIDEO(7), HEART(8)
- MonitorService als Foreground Service mit WakeLock
- Luminanz-basierte Bewegungserkennung (Pixel-Differenz) als Fallback
- Room-Datenbank (HavenEventDB) mit Event + EventTrigger Schema
- Messenger-IPC zwischen Monitoren und Service

**Verbessern / Ersetzen:**
- Java → vollständig Kotlin
- Legacy Camera API → CameraX
- SharedPreferences → DataStore
- Messenger-IPC → Kotlin Coroutines/Flow/StateFlow
- Signal-Only-Alerts → flexibles Benachrichtigungssystem (FCM + selbstgehostet)
- Kein Gyroskop → Sensorfusion mit Complementary/Kalman Filter
- Kein ML → TFLite-basierte Personenerkennung

### AlfredCamera (referenzen/AlfredCamera/)

**EventTrigger / Detection-Typen (Protobuf `DetectionModeSetting`):**
```
DetectionMode:
  MODE_DEFAULT(0)   // alle Bewegungen
  MODE_MOTION(1)    // reine Bewegungserkennung (kein ML)
  MODE_PERSON(2)    // Person (TFLite)
  MODE_PET(3)       // Tier (TFLite)
  MODE_VEHICLE(4)   // Fahrzeug (TFLite)

Sensitivity:
  SENSITIVITY_DEFAULT(0) / LOW(1) / MEDIUM(2) / HIGH(3)

CustomDetectionSetting.context:
  CONTEXT_CONTINUOUS(0)  // dauerhaft aktiv
  CONTEXT_STOP(1)        // Auslösung bei Bewegungsstopp
  CONTEXT_ABSENT(2)      // Auslösung bei Abwesenheit einer Person
  CONTEXT_LINGER(3)      // Auslösung bei längerem Verweilen
  + params: String       // JSON-Parameter pro Modus
```

**Sound-Detection (`SoundDetectionModeSetting`):**
```
enabled: Boolean
decibelDetection:
  enabled: Boolean
  threshold: Int   // absoluter Dezibel-Schwellwert (nicht relativ!)
```

**Erkennungszonen (`DetectionZoneSetting`):**
```
enabled: Boolean
zones: List<Zone>   // Koordinaten-Polygone
shapeType: POLYGON(0) | RECTANGLE(1)
```

**Zeitplan (`DetectionScheduleSetting`):**
```
enabled: Boolean
schedule: String    // wahrscheinlich cron-ähnliches JSON-Format
```

**Pro-Event gespeicherte Konfig (`EventConfig`):**
```
sensitivity: Int    // Sensitivität zum Zeitpunkt des Events
aiModel: String     // welches TFLite-Modell hat detektiert
isZoomed: Boolean   // war digitaler Zoom aktiv
isDz: Boolean       // war eine DetectionZone aktiv
```

**Weitere Settings (`CameraSettingsResponse`):**
- `DetectionBoundingBoxSetting` – Bounding Boxes im Live-Feed ein/aus
- `DetectionReminderSetting` – Benachrichtigung wenn lange keine Detektion
- `LowLightFilterSetting` – Nachtsicht-Filter
- `ContinuousRecordingSetting` – Dauerhaft-Aufnahme
- `ZoomInLockSetting` – digitalen Zoom sperren
- `ObjectDetectionOptimization` – enabled(default:false) + threshold(default:50)

**UX-Aktivitäten übernehmen:**
- `MotionDetectionScheduleActivity` → Schedule/Zeitplan-System
- `TrustCircleSettingActivity` → Multi-Device Pairing (QR-Code-basiert)
- `DeviceManagementActivity` → Geräteverwaltung mit Rollen (Admin/Viewer/ReadOnly)
- `EventBook` / `EventBookGrid` → Ereignis-Timeline mit Filterfunktion
- `EventPlayerActivity` → Clip-Playback mit Metadaten
- `LiveActivity` / `ViewerActivity` → WebRTC Live-Stream UI
- `DetectionSettingActivity` → Erkennungszonen (ROI) im Kamerabild
- `CameraHealthViewerActivity` → Gerätestatus-Monitoring

**Nicht übernehmen:**
- Google Ads / AdServices
- Firebase Analytics Tracking
- Cloud-only Speicherung
- Closed-source Account-System

---

## Funktionale Anforderungen (Kurzreferenz)

| Feature | Priorität | Herkunft |
|---|---|---|
| Sensor-Monitoring (Accel, Gyro, Mic, Light, Baro) | P0 | Haven |
| Kamera-Bewegungserkennung (Luminanz + ML) | P0 | Haven + Alfred |
| Foreground Service + WakeLock | P0 | Haven |
| Ereignis-Timeline + Archiv | P0 | Haven + Alfred |
| Verschlüsselte lokale Speicherung | P0 | Haven |
| Sensorfusion (Complementary/Kalman Filter) | P1 | Neu |
| Kalibrierungs-Wizard | P1 | Neu |
| TFLite Person/Objekt-Erkennung | P1 | Neu |
| Erkennungszonen (ROI) | P1 | Alfred |
| Zeitpläne / Schedules | P1 | Alfred |
| Change Detection (pHash/SSIM) | P1 | Neu |
| NotificationEngine (abstrahierte Kanäle) | P1 | Neu |
| SignalRestChannel (signal-cli REST API) | P1 | Haven (neu impl.) |
| NotificationRules (Trigger → Kanal) | P1 | Neu |
| Heartbeat via Signal | P1 | Haven (neu impl.) |
| MattermostChannel (Incoming Webhook) | P1 | Neu |
| SignalIntentChannel (Fallback) | P2 | Neu |
| WebRTC Live-Stream | P2 | Alfred |
| Multi-Device Pairing (QR-Code) | P2 | Alfred |
| Zwei-Wege-Audio | P2 | Alfred |
| Telefon-Manipulation-Erkennung | P2 | Neu |
| Self-hosted Backend (Signaling + TURN + signal-cli) | P2 | Neu |

---

## Android-Berechtigungen

```
CAMERA, RECORD_AUDIO, FOREGROUND_SERVICE,
FOREGROUND_SERVICE_CAMERA, FOREGROUND_SERVICE_MICROPHONE,
POST_NOTIFICATIONS, WAKE_LOCK, RECEIVE_BOOT_COMPLETED
```

---

## Projektstruktur (aktuell)

```
Haven 2.0/
├── CLAUDE.md
├── PROJECT.md
├── referenzen/
│   ├── haven-0.2.1/     # Original Haven App (Java/Kotlin)
│   └── AlfredCamera/    # Alfred APK (decompiled, Referenz)
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── assets/
        │   └── efficientdet_lite0.tflite  # TFLite Modell (manuell hinzufügen, ~4 MB)
        └── java/org/havenapp/main/
            ├── MainActivity.kt              # AppCompatActivity (required for locale switching)
            ├── MonitorService.kt            # Foreground Service, State Machine
            ├── sensor/
            │   ├── MonitorState.kt          # IDLE / COUNTDOWN / CALIBRATING / ACTIVE
            │   ├── Sensitivity.kt           # Enum: accelerometerMultiplier, microphoneThresholdDb, lightDeltaLux, cameraMotionThreshold
            │   ├── SensorMonitor.kt         # Interface: observe(sensitivity, warmupMs)
            │   ├── FusedMotionMonitor.kt    # Phase 2: Accel+Gyro Complementary Filter
            │   ├── AccelerometerMonitor.kt  # (veraltet, nicht mehr in MonitorService)
            │   ├── GyroscopeMonitor.kt      # (veraltet, nicht mehr in MonitorService)
            │   ├── LightMonitor.kt          # EMA-Baseline, emaBaseline: StateFlow<Float?>
            │   ├── MicrophoneMonitor.kt     # Dezibel-basiert, absoluter Schwellwert
            │   └── CameraPosition.kt        # BACK / FRONT
            ├── media/
            │   ├── CameraAnalyzer.kt        # 3-Stufen: Luma-Diff → pHash → TFLite
            │   └── LuminanceMotionDetector.kt  # Stufe 1: pixelweiser Luma-Vergleich
            ├── detection/
            │   ├── SensorFusionEngine.kt    # Complementary Filter (alpha=0.7)
            │   ├── PerceptualHashDetector.kt # 8×8 aHash, Hamming-Distanz
            │   ├── DetectionMode.kt         # MOTION_ONLY / PERSON / PET / VEHICLE / ALL
            │   ├── DetectionZone.kt         # Normalisiertes Rechteck (0.0–1.0), toPixelBounds(), serialize()
            │   └── HavenObjectDetector.kt   # TFLite Task Library Wrapper (EfficientDet), isAvailable: Boolean
            ├── events/
            │   ├── TriggerType.kt
            │   ├── TriggerEvent.kt
            │   └── Severity.kt              # LOW / MEDIUM / HIGH / CRITICAL
            ├── storage/
            │   ├── SettingsRepository.kt    # DataStore: Sensitivity, Camera, DetectionMode, DetectionZone, …
            │   └── EventRepository.kt       # Room wrapper; discardTriggersSince() für Stop-Cooldown
            ├── di/
            │   └── DataStoreModule.kt
            └── ui/
                ├── HavenNavGraph.kt         # Routes: MONITOR, TIMELINE, SETTINGS, DIAGNOSTICS, ZONE_EDITOR, EVENT_DETAIL
                ├── monitor/                 # MonitorScreen + ViewModel (Kalibrierungs-Wizard)
                ├── timeline/                # TimelineScreen, EventDetailScreen + ViewModels
                ├── settings/                # SettingsScreen + ViewModel + ZoneEditorScreen + ZoneEditorViewModel
                └── diagnostics/             # DiagnosticsScreen + ViewModel (Settings + Kalibrierungsstrategie + Trigger-Log)
```

---

## Entwicklungsphasen

### Phase 1 – Foundation (MVP Sensor Core) ✅ ABGESCHLOSSEN
- Android-Projekt aufgesetzt (Kotlin, Hilt, Room, CameraX, Compose)
- MonitorService (Foreground, Kotlin Coroutines, WakeLock)
- State Machine: IDLE → COUNTDOWN → CALIBRATING → ACTIVE
- Accelerometer + Gyro + Light + Mic Monitor (Kotlin, callbackFlow)
- Kamera-Bewegungserkennung (Luminanz-Differenz via CameraX ImageAnalysis)
- Room-Datenbank: HavenEvent + EventTriggerEntity
- UI: MonitorScreen (State-aware), Timeline, EventDetail, Settings, Diagnostics
- DataStore Settings: Sensitivity, CameraPosition, Countdown, Calibration, Language
- Sprachen: DE + EN mit AppCompatDelegate per-app locale
- Versionierung: BuildConfig.VERSION_NAME / VERSION_CODE
- Diagnostics-Screen: Settings-Zusammenfassung + Trigger-Log + Share-Export

### Phase 2 – Detection Intelligence ✅ ABGESCHLOSSEN
- ✅ Sensorfusion (Complementary Filter) → `FusedMotionMonitor` + `SensorFusionEngine`
- ✅ Change Detection (pHash) → `PerceptualHashDetector` als Stufe 2 in CameraAnalyzer
- ✅ Kalibrierungs-Wizard → Live-Noise-Floor-Anzeige in MonitorScreen während CALIBRATING
- ✅ TFLite-Integration → `HavenObjectDetector` (EfficientDet Lite 0), DetectionMode-Setting
- ✅ Erkennungszonen (ROI) → `DetectionZone`, `ZoneEditorScreen` mit Live-Kamera-Preview + Touch-to-draw Canvas + Dim-Overlay
- ✅ Diagnostics erweitert → TFLite-Status, Kalibrierungsstrategie (alle Sensoren + Schwellwerte + Runtime-Werte), Stop-Cooldown
- ✅ Stop-Cooldown → letzte 30 s beim Beenden retroaktiv aus DB gelöscht (`STOP_COOLDOWN_SECONDS = 30`)

### Phase 3 – UX & Scheduling
- Zeitpläne (Armed/Disarmed/Night/Presence-Aware)
- Ereignis-Timeline mit Filter + Clip-Player
- Settings mit DataStore
- **NotificationEngine**: `NotificationChannel`-Interface + `LocalPushChannel`
- **SignalRestChannel**: Anbindung an self-hosted signal-cli REST API
  - Konfigurationsflow (Backend-URL, JWT, Empfängernummern, Test-Nachricht)
  - Heartbeat-Timer (konfigurierbar, Standard 30 min)
  - Medien-Anhänge (Foto/Audio) bei MEDIUM/HIGH/CRITICAL Events
- **MattermostChannel**: Incoming Webhook, kein Backend-Dienst nötig
  - Konfigurationsflow (Webhook-URL, Test-Nachricht)
  - Markdown-formatierte Alert-Nachrichten
  - Heartbeat über gleichen Kanal konfigurierbar
- **NotificationRule**-Editor in Settings (welche Trigger → welche Kanäle)
- **SignalIntentChannel** als optionaler Fallback (lokale Signal-App)

### Phase 4 – Networking & Multi-Device
- WebRTC Live-Stream
- Self-hosted Signaling-Backend (enthält auch signal-cli REST API als Dienst)
- QR-Code Pairing
- Geräteverwaltung (Admin/Viewer/ReadOnly)
- Zwei-Wege-Audio

### Phase 5 – Hardening & Polish
- Android Keystore Verschlüsselung aller Medien
- Telefon-Manipulations-Erkennung
- Batterie-Optimierung
- Audit-Logs
- Export- und Löschfunktionen

---

## Signal-Anbindung – Analyse & Strategie

### Warum die Haven 0.2.1 Implementierung veraltet ist

Haven 0.2.1 bettet **signal-cli** (`org.asamk.signal.Main`) direkt als Library ein
und ruft sie per `AsyncTask` + `Namespace`-HashMap auf. Das ist aus mehreren Gründen nicht mehr nutzbar:

- `signal-cli` hat seit ~2021 interne Umstrukturierungen durchlaufen (v0.10+ / v0.11+)
  – das alte Embedding-API (`Main.handleCommands(Namespace)`) existiert so nicht mehr
- Signal hat das Registrierungsprotokoll mehrfach geändert (Captcha-Pflicht, neue Endpoints)
- `AsyncTask` ist seit API 30 deprecated und in API 33 entfernt
- Die App müsste eine **eigene Signal-Nummer** registrieren und verwalten –
  inklusive SMS/Anruf-Verifikation, was auf einem dedizierten Gerät schwierig ist
- Kein offizielles Android-SDK von Signal; alle Drittlösungen arbeiten gegen interne APIs

### Moderne Ansätze (Bewertung)

| Ansatz | Beschreibung | Pro | Contra |
|---|---|---|---|
| **Intent an Signal-App** | `ACTION_SEND` / `ACTION_SENDTO` an `org.thoughtcrime.securesms` | Keine Registrierung, kein Protokoll-Risiko | Signal muss installiert sein, kein Anhang ohne UI-Interaktion |
| **signal-cli REST API** (self-hosted) | signal-cli als Docker-Dienst auf dem Backend; App ruft HTTP-Endpoint auf | Volles Feature-Set (Anhänge, Gruppen, Heartbeat), sauber entkoppelt | Erfordert Backend; signal-cli muss einmalig registriert werden |
| **Molly / Molly-FOSS** | Signal-Fork mit REST-Interface | FOSS, aktiv gepflegt | Andere App erforderlich |

**Entscheidung für Haven 2.0:**
- **Primär**: signal-cli REST API auf dem self-hosted Backend (gleicher Server wie TURN/Signaling)
- **Fallback (optional)**: Intent an installierte Signal-App für einfache Text-Alerts ohne Backend
- **Kein direktes SDK-Embedding** mehr – zu fragil

### Abstrahiertes NotificationEngine-Design

Signal ist **ein** Kanal unter mehreren. Die Architektur muss erweiterbar bleiben:

```kotlin
// notify/NotificationChannel.kt
interface NotificationChannel {
    val id: String
    suspend fun send(event: HavenEvent, attachment: File?): Result<Unit>
    suspend fun sendHeartbeat(message: String): Result<Unit>
}

// Implementierungen:
// notify/channel/SignalRestChannel.kt      – signal-cli REST API
// notify/channel/SignalIntentChannel.kt    – Intent an lokale Signal-App
// notify/channel/MattermostChannel.kt      – Mattermost Incoming Webhook
// notify/channel/LocalPushChannel.kt       – Android-Benachrichtigung
// (erweiterbar: Matrix, ntfy, Webhook, ...)

// notify/NotificationRouter.kt
class NotificationRouter(
    private val channels: List<NotificationChannel>,
    private val rules: List<NotificationRule>
) {
    suspend fun route(event: HavenEvent) { ... }
}
```

### Event-basierter Auslöse-Flow

```
HavenEvent ausgelöst
  → EventEngine bewertet Schwere (LOW / MEDIUM / HIGH / CRITICAL)
  → NotificationRouter prüft Regeln:
       - Welche Kanäle für welche EventTrigger-Typen?
       - Welche Sensitivitätsschwelle?
       - Ist Quiet-Hours aktiv?
       - Cooldown seit letzter Benachrichtigung?
  → Ausgewählte Kanäle senden parallel (Coroutines)
  → Ergebnis wird in AuditLog persistiert
```

### NotificationRule-Modell

```kotlin
data class NotificationRule(
    val triggerTypes: Set<TriggerType>,   // z.B. CAMERA_PERSON, MICROPHONE
    val minSeverity: Severity,            // LOW / MEDIUM / HIGH / CRITICAL
    val channels: Set<String>,            // Channel-IDs
    val cooldownMs: Long,                 // Mindestabstand zwischen Alerts
    val attachMedia: Boolean,             // Foto/Audio-Anhang mitsenden?
    val quietHours: TimeRange?,           // Keine Benachrichtigungen in Zeitfenster
)
```

### SignalRestChannel – Implementierungsdetails

```
Backend: signal-cli >= 0.11 als REST-API (Docker: bbernhard/signal-cli-rest-api)
Endpoint: POST /v2/send
Auth:     Bearer Token (JWT, gleiche Infrastruktur wie WebRTC-Signaling)

Payload:
  message:     String        (Alert-Text mit Timestamp, Trigger-Typ, Gerätename)
  recipients:  List<String>  (E.164-Nummern oder Gruppen-IDs)
  base64_attachments: List?  (Foto/Audio als Base64, nur bei attachMedia=true)

Heartbeat:
  Periodischer Lebenszeichen-Ping (konfigurierbar, Standard: 30 min)
  Enthält: Gerätename, Akkustand, Uptime, Anzahl Events seit letztem Heartbeat
```

### Signal-Einrichtungsflow in der App (Settings)

1. Nutzer trägt Backend-URL + JWT-Token ein (oder scannt QR)
2. App testet Verbindung (GET /v1/health)
3. Nutzer trägt Empfänger-Nummer(n) ein (E.164)
4. App sendet Test-Nachricht
5. Konfiguration wird verschlüsselt in DataStore gespeichert (Android Keystore)

### MattermostChannel – Implementierungsdetails

Mattermost unterstützt **Incoming Webhooks** – keine eigene App, keine Registrierung,
nur eine Webhook-URL aus dem Mattermost-Server-Admin-Bereich.

```
Endpoint:  POST <webhook-url>  (selbst konfiguriert, z.B. https://mattermost.example.com/hooks/xxx)
Auth:      In der Webhook-URL enthalten (kein separater Token nötig)
Format:    JSON  { "text": "...", "username": "Haven", "icon_emoji": ":shield:" }
           Markdown wird unterstützt → strukturierte Alert-Nachrichten möglich

Alert-Nachricht (Beispiel):
  **Haven Alert** – [Gerätename]
  Trigger: Person erkannt (TFLite, confidence 87%)
  Zeit: 2026-03-23 14:32:11
  Modus: Armed | Zone: Eingang aktiv
  [Foto-Anhang als URL oder Base64 – nur wenn self-hosted Mattermost mit Dateiupload]

Heartbeat:
  Gleiche Logik wie Signal-Heartbeat – über NotificationRouter konfigurierbar

Einrichtung in der App:
  1. Nutzer trägt Webhook-URL ein
  2. App sendet Test-Nachricht (POST mit {"text":"Haven Test"})
  3. URL wird verschlüsselt in DataStore gespeichert
```

**Vorteil gegenüber Signal:** Kein Backend-Dienst nötig – der Mattermost-Server des Nutzers
ist bereits vorhanden. Incoming Webhooks funktionieren ohne signal-cli-Infrastruktur.

### Berechtigungen (nur bei Intent-Fallback nötig)

Kein zusätzlicher Permission-Bedarf für REST/Webhook-Ansätze (nur INTERNET, bereits vorhanden).
Für Intent-Fallback: keine extra Permissions, aber Signal muss installiert sein.

---

## Sensor-Kalibrierung – Analyse & Strategie

### Das Problem in Haven 0.2.1
- `AccelerometerMonitor` nutzt `mAccel = mAccel * 0.9f + delta` – gerätespezifischer Jitter → viele False Positives
- Sensitivität als Text-String ("Low"/"Medium"/"High") → `Integer.parseInt()` → NumberFormatException bei "Off" (→ Fallback 50)
- Kein Baseline-Learning, kein Noise-Floor

### Alfred Camera Ansatz (keine explizite Kalibrierung)
Alfred umgeht das Problem durch:
1. **Feste Sensitivity-Stufen** (LOW/MEDIUM/HIGH) – keine Rohwerte, keine Geräteabhängigkeit
2. **Dezibel-Threshold** für Ton (absoluter Wert statt relativer Pegelunterschied)
3. **ML-Modell normalisiert** Kamera-Bewegung implizit (on-device oder Cloud)
4. **Pro-Event gespeichert**: welche Sensitivität + welches Modell hat ausgelöst → forensisch nachvollziehbar

### Haven 2.0 Kalibrierungsstrategie
Kombination aus beiden Ansätzen:

**Accelerometer / Gyro (implementiert):**
- Warmup-Phase (konfigurierbar, Standard 10s) → Rauschen sampeln
- `noiseFloor` = **90. Perzentil** der Warmup-Samples (robust gegen Ausreißer)
- `threshold = noiseFloor * sensitivityMultiplier`
- Aktuelle Multiplier: `LOW=6.5×`, `MEDIUM=4.5×`, `HIGH=2.5×`
- Severity-Stufen: `HIGH` wenn delta > threshold×3, `MEDIUM` wenn > threshold×1.5, sonst `LOW`
- Cooldown: HIGH=200ms, MEDIUM=500ms, LOW=800ms (verhindert Rapid-Fire)

**Licht (implementiert – EMA-Baseline):**
- **Exponential Moving Average** (alpha=0.02, ~10s Zeitkonstante) als Referenz
- Vergleich Instantanwert vs. EMA → echte Spikes werden erkannt
- Sonnenauf/-untergang (~0.5 lux/s) erzeugt nur ~5 lux EMA-Drift → kein False Positive
- Lampe an/aus (+200 lux sofort) → sofortige Erkennung
- Cooldown 30s nach Trigger (verhindert Kaskaden bei Tageslichtveränderungen)
- Nach Trigger: EMA wird sofort auf aktuellen Lux-Wert gesetzt (EMA-Snap), damit kein Kaskaden-Trigger bei langsamen Lichtdrifts
- Aktuelle Thresholds: `LOW=100 lux`, `MEDIUM=60 lux`, `HIGH=30 lux`

**Mikrofon:**
- Dezibel-basiert (wie Alfred), kein relativer Pegelvergleich
- Absolute dB-Schwelle: `LOW=70dB`, `MEDIUM=60dB`, `HIGH=50dB`

**Kamera-Bewegung (3-Stufen-Pipeline, implementiert):**
- **Stufe 1 – Luminanz-Diff** (schnell): Anteil veränderter Pixel, Thresholds: `LOW=0.22`, `MEDIUM=0.12`, `HIGH=0.06` → in `Sensitivity.cameraMotionThreshold` (Single Source of Truth)
- **Stufe 2 – Perceptual Hash** (strukturell): 8×8 Average Hash, Hamming-Distanz ≥ 4 bestätigt echte Strukturänderung, filtert Lichtflicker heraus
- **Stufe 3 – TFLite** (semantisch, nur bei bestätigter Bewegung): EfficientDet Lite 0, COCO-trained, klassifiziert Person/Tier/Fahrzeug → spart Akku (ML läuft nicht auf jedem Frame)
- TFLite degradiert graceful wenn Modell-Datei fehlt (→ MOTION_ONLY Fallback)
- ROI-Zonen schränken aktive Bildbereiche ein → `cropLuma()` in CameraAnalyzer (implementiert)

**Sensorfusion (implementiert, Phase 2):**
- `FusedMotionMonitor` ersetzt separate `AccelerometerMonitor` + `GyroscopeMonitor` in MonitorService
- `SensorFusionEngine`: Complementary Filter, alpha=0.7 (70% Gyro, 30% Accel-Delta)
- Tisch-Vibration: hoher Accel-Delta, niedriger Gyro → fusionierter Score gedämpft → weniger False Positives
- Intentionale Bewegung: beides hoch → Score bleibt hoch → korrekte Erkennung
- Graceful Degradation ohne Gyroscop: latestGyroMagnitude=0, Score = 30% Accel-Delta

**Kalibrierungs-Wizard (implementiert, Phase 2):**
- MonitorService emittiert `calibrationSecondsRemaining` + `calibrationResults` während CALIBRATING
- `FusedMotionMonitor.noiseFloor` + `LightMonitor.emaBaseline` als `StateFlow<Float?>` (null → Wert nach Warmup)
- MonitorScreen zeigt: Countdown, pro Sensor Spinner→✓ + berechneter Noise-Floor-Wert

**EventConfig pro Event speichern (wie Alfred):**
```kotlin
data class EventConfig(
    val sensitivity: Int,      // Schwellwert zum Zeitpunkt des Events
    val detectionMode: String, // MOTION / PERSON / PET / VEHICLE
    val aiModel: String?,      // TFLite-Modell falls ML aktiv
    val isZoneActive: Boolean  // War ROI-Zone aktiv
)
```

---

## Haven 2.0 – EventTrigger-Typen (erweitert)

```kotlin
// Aus Haven 0.2.1 übernommen:
ACCELEROMETER = 0
CAMERA = 1         // Luminanz-Bewegung
MICROPHONE = 2
PRESSURE = 3
LIGHT = 4
POWER = 5
BUMP = 6           // Significant Motion
CAMERA_VIDEO = 7
HEART = 8          // Heartbeat

// Neu in Haven 2.0 (implementiert):
SOUND_DECIBEL = 14   // Dezibel-Schwellwert überschritten
GYROSCOPE = 15       // Gyroskop-Bewegung (intern, via FusedMotionMonitor)

// Neu in Haven 2.0 (Phase 2, implementiert):
CAMERA_PERSON = 9    // TFLite EfficientDet: Person erkannt
CAMERA_PET = 10      // TFLite EfficientDet: Tier erkannt (cat, dog)
CAMERA_VEHICLE = 11  // TFLite EfficientDet: Fahrzeug erkannt (car, motorcycle, bus, truck, …)

// Neu in Haven 2.0 (Phase 3, geplant):
CAMERA_LINGER = 12   // Person verweilt (CONTEXT_LINGER)
CAMERA_ABSENT = 13   // Person nicht mehr da (CONTEXT_ABSENT)
```

---

## UI / Design-Regeln (Material Design 3)

### Grundprinzipien
- **Dark-first**: Haven wird primär in Dunkelheit/Nacht genutzt → Dark Theme als Standard, Light Theme optional
- **Material Design 3** (Material You) + Jetpack Compose – keine XML-Layouts
- **Dynamic Color** auf Android 12+ aktivieren; Fallback: eigenes Farbschema (dunkel, teal/blau-grün)
- **Mindesttouchziel 48dp** für alle interaktiven Elemente (Barrierefreiheit)
- **contentDescription** an allen Icons und interaktiven Bildelementen

### Farbschema (Fallback, wenn kein Dynamic Color)
```
primary:          #00897B  (Teal 600 – Sicherheit, Kontrolle)
onPrimary:        #FFFFFF
primaryContainer: #004D40  (dunkel)
secondary:        #546E7A  (Blau-Grau)
surface:          #121212  (fast schwarz)
surfaceVariant:   #1E1E1E
error:            #CF6679  (gedämpftes Rot)
```

### Severity-Farbkodierung (konsistent überall)
| Severity | Farbe | Token |
|---|---|---|
| LOW | Grün | `MaterialTheme.colorScheme.tertiary` |
| MEDIUM | Gelb/Amber | `Color(0xFFFFB300)` |
| HIGH | Orange | `Color(0xFFE65100)` |
| CRITICAL | Rot | `MaterialTheme.colorScheme.error` |

### Navigation
- **Bottom Navigation Bar** (3–4 Ziele): Monitor · Timeline · Settings
- Diagnostics als sekundärer Screen (kein Bottom-Nav-Eintrag, via Settings erreichbar)
- `NavigationBarItem` mit Icons aus `Icons.Filled.*` + Label
- Navigation Compose: `popUpTo + saveState + restoreState` für korrekte Backstack-Behandlung

### Screens & Komponenten
- **Scaffold + TopAppBar** (`CenterAlignedTopAppBar`) auf allen Screens
- **Cards** mit `shape = RoundedCornerShape(12.dp)`, `surfaceVariant` als containerColor
- **LazyColumn** für Timeline / Trigger-Log (niemals Column für Listen)
- **FAB** nur auf Screens mit primärer Aktion (z.B. Share in Diagnostics)
- **CircularProgressIndicator** für Kalibrierungsphase
- Sensor-Werte, Logs, Timestamps: `FontFamily.Monospace`

### Typografie-Hierarchie
```
headlineSmall  → Screen-Titel, großer Status ("ARMED")
titleMedium    → Card-Titel, Sektionsüberschriften
bodyMedium     → Beschreibungstext, Einstellungen
labelMedium    → Chips, Badges, Metadaten
bodySmall / Monospace → Sensor-Rohdaten, Timestamps
```

### Spacing-Konventionen
```
Screen-Padding:       16.dp horizontal
Card-interner Abstand: 16.dp
Zwischen Sektionen:   24.dp
Zwischen List-Items:  8.dp
Icon-Text-Abstand:    8.dp
```

### Status-Indikator (MonitorScreen)
- **IDLE**: Neutraler Zustand, großer „Überwachung starten"-Button (FilledButton)
- **COUNTDOWN**: Großer Countdown-Zähler (`displayLarge`), abbrechen via OutlinedButton
- **CALIBRATING**: Kalibrierungs-Wizard – Countdown in `displaySmall` + Sensor-Zeilen mit Spinner→✓ + Noise-Floor-Wert in `FontFamily.Monospace`
- **ACTIVE**: Deutliches „AKTIV"-Badge (error-Container-Farbe) + Puls-Animation optional

### Was zu vermeiden ist
- Keine hardcodierten Farb-Hex-Werte außer den Severity-Farben oben
- Keine `Column` für scrollbare Listen
- Kein `weight(1f)` als Spacer-Ersatz – `Spacer(Modifier.height/width(X.dp))` nutzen
- Keine UI-Logik in Composables – State aus ViewModel via `collectAsStateWithLifecycle()`

---

## Build & Tooling

- Schnelles Kompilierungscheck: `./gradlew :app:compileDebugKotlin` (~35 s mit Daemon)
- Vollständiger Debug-Build: `./gradlew :app:assembleDebug`
- GSD-Planung noch nicht initialisiert → `/gsd:new-project` vor `/gsd:quick`

---

## AppLogger – In-App-Fehlerprotokollierung

`storage/AppLogger.kt` – Hilt `@Singleton`, Ring-Buffer (max 500 Einträge), `StateFlow<List<Entry>>`.
- `appLogger.e/w/i/d(tag, msg)` → auch an `android.util.Log` weitergeleitet
- In `HavenObjectDetector` injiziert → TFLite-Init- und Inferenzfehler werden erfasst
- In `MonitorService` injiziert → Service-Lifecycle-Events
- `DiagnosticsScreen` zeigt Einträge live, farbkodiert nach Level; "Leeren"-Button
- **Kein `Runtime.exec("logcat")` verwenden** – auf echten Geräten (API 26+) unzuverlässig

---

## Wichtige Implementierungshinweise

- **Immer Kotlin** – kein neues Java
- **CameraX ImageAnalysis** für Kamera-Frames (nicht deprecated Camera1/Camera2 direkt)
- **DataStore** statt SharedPreferences
- **Coroutines + Flow** statt Messenger/Handler IPC
- **Hilt** für Dependency Injection
- **Keine Google-Cloud-Dienste** (Firebase Analytics, AdServices, etc.)
- **Verschlüsselung ab Tag 1** – keine unverschlüsselten Medien persistieren
- **Sensor.TYPE_ACCELEROMETER_UNCALIBRATED** mit Fallback auf TYPE_ACCELEROMETER
- **Sensitivity immer als Enum** (nicht als Text-String wie Haven) → keine parseInt-Bugs
- **Dezibel-basierte Ton-Detektion** (absoluter Schwellwert, wie Alfred) statt relativer Pegelvergleich
- **Noise-Floor: 90. Perzentil** der Warmup-Samples (nicht Average) → robuster gegen Ausreißer
- **EMA-Baseline für Lichtsensor** (alpha=0.02) → kein False Positive bei Sonnenauf/-untergang
- **AppCompatActivity** für MainActivity (required für AppCompat locale delegate)
- **EventConfig mit jedem Event speichern** (Sensitivität + Modell + Zonen-Status) für forensische Nachvollziehbarkeit
- **Alfred Camera Analyse**: Motion-Detection läuft nativ (C++), keine Schwellwerte in Java extrahierbar. Sensitivitäts-Enum (LOW/MEDIUM/HIGH) und absolute dB-Thresholds übernommen. Unser Noise-Floor-Ansatz ist konzeptionell überlegen.
- **Sensorfusion**: `FusedMotionMonitor` (Phase 2) ersetzt separate Accel/Gyro-Monitore. alpha=0.7. AccelerometerMonitor + GyroscopeMonitor bleiben im Code, werden aber nicht mehr von MonitorService genutzt.
- **pHash-Bestätigung**: `PerceptualHashDetector` als Stufe 2 in CameraAnalyzer. Hamming-Distanz < 4 → Helligkeitsshift, kein echter Trigger. Luma-Diff ≥ 2× Threshold → sofortiger Trigger ohne pHash-Bestätigung.
- **TFLite**: `HavenObjectDetector` ist `@Singleton`, initialisiert lazy. Modell-Datei `efficientdet_lite0.tflite` nach `assets/` kopieren (curl-Befehl im build.gradle.kts Kommentar). Ohne Datei → MOTION_ONLY (kein Crash).
- **DetectionMode**: In DataStore gespeichert, in SettingsScreen als RadioGroup wählbar. MonitorService liest beim Start und übergibt an CameraAnalyzer.
- **Kalibrierungs-Wizard**: `CalibrationResults` als StateFlow in MonitorService.Companion. Sensor-Monitore emittieren ihren Noise-Floor via `StateFlow<Float?>` (reset zu null bei jedem observe()-Aufruf).
- **Erkennungszonen (ROI)**: `DetectionZone` speichert normalisierte Koordinaten (0.0–1.0). `cropLuma()` in CameraAnalyzer schneidet das Luma-Array vor beiden Detektoren zu – Detektoren kennen keine Zonen. Zone in DataStore (Serialisierung: "left,top,right,bottom"). `ZoneEditorScreen` zeigt Live-Kamera-Preview via `AndroidView(PreviewView)` + Canvas-Overlay; Camera-Binding per `DisposableEffect(cameraSelector)` mit `ProcessCameraProvider` (wird in `onDispose` freigegeben). Drag-Gesten (Mindestgröße 5%×5%), Dim-Overlay außerhalb der Zone, weiße Eck-Handles.
- **Stop-Cooldown**: Beim Beenden der Überwachung werden die letzten `STOP_COOLDOWN_SECONDS = 30` Sekunden retroaktiv aus der DB gelöscht (`EventRepository.discardTriggersSince()`). Begründung: Nutzer läuft zum Gerät und löst dabei Sensoren aus. Wert ist in `MonitorService.Companion` öffentlich sichtbar und wird in Diagnostics angezeigt.
- **Sensitivity-Enum ist Single Source of Truth** für alle Sensor-Schwellwerte: `accelerometerMultiplier`, `microphoneThresholdDb`, `lightDeltaLux`, `cameraMotionThreshold`. CameraAnalyzer liest `sensitivity.cameraMotionThreshold` statt hardcodierter when-Expression.
- **Diagnostics-Screen**: Zeigt Settings, Kalibrierungsstrategie (pro Sensor: Algorithmus, Runtime-Werte aus `MonitorService.calibrationResults`, berechnete Schwellwerte, statische Parameter) und Trigger-Log. `DiagnosticsViewModel` injiziert `HavenObjectDetector` für `isAvailable`-Status. Share-Export enthält alle Diagnosedaten als Plain-Text.

<!-- GSD:project-start source:PROJECT.md -->
## Project

**Haven 2.0**

Haven 2.0 ist eine privacy-zentrierte, hochsichere Android-Überwachungsapp (Kotlin).
Sie kombiniert sensorische Tiefe (Beschleunigungssensor, Gyroskop, Mikrofon, Licht, Kamera) mit
KI-basierter Objekterkennung (TFLite/EfficientDet) und verschlüsselter lokaler Speicherung —
ohne Cloud-Abhängigkeit, ohne Tracking. Für Nutzer, die physische Sicherheit brauchen und
Datenschutz ernst nehmen.

**Core Value:** Zuverlässige, privacy-respektierende Bewegungserkennung — die App muss starten, kalibrieren
und Ereignisse erfassen, ohne dass etwas stillschweigend fehlschlägt.

### Constraints

- **Tech Stack**: Kotlin only — kein neues Java
- **Android**: minSdk 26, targetSdk 35
- **Privacy**: Keine Google Cloud Services, keine Firebase Analytics
- **Verschlüsselung**: Keine unverschlüsselten Medien persistieren (ab Phase 5 Keystore)
- **DI**: Hilt — keine manuellen Konstruktoren für injizierte Klassen
- **UI**: Jetpack Compose + Material Design 3, Dark-first
<!-- GSD:project-end -->

<!-- GSD:stack-start source:codebase/STACK.md -->
## Technology Stack

## Languages
- Kotlin 2.0.20 - All application code under `app/src/main/java/org/havenapp/main/`
- None (Java fully replaced; no new Java files)
## Runtime
- Android SDK, minSdk 26 (Android 8.0 Oreo), targetSdk 35, compileSdk 35
- JVM target: Java 17 (`sourceCompatibility = JavaVersion.VERSION_17`)
- Gradle 8.9 (via Gradle Wrapper at `gradle/wrapper/gradle-wrapper.properties`)
- Version catalog: `gradle/libs.versions.toml`
- Lockfile: None (no `gradle.lockfile`)
## Frameworks
- AppCompat 1.7.0 - `AppCompatActivity` in `MainActivity.kt` (required for per-app locale switching)
- AndroidX Core KTX 1.15.0 - Kotlin extensions throughout
- AndroidX Activity Compose 1.9.3 - Compose integration in `MainActivity.kt`
- Jetpack Compose BOM 2024.09.03 - All UI under `app/src/main/java/org/havenapp/main/ui/`
- Material 3 (compose-material3) - `HavenTheme` in `ui/theme/Theme.kt`, Dynamic Color on Android 12+
- Material Icons Extended (compose-material-icons) - Icons in all screens
- Navigation Compose 2.8.3 - `HavenNavGraph.kt` defines routes: MONITOR, TIMELINE, SETTINGS, DIAGNOSTICS, ZONE_EDITOR, EVENT_DETAIL
- Lifecycle ViewModel KTX + Compose 2.8.6 - All ViewModels (`MonitorViewModel`, `TimelineViewModel`, `SettingsViewModel`, `DiagnosticsViewModel`, `ZoneEditorViewModel`, `EventDetailViewModel`)
- Lifecycle Runtime KTX + Compose 2.8.6 - `collectAsStateWithLifecycle()` in Composables
- Lifecycle Service 2.8.6 - `MonitorService` extends `LifecycleService`
- Hilt 2.51.1 - `@HiltAndroidApp` in `HavenApplication.kt`, `@AndroidEntryPoint` in `MonitorService.kt`
- Hilt Navigation Compose 1.2.0 - `hiltViewModel()` in nav graph
- KSP 2.0.20-1.0.25 - Code generation for Hilt and Room
- Room 2.6.1 - `HavenDatabase` in `storage/HavenDatabase.kt`, two entities: `EventEntity`, `EventTriggerEntity`
- CameraX Core 1.4.0 - `ImageAnalysis` in `MonitorService.kt`
- CameraX Camera2 1.4.0 - Camera2 backend implementation
- CameraX Lifecycle 1.4.0 - `ProcessCameraProvider` lifecycle binding
- CameraX View 1.4.0 - `PreviewView` in `ZoneEditorScreen.kt`
- TensorFlow Lite Task Vision 0.4.4 - `HavenObjectDetector` in `detection/HavenObjectDetector.kt`
- DataStore Preferences 1.1.1 - `SettingsRepository` in `storage/SettingsRepository.kt`
- Kotlinx Coroutines Android 1.8.1 - `callbackFlow`, `StateFlow`, `lifecycleScope` throughout sensors and service
## Build System
- `build.gradle.kts` - root project config (plugins only)
- `app/build.gradle.kts` - application module (AGP 8.5.2, dependencies)
- `gradle/libs.versions.toml` - version catalog (single source of truth for all versions)
- `settings.gradle.kts` - repository declarations, single `:app` module
- `gradle.properties` - JVM heap (`-Xmx2048m`), AndroidX enabled, parallel builds, official Kotlin code style
- `app/proguard-rules.pro` - ProGuard rules for release builds
- `debug`: no minification, applicationId suffix `.debug`
- `release`: minify + shrink resources enabled, ProGuard applied
- `buildConfig = true` - generates `BuildConfig.VERSION_NAME` / `VERSION_CODE`
- `noCompress += "tflite"` - TFLite model files excluded from compression
## Runtime Requirements
- Android Studio (current)
- JDK 17
- Android SDK with compileSdk 35 tools
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
<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->
## Conventions

## Naming
- PascalCase for all Kotlin source files matching the primary class/object they contain
- One class/interface per file (enforced by project structure)
- Examples: `FusedMotionMonitor.kt`, `HavenObjectDetector.kt`, `SettingsRepository.kt`
- Screen files end with `Screen`: `MonitorScreen.kt`, `ZoneEditorScreen.kt`
- ViewModel files end with `ViewModel`: `MonitorViewModel.kt`, `DiagnosticsViewModel.kt`
- Entity files end with `Entity`: `EventEntity.kt`, `EventTriggerEntity.kt`
- DAO files end with `Dao`: `EventDao.kt`, `EventTriggerDao.kt`
- PascalCase throughout: `SensorMonitor`, `LightMonitor`, `CameraAnalyzer`
- Interfaces use noun names (not `I`-prefix): `SensorMonitor` not `ISensorMonitor`
- DI modules end with `Module`: `AppModule`, `DatabaseModule`, `DataStoreModule`
- Hilt components are `object`: `object AppModule`, `object DatabaseModule`
- camelCase for all functions: `startMonitoring()`, `acquireWakeLock()`, `buildNotification()`
- Private helpers use verb-noun pattern: `buildBitmap()`, `cropLuma()`, `deriveSeverity()`
- Suspend functions have no special suffix — just camelCase: `openEvent()`, `recordTrigger()`
- ViewModel setters use `set` prefix: `setSensitivity()`, `setCameraPosition()`, `setDetectionMode()`
- camelCase for all variables
- Private backing `MutableStateFlow` uses `_` prefix: `_noiseFloor`, `_state`, `_calibrationResults`
- Public `StateFlow` exposes without prefix: `noiseFloor`, `state`, `calibrationResults`
- Constants use SCREAMING_SNAKE_CASE in `companion object`: `ACTION_START`, `STOP_COOLDOWN_SECONDS`, `TFLITE_MIN_INTERVAL_MS`
- DataStore preference keys use `KEY_` prefix: `KEY_SENSITIVITY`, `KEY_DETECTION_ZONE`
- PascalCase enum class, SCREAMING_SNAKE_CASE entries: `Sensitivity.MEDIUM`, `MonitorState.CALIBRATING`
- Enum values have typed constructor params (no bare ints): see `Sensitivity` with `accelerometerMultiplier`, `microphoneThresholdDb`, etc.
- lowercase, dot-separated, feature-grouped under `org.havenapp.main`:
## Code Style
- No `.editorconfig` or `.ktlint` config found — style is enforced by convention and code review
- Standard Kotlin indentation: 4 spaces
- Opening braces on same line (K&R style)
- Single-expression functions written inline where appropriate: `fun serialize(): String = "$left,$top,$right,$bottom"`
- Trailing lambdas outside parentheses: `dataStore.data.map { prefs -> ... }`
- `Unit` return type omitted on overrides: `override fun onAccuracyChanged(...) = Unit`
- Kotlin 2.0.20, JVM target 17
- `entries` used instead of deprecated `values()` on enums: `TriggerType.entries.find { it.id == id }`
## Patterns Used
- `ViewModel` holds only `StateFlow` — no business logic
- Business logic lives in Repositories (`SettingsRepository`, `EventRepository`) and domain classes (`SensorFusionEngine`, `CameraAnalyzer`)
- Composable screens only call ViewModel functions; never repositories directly
- State is read via `collectAsStateWithLifecycle()` — never `collectAsState()`
- Missing hardware (no gyroscope): `latestGyroMagnitude` stays 0, fused score degrades to 30% accel delta
- Missing TFLite model file: `HavenObjectDetector.isAvailable` returns false, `CameraAnalyzer` falls back to `MOTION_ONLY`
- Missing sensor: flow is closed immediately with `close()`
- `SettingsUiState` in `SettingsViewModel.kt`
- `DiagnosticsUiState` in `DiagnosticsViewModel.kt`
- `@Singleton` on all monitors and repositories
- `@HiltViewModel` on all ViewModels
- `@AndroidEntryPoint` on `MonitorService` and `MainActivity`
- System services (e.g. `SensorManager`) provided via `@Module` objects in `di/`
- `@ApplicationContext` qualifier used when context is needed in singletons
- DAOs are interfaces annotated with `@Dao`
- Reactive queries return `Flow<List<...>>` for live observation
- Mutating operations are `suspend fun`
- No raw SQL for inserts/updates where annotations suffice (`@Insert`, `@Query` for custom logic only)
- All routes defined as `const val` in `Routes` object in `HavenNavGraph.kt`
- Helper functions for parameterized routes: `Routes.eventDetail(eventId: Long)`
- Navigation uses `popUpTo + saveState + restoreState` pattern for bottom nav tab switching
- All screens use `Scaffold + CenterAlignedTopAppBar`
- Cards use `shape = RoundedCornerShape(12.dp)` and `surfaceVariant` container
- Lists use `LazyColumn`, never `Column`
- Sensor/timestamp values: `FontFamily.Monospace`
- `Spacer(Modifier.height(X.dp))` for spacing — not `weight(1f)` as spacer
- Icons from `Icons.Filled.*` only
- `contentDescription` on all icons
## Error Handling
- DataStore enum deserialization: `runCatching { Sensitivity.valueOf(it) }.getOrNull()`
- TFLite model loading: `runCatching { ... }.onFailure { initError = ... }`
- Bitmap conversion: `runCatching { ... }.getOrNull()`
- Logcat read: `runCatching { ... }.getOrDefault(listOf("(logcat unavailable)"))`
- `Log.w(TAG, ...)` for warnings (model not found)
- `Log.i(TAG, ...)` for successful init
- `Log.e(TAG, "...", it)` for inference failures with full exception
## Logging
- Only `HavenObjectDetector` uses structured logging with a `TAG` constant: `private const val TAG = "HavenObjectDetector"`
- Other classes do not log — sensor monitors and the service emit state via Flow/StateFlow instead
- Diagnostics screen surfaces logs to the user via `DiagnosticsViewModel.refreshLogs()` which reads `logcat` process output (WARN level and above, last 200 lines, filtered to app PID)
- `Log.i` — successful model initialization
- `Log.w` — soft failures (model file missing, load failure)
- `Log.e` — inference-time exceptions
## Documentation
- Class-level KDoc on every domain class explaining its purpose and algorithm: `SensorFusionEngine`, `PerceptualHashDetector`, `CameraAnalyzer`, `LightMonitor`, `FusedMotionMonitor`, `HavenObjectDetector`
- `@param` tags on public functions with non-obvious parameters
- Companion object constants documented inline when their value needs justification (e.g. EMA alpha, cooldown values)
- Algorithm rationale in comments: numerical thresholds explained with real-world examples (e.g. "Sonnenauf/-untergang (~0.5 lux/s) erzeugt nur ~5 lux EMA-Drift")
- KDoc and inline comments are mixed German/English. Class-level docs trend English; inline algorithm notes trend German. New code should be consistent within a file.
- `@return` tags often omitted on simple functions
- No module-level `package-info` files
- No generated API docs setup
<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:ARCHITECTURE.md -->
## Architecture

## Pattern
- Unidirectional data flow: sensors → `Flow<TriggerEvent>` → `MonitorService` → Room + `StateFlow` → ViewModel → Compose UI
- `MonitorService` is the single runtime coordinator; it owns the sensor lifecycle
- Service state is exposed as process-wide `companion object` `StateFlow`s (not injected), so ViewModels can observe it without holding a reference to the service
- Hilt provides all singleton dependencies; no manual object graphs
- DataStore (not SharedPreferences) for all persistent settings
## Layers
- Purpose: render state, accept user actions, navigate between screens
- Location: `app/src/main/java/org/havenapp/main/ui/`
- Contains: Composable screens, ViewModels (`@HiltViewModel`), theme, nav graph
- Depends on: ViewModel only; never calls repositories or sensors directly
- Observes: `StateFlow` / `Flow` collected with `collectAsStateWithLifecycle()`
- Purpose: orchestrate the monitoring session lifecycle — countdown → calibration → active sensing
- Location: `app/src/main/java/org/havenapp/main/MonitorService.kt`
- Contains: `MonitorService` (`LifecycleService`, `@AndroidEntryPoint`)
- Depends on: `EventRepository`, `SettingsRepository`, `FusedMotionMonitor`, `LightMonitor`, `MicrophoneMonitor`, `CameraAnalyzer`, `HavenObjectDetector`
- Exposes: companion-object `StateFlow`s (`state`, `countdownSeconds`, `calibrationSecondsRemaining`, `calibrationResults`) readable by any ViewModel
- Purpose: wrap Android hardware into cold `Flow<TriggerEvent>` streams with built-in warmup/calibration
- Location: `app/src/main/java/org/havenapp/main/sensor/`
- Contains: `SensorMonitor` interface + `FusedMotionMonitor`, `LightMonitor`, `MicrophoneMonitor` implementations; legacy `AccelerometerMonitor`, `GyroscopeMonitor` (kept but not wired)
- Depends on: Android `SensorManager`, `SensorFusionEngine`
- Contract: `SensorMonitor.observe(sensitivity, warmupMs): Flow<TriggerEvent>` — flow is cold, unregisters listeners on collection cancellation
- Purpose: per-frame camera analysis via CameraX `ImageAnalysis`
- Location: `app/src/main/java/org/havenapp/main/media/`
- Contains: `CameraAnalyzer` (implements `ImageAnalysis.Analyzer`), `LuminanceMotionDetector`
- Depends on: `PerceptualHashDetector`, `HavenObjectDetector`, `DetectionZone`, `Sensitivity`
- Exposes: `CameraAnalyzer.events: Flow<TriggerEvent>` backed by a `Channel`
- Purpose: detection algorithms and configuration types; no Android framework dependencies
- Location: `app/src/main/java/org/havenapp/main/detection/`
- Contains: `SensorFusionEngine` (complementary filter), `PerceptualHashDetector` (aHash), `HavenObjectDetector` (TFLite wrapper), `DetectionMode`, `DetectionZone`
- Depends on: TFLite Task Library for `HavenObjectDetector`; pure Kotlin for others
- Purpose: shared domain types emitted by sensors and persisted to storage
- Location: `app/src/main/java/org/havenapp/main/events/`
- Contains: `TriggerEvent` (data class), `TriggerType` (enum, IDs 0–15), `Severity` (LOW/MEDIUM/HIGH/CRITICAL)
- Depends on: nothing (pure Kotlin)
- Purpose: persist events and settings; provide reactive `Flow` access
- Location: `app/src/main/java/org/havenapp/main/storage/`
- Contains: `HavenDatabase` (Room), `EventRepository`, `SettingsRepository`, DAOs, entities
- Depends on: Room, DataStore
- Used by: `MonitorService` (write path), ViewModels (read path via repository flows)
- Purpose: wire singletons via Hilt modules
- Location: `app/src/main/java/org/havenapp/main/di/`
- Contains: `AppModule` (SensorManager), `DatabaseModule` (Room, DAOs), `DataStoreModule` (DataStore)
## Data Flow
- `SettingsRepository` exposes each setting as a `Flow` backed by DataStore
- `MonitorService` reads settings once at session start via `.first()` (snapshot)
- ViewModels hold settings as `StateFlow` via `.stateIn(viewModelScope, ...)`
- User taps "Start" → `MonitorViewModel.startMonitoring()` → `Intent(ACTION_START)` → `startForegroundService()`
- `MonitorService.state` (`companion MutableStateFlow`) updates: IDLE → COUNTDOWN → CALIBRATING → ACTIVE
- `MonitorViewModel` exposes `MonitorService.state` as a `StateFlow` collected by `MonitorScreen`
- UI re-composes on state changes; no polling
## Key Abstractions
- Single method: `observe(sensitivity: Sensitivity, warmupMs: Long): Flow<TriggerEvent>`
- All three active sensor monitors implement this interface
- `MonitorService` treats all sensors uniformly: collect the merged flow
- The universal event token flowing from sensors → service → repository
- Fields: `type: TriggerType`, `timestamp`, `sensorValue: Float?`, `mediaPath: String?`, `severity: Severity`
- Single source of truth for all sensor thresholds: `accelerometerMultiplier`, `microphoneThresholdDb`, `lightDeltaLux`, `cameraMotionThreshold`
- Eliminates the `parseInt("Off")` bug class from Haven 0.2.1
- Values: `OFF`, `LOW(6.5, 70dB, 100lux, 0.22)`, `MEDIUM(4.5, 60dB, 60lux, 0.12)`, `HIGH(2.5, 50dB, 30lux, 0.06)`
- State machine states: `IDLE` → `COUNTDOWN` → `CALIBRATING` → `ACTIVE`
- Exposed as `MonitorService.state: StateFlow<MonitorState>` (companion object)
- Normalized coordinates (0.0–1.0); `toPixelBounds(w, h)` converts at analysis time
- Serialized as `"left,top,right,bottom"` string in DataStore
- `CameraAnalyzer.cropLuma()` applies zone before both luma and pHash detectors — detectors are zone-unaware
- `@Singleton`, lazy `initialize()` call
- Graceful degradation: if `efficientdet_lite0.tflite` is absent → `isAvailable = false`, `initError` set, no crash
- `availabilityFlow: StateFlow<Boolean>` for reactive UI updates
## State Management
- `MonitorService.state: StateFlow<MonitorState>` — current lifecycle phase
- `MonitorService.countdownSeconds: StateFlow<Int>` — live countdown display
- `MonitorService.calibrationSecondsRemaining: StateFlow<Int>` — calibration progress
- `MonitorService.calibrationResults: StateFlow<CalibrationResults?>` — noise-floor values after warmup
- `FusedMotionMonitor.noiseFloor: StateFlow<Float?>` — null until warmup finishes; reset to null on each `observe()` call
- `LightMonitor.emaBaseline: StateFlow<Float?>` — same pattern
- All settings are `Flow<T>` in `SettingsRepository` backed by DataStore; persisted across process restarts
- Each ViewModel converts repository/service flows into `StateFlow` via `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), default)`
- UI state exposed as a single `uiState: StateFlow<XxxUiState>` data class where multiple flows are combined (see `DiagnosticsViewModel`)
## Dependency Injection
- `AppModule` (`di/AppModule.kt`): provides `SensorManager` as `@Singleton`
- `DatabaseModule` (`di/DatabaseModule.kt`): provides `HavenDatabase`, `EventDao`, `EventTriggerDao` as singletons
- `DataStoreModule` (`di/DataStoreModule.kt`): provides `DataStore<Preferences>` as `@Singleton`
- `EventRepository`, `SettingsRepository`, `FusedMotionMonitor`, `LightMonitor`, `MicrophoneMonitor`, `HavenObjectDetector` — all `@Singleton @Inject constructor`
- `MonitorService` receives `EventRepository`, `SettingsRepository`, `HavenObjectDetector`, `FusedMotionMonitor`, `LightMonitor`, `MicrophoneMonitor` via `@Inject lateinit var`
## Error Handling
- `runCatching { }.getOrNull()` / `getOrDefault()` used in `HavenObjectDetector`, `DetectionZone.fromString()`, `CameraAnalyzer.buildBitmap()`
- Sensor absence: `FusedMotionMonitor` closes the flow if no accelerometer; gyroscope absence sets `latestGyroMagnitude = 0` (fusion still works)
- TFLite model absence: `HavenObjectDetector.isAvailable` stays false; `CameraAnalyzer` falls back to generic `CAMERA` trigger type
- Camera binding failures: wrapped in `runCatching {}` in `MonitorService.startCamera()`
- DataStore reads: all settings have `?: default` fallbacks; enum parsing uses `runCatching { Sensitivity.valueOf(it) }.getOrNull() ?: Sensitivity.MEDIUM`
<!-- GSD:architecture-end -->

<!-- GSD:workflow-start source:GSD defaults -->
## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:
- `/gsd:quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd:debug` for investigation and bug fixing
- `/gsd:execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.
<!-- GSD:workflow-end -->

<!-- GSD:profile-start -->
## Developer Profile

> Profile not yet configured. Run `/gsd:profile-user` to generate your developer profile.
> This section is managed by `generate-claude-profile` -- do not edit manually.
<!-- GSD:profile-end -->
