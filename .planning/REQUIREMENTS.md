# Requirements: Haven 2.0

**Defined:** 2026-03-31
**Core Value:** Zuverlässige, privacy-respektierende Bewegungserkennung — die App muss starten, kalibrieren und Ereignisse erfassen, ohne dass etwas stillschweigend fehlschlägt.

## v1 Requirements

Phase-2-Bugfix + fehlende Phase-2-Features: Bugfixes für vollständige Nutzbarkeit + Videoaufzeichnung bei Auslösung, Datenverschlüsselung und App-PIN.

### TFLite / KI-Erkennung

- [x] **TFLITE-01**: KI-Erkennungsmodi (PERSON, PET, VEHICLE, ALL) sind in den Einstellungen wählbar — `isAvailable` muss `true` sein sobald das Modell geladen ist
- [x] **TFLITE-02**: TFLite-Initialisierung erfolgt beim App-Start (unabhängig vom aktiven Screen), sodass `availabilityFlow` vor dem ersten Screen-Render emittiert
- [x] **TFLITE-03**: DiagnosticsScreen zeigt TFLite-Status reaktiv — aktualisiert sich sobald Initialisierung abgeschlossen ist

### Erkennungszonen

- [x] **ZONE-01**: Im ZoneEditorScreen gezeichnete Zone wird in DataStore gespeichert und überlebt App-Neustart
- [x] **ZONE-02**: Gespeicherte Zone wird beim Monitoring korrekt aus DataStore geladen und an CameraAnalyzer übergeben

### Ereignis-Verwaltung

- [x] **EVENT-01**: Nutzer kann einzelne Ereignisse aus der Timeline löschen (Swipe-to-delete oder Delete-Button in EventDetailScreen)
- [x] **EVENT-02**: Löschvorgang entfernt zugehörige EventTriggerEntities und das HavenEvent aus Room

### Sensor / Kamera Stabilität

- [x] **SENSOR-01**: Nach TFLite-Fix: CameraAnalyzer läuft stabil ohne Crashes bei aktivem ML-Modus
- [x] **SENSOR-02**: FusedMotionMonitor verursacht keine ungewollten Trigger während der Kalibrierungsphase

### Videoaufzeichnung bei Sensorauslösung

- [x] **REC-01**: Wenn ein Sensor auslöst, wird automatisch ein Video-Clip (Kamera + Ton) gestartet; Clip-Dauer konfigurierbar in Settings (10 s / 30 s / 60 s, Standard 30 s)
- [x] **REC-02**: Aufgezeichneter Clip wird dem auslösenden HavenEvent in Room zugeordnet (mediaPath in EventTriggerEntity) und im EventDetailScreen abspielbar
- [x] **REC-03**: Während eine Aufnahme läuft, kann eine weitere Auslösung keinen zweiten parallelen Clip starten (Cooldown bis Clip abgeschlossen)

### Datenverschlüsselung

- [ ] **SEC-01**: Aufgezeichnete Video- und Audio-Dateien werden AES-GCM-verschlüsselt im internen App-Speicher abgelegt (Android Keystore, `EncryptedFile` via `security-crypto`)
- [ ] **SEC-02**: Bestehende unverschlüsselte Medien-Dateien werden beim ersten App-Start nach dem Update migriert (verschlüsselt und Original gelöscht)

### App-PIN

- [ ] **SEC-03**: Optionaler App-PIN (4–6-stellig, in Settings aktivierbar) — wenn aktiv, muss der PIN beim App-Start korrekt eingegeben werden bevor die UI zugänglich ist
- [ ] **SEC-04**: App sperrt sich automatisch wenn sie in den Hintergrund geht (konfigurierbar: sofort / nach 30 s / nie)
- [ ] **SEC-05**: PIN-Hash wird als SHA-256(PIN + random Salt) in DataStore gespeichert (kein Klartext-PIN)

## v2 Requirements

Geplante Features für Phase 3+, noch nicht begonnen.

### Benachrichtigungen (Phase 3)

- **NOTIF-01**: NotificationEngine mit abstrahiertem Channel-Interface
- **NOTIF-02**: SignalRestChannel (signal-cli REST API, self-hosted)
- **NOTIF-03**: MattermostChannel (Incoming Webhook)
- **NOTIF-04**: NotificationRules-Editor (Trigger → Kanal)
- **NOTIF-05**: Heartbeat-Timer (Signal / Mattermost)

### Zeitpläne (Phase 3)

- **SCHED-01**: Armed/Disarmed-Zeitpläne
- **SCHED-02**: Timeline-Filter und Clip-Player

### Netzwerk (Phase 4)

- **NET-01**: WebRTC Live-Stream
- **NET-02**: QR-Code Pairing
- **NET-03**: Geräteverwaltung (Admin/Viewer/ReadOnly)

### Hardening (Phase 5)

- **HARD-01**: Telefon-Manipulations-Erkennung
- **HARD-02**: Audit-Logs
- **HARD-03**: Batterie-Optimierung

## Out of Scope

| Feature | Reason |
|---------|--------|
| Signal-CLI direkt einbetten | Veraltete API, nicht mehr wartbar — stattdessen REST API (Phase 3) |
| Firebase Analytics / AdServices | Privacy-Prinzip — keine Cloud-Dienste |
| Cloud-only Speicherung | Local-first ist Kernprinzip |
| Java-Neucode | Projekt ist vollständig Kotlin |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| TFLITE-01 | Phase 3 | Complete |
| TFLITE-02 | Phase 3 | Complete |
| TFLITE-03 | Phase 3 | Complete |
| ZONE-01 | Phase 3 | Complete |
| ZONE-02 | Phase 3 | Complete |
| EVENT-01 | Phase 3 | Complete |
| EVENT-02 | Phase 3 | Complete |
| SENSOR-01 | Phase 3 | Complete |
| SENSOR-02 | Phase 3 | Complete |
| REC-01 | Phase 3 | Complete |
| REC-02 | Phase 3 | Complete |
| REC-03 | Phase 3 | Complete |
| SEC-01 | Phase 3 | Pending |
| SEC-02 | Phase 3 | Pending |
| SEC-03 | Phase 3 | Pending |
| SEC-04 | Phase 3 | Pending |
| SEC-05 | Phase 3 | Pending |

**Coverage:**
- v1 requirements: 17 total
- Mapped to phases: 17
- Unmapped: 0 ✓

---
*Requirements defined: 2026-03-31*
*Last updated: 2026-04-02 — Phase 3 erweitert: Videoaufzeichnung (REC-01–03), Verschlüsselung (SEC-01–02), App-PIN (SEC-03–05)*
