# Haven 2.0

## What This Is

Haven 2.0 ist eine privacy-zentrierte, hochsichere Android-Überwachungsapp (Kotlin).
Sie kombiniert sensorische Tiefe (Beschleunigungssensor, Gyroskop, Mikrofon, Licht, Kamera) mit
KI-basierter Objekterkennung (TFLite/EfficientDet) und verschlüsselter lokaler Speicherung —
ohne Cloud-Abhängigkeit, ohne Tracking. Für Nutzer, die physische Sicherheit brauchen und
Datenschutz ernst nehmen.

## Core Value

Zuverlässige, privacy-respektierende Bewegungserkennung — die App muss starten, kalibrieren
und Ereignisse erfassen, ohne dass etwas stillschweigend fehlschlägt.

## Requirements

### Validated

- ✓ Foreground Service mit WakeLock (MonitorService) — Phase 1
- ✓ State Machine IDLE → COUNTDOWN → CALIBRATING → ACTIVE — Phase 1
- ✓ FusedMotionMonitor (Accel + Gyro Complementary Filter, SENSOR_DELAY_GAME) — Phase 2/3
- ✓ LightMonitor dual-rate EMA (emaFast α=0.1, emaSlow α=0.02) + cross-sensor Suppression — Phase 1/2/3
- ✓ RecentTriggerState (prozess-globaler Trigger-Zeitstempel-Tracker) — Phase 3
- ✓ MicrophoneMonitor (dezibel-basiert) — Phase 1
- ✓ CameraAnalyzer 3-Stufen-Pipeline (Luma-Diff → pHash → TFLite) — Phase 2
- ✓ HavenObjectDetector (EfficientDet Lite 0, graceful degradation) — Phase 2
- ✓ DetectionZone (normalisierte Koordinaten, ZoneEditorScreen) — Phase 2
- ✓ Room-Datenbank (HavenEvent + EventTriggerEntity) — Phase 1
- ✓ DataStore Settings (Sensitivity, CameraPosition, DetectionMode, Zone, PIN, AutoLock, MediaEncryption …) — Phase 1/2/3
- ✓ UI: MonitorScreen, TimelineScreen, EventDetailScreen (FilterChip-Filter), SettingsScreen, DiagnosticsScreen — Phase 1/2/3
- ✓ Kalibrierungs-Wizard (Noise-Floor-Anzeige) — Phase 2
- ✓ Stop-Cooldown (30 s retroaktiv verwerfen) — Phase 2
- ✓ AppLogger (In-App-Ringpuffer, DiagnosticsScreen) — Phase 2
- ✓ ClipRecorder (CameraX-Videoaufzeichnung bei Trigger) — Phase 3 (REC-01–03)
- ✓ MediaEncryptionManager (AES-GCM via Android Keystore, konfigurierbar) — Phase 3 (SEC-01–02)
- ✓ AppLockState + PinHashManager + PinLockScreen (PIN-Sperre, ProcessLifecycleOwner Auto-Lock) — Phase 3 (SEC-03–05)
- ✓ ExoPlayer-Videowiedergabe in EventDetailScreen (verschlüsselte .enc-Dekryptierung) — Phase 3
- ✓ HavenAlertChannel-Interface + NotificationRouter (Severity-Filter, Trigger-Whitelist, Anti-Flood-Cooldown) — Phase 4 (NOTIF-01)
- ✓ SignalRestChannel (signal-cli REST API, Bearer-Auth, base64-JPEG-Anhang) — Phase 4 (NOTIF-02)
- ✓ MattermostChannel (Incoming Webhook, Markdown, username "Haven", ":shield:") — Phase 4 (NOTIF-03)
- ✓ PushoverChannel (multipart POST, Priority-Mapping, JPEG-Anhang, Emergency-Priority für CRITICAL) — Phase 4 (NOTIF-03+)
- ✓ NotificationRule-Settings-UI (minSeverity, Cooldown, Trigger-Whitelist, attachMedia, Channel-Dialoge) — Phase 4 (NOTIF-04)
- ✓ Settings in 5 CategoryCards gruppiert — Phase 4 (NOTIF-04)
- ✓ Heartbeat-Coroutinen pro Kanal (Off/15/30/60 min, auto-cancelled via structured concurrency) — Phase 4 (NOTIF-05)
- ✓ AppLogger LogLevel (Normal/Debug), sofort wirksam + DataStore-persistent — Phase 4
- ✓ Event-Löschung löscht zugehörige Mediendateien (Video-Clips) — Quick Fix

