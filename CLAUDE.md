# CLAUDE.md — Haven 2.0 Projekt-Kontext

## 1. Projekt-Zusammenfassung
Haven 2.0 ist eine **privacy-zentrierte Android-Überwachungsapp** (Kotlin).
*   **Leitprinzipien:** Local-first, On-device AI (keine Cloud-Inferenz), Sensorfusion, verschlüsselte lokale Speicherung.
*   **Tech-Stack:** Kotlin 2.0.20, minSdk 26, targetSdk 35.
*   **Frameworks:** Jetpack Compose (UI), MVVM + Clean Architecture, Hilt (DI), Room (DB), DataStore (Settings), CameraX (Media), TensorFlow Lite (KI).

## 2. Architektur & Projektstruktur
Die App folgt einem **Unidirektionalen Datenfluss**: Sensoren → Flow → MonitorService → Room/Repository → ViewModel → UI.

### Grobe Struktur (`org.havenapp.main.*`) [17, 23-29]
*   `MonitorService.kt`: Zentraler Koordinator (LifecycleService). Steuert Lifecycle (IDLE → COUNTDOWN → CALIBRATING → ACTIVE) und fusioniert Sensor-Flows.
*   `sensor/`: Hardware-Wrapper (FusedMotion, Light, Microphone). Nutzen `callbackFlow`.
*   `media/`: CameraX-Pipeline (`CameraAnalyzer`).
*   `detection/`: Algorithmen (SensorFusionEngine, PerceptualHashDetector, HavenObjectDetector).
*   `storage/`: `EventRepository` (Room) und `SettingsRepository` (DataStore).
*   `ui/`: Compose-Screens und ViewModels (MVVM) .

## 3. Zentrale Design-Entscheidungen
*   **Service-Status:** Status-Flows (`state`, `countdown` etc.) sind im **Companion Object** des `MonitorService` prozessweit zugänglich.
*   **Single Source of Truth:** Das `Sensitivity` Enum enthält alle Schwellwerte für alle Sensoren.
*   **Kalibrierung:** 10s Warmup; Noise-Floor = **90. Perzentil** der Samples (nicht Durchschnitt).
*   **Stop-Cooldown:** Beim Beenden werden die letzten 30s retroaktiv aus der DB gelöscht (Nutzerinteraktion).
*   **Sicherheit:** AES-GCM (Android Keystore) für Medien; App-PIN via SHA-256 + Salt.

## 4. Kern-Logik & Algorithmen
*   **Kamera-Pipeline (3 Stufen):** 1. Luma-Diff (schnell) → 2. Perceptual Hash (strukturell, Hamming-Distanz ≥ 4) → 3. TFLite (semantisch, EfficientDet Lite 0).
*   **Sensorfusion:** `FusedMotionMonitor` kombiniert Accel + Gyro via **Complementary Filter** (alpha=0.7).
*   **Lichtsensor:** EMA-Baseline (alpha=0.02) zur Erkennung von Licht-Spikes.
*   **Audio:** Absolute dB-Schwellwerte statt relativer Vergleiche.

## 5. Coding-Konventionen
*   **Dateien:** PascalCase; eine Klasse pro Datei; Interfaces ohne I-Präfix.
*   **Benennung:** Screens enden auf `Screen`, ViewModels auf `ViewModel`.
*   **Flows:** Private `_state` (MutableStateFlow), öffentliche `state` (StateFlow).
*   **UI:** State-Erhalt via `collectAsStateWithLifecycle()`; Listen nur per `LazyColumn`; Touch-Ziele min. 48dp.
*   **DI:** Hilt für Singletons und ViewModels; keine manuellen Konstruktoren für injizierte Klassen.
*   **Fehler:** Graceful Degradation (z.B. MOTION_ONLY ohne TFLite-Modell).

## 6. Wichtige Befehle
*   **Build:** `./gradlew :app:assembleDebug`.
*   **Compile-Check:** `./gradlew :app:compileDebugKotlin`.
*   **Workflow:** Änderungen immer via GSD-Kommandos (`/gsd:quick`, `/gsd:execute-phase`) einleiten.

## 7. Cloud-Server & Web-UI
**Haven Cloud-Server** ist das optionale, self-hosted Backend zur Skalierung auf mehrere Geräte und Nutzer.

*   **Backend-Stack:** Python 3.12, FastAPI (API), PostgreSQL (DB), Redis & Celery (Worker).
*   **Web-UI-Stack:** Django 5.2, HTMX 2.x, Alpine.js, Tailwind CSS.
*   **Authentifizierung:** Multi-User mit User-Keys, 2FA (TOTP) und App-Keys für individuelles Geräte-Pairing.
*   **Sicherheit:** Verschlüsselung der Nutzerdaten via Argon2id (abgeleitet aus Passwort + Username).
*   **Features:** Admin-Quotas (Speicher/Events), Cloud-KI-Analyse (TFLite/OpenRouter), Benachrichtigungen via E-Mail/Signal/Pushover.

### 7.1 Erweiterte Architektur & Datenfluss
1.  **Android Client:** Lädt verschlüsselte Events und Video-Clips via App-Key-Authentifizierung hoch.
2.  **FastAPI (API):** Validiert Quotas und speichert Metadaten in PostgreSQL sowie Medien im verschlüsselten Speicher.
3.  **Celery Worker:** Führt asynchrone Aufgaben aus (KI-Zweitmeinung, Versand von Cloud-Notifications).
4.  **Django (Web-UI):** Ermöglicht das Browsen der Timeline, Video-Streaming und die Admin-Verwaltung der Nutzer.

### 7.2 Cloud-spezifische Konventionen
*   **Authentifizierung:** App-Keys müssen in der Android-App unter *Einstellungen → Cloud-Server* hinterlegt werden.
*   **Kryptographie:** Passwort-Hashing via PBKDF2/Argon2; Cloud-Passwort dient als Root-Key für die serverseitige Verschlüsselung.
*   **Deployment:** Der gesamte Stack wird via Docker Compose (Ports 8000 API, 8080 Web-UI) bereitgestellt.

### 7.3 Wichtige Befehle

- Stack starten: `docker compose up --build -d`
- Android-App verbinden: in der Haven 2.0 Android-App -> `API-URL: http://<server-ip>:8000`
- Web UI: `http://localhost:8080`

- Detaillierte Beschreibung in der Datei "Cloud Server QUICKSTART.md"