### Active

- [ ] **BUG-01**: TFLite-Initialisierung — KI-Erkennungsmodi bleiben in Settings deaktiviert weil `isAvailable=false` trotz vorhandener Modelldatei (Chicken-and-Egg-Loop)
- [ ] **BUG-02**: Erkennungszonen speichern — Zone lässt sich im ZoneEditorScreen zeichnen, wird aber nicht in DataStore persistiert
- [ ] **BUG-03**: Ereignisse löschen — kein Delete-Button in TimelineScreen / EventDetailScreen
- [ ] **BUG-04**: Sensor/Kamera-Stabilität — weitere Phase-2-Probleme (CameraAnalyzer, FusedMotionMonitor) nach TFLite-Fix evaluieren

### Out of Scope

- WebRTC / Multi-Device Pairing — Phase 5
- Keystore-Verschlüsselung der Medien — Phase 5/6
- Self-hosted Backend — Phase 5/6
- Zeitpläne (Armed/Disarmed/Night) — Phase 5

## Context

**Aktueller Stand:** Phase 4 abgeschlossen (2026-04-06). NotificationEngine vollständig implementiert:
SignalRestChannel (signal-cli REST API), MattermostChannel (Webhook), PushoverChannel (Pushover API)
mit konfigurierbaren Severity-Schwellwerten, Anti-Flood-Cooldown, Trigger-Whitelist, Medienanhängen
und Heartbeat-Timern pro Kanal. Settings in 5 CategoryCards gruppiert. AppLogger LogLevel (Normal/Debug).
Pushover und Mattermost auf Gerät verifiziert. Event-Löschung löscht jetzt auch zugehörige Mediendateien.

**Referenz-Apps analysiert:** Haven 0.2.1 (Java, legacy), AlfredCamera (decompiled Protobuf-Schemas).

**TFLite-Status:** Modell `efficientdet_lite0.tflite` ist in assets vorhanden (~4.5 MB).
Initialisierungsfehler in `SettingsViewModel` und `DiagnosticsViewModel` wurde identifiziert
und teilweise behoben — Commit `e2a88cc` und `6045edb`.

**Architektur:** MVVM + Clean Architecture, Jetpack Compose, Hilt DI, Room, DataStore,
CameraX, Kotlin Coroutines/Flow. Kein XML, kein Java-Neucode.

## Constraints

- **Tech Stack**: Kotlin only — kein neues Java
- **Android**: minSdk 26, targetSdk 35
- **Privacy**: Keine Google Cloud Services, keine Firebase Analytics
- **Verschlüsselung**: Keine unverschlüsselten Medien persistieren (ab Phase 5 Keystore)
- **DI**: Hilt — keine manuellen Konstruktoren für injizierte Klassen
- **UI**: Jetpack Compose + Material Design 3, Dark-first

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| TFLite eager init in HavenApplication | Bricht Chicken-and-Egg-Loop; `initialize()` läuft beim App-Start unabhängig vom aktiven Screen | — Pending |
| DiagnosticsViewModel reaktiv auf availabilityFlow | `isAvailable` war Snapshot → kein UI-Update nach Init; Flow behebt das | — Pending |
| FusedMotionMonitor statt separater Accel/Gyro-Monitore | Complementary Filter reduziert False Positives bei Tisch-Vibrationen | ✓ Good |
| 3-Stufen Kamera-Pipeline (Luma → pHash → TFLite) | TFLite nur bei bestätigter Bewegung → Akku-Optimierung | ✓ Good |
| Stop-Cooldown 30 s | Nutzer löst beim Herangehen Sensoren aus; retroaktives Löschen verhindert False Events | ✓ Good |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd:transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd:complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-04-04 — Phase 3 complete*
