# Requirements: Haven 2.0

**Defined:** 2026-03-31
**Core Value:** Zuverlässige, privacy-respektierende Bewegungserkennung — die App muss starten, kalibrieren und Ereignisse erfassen, ohne dass etwas stillschweigend fehlschlägt.

## v1 Requirements

Phase-2-Fehlerbehebung: Alle bekannten Bugs, die die vollständige Nutzbarkeit von Phase 2 blockieren.

### TFLite / KI-Erkennung

- [ ] **TFLITE-01**: KI-Erkennungsmodi (PERSON, PET, VEHICLE, ALL) sind in den Einstellungen wählbar — `isAvailable` muss `true` sein sobald das Modell geladen ist
- [ ] **TFLITE-02**: TFLite-Initialisierung erfolgt beim App-Start (unabhängig vom aktiven Screen), sodass `availabilityFlow` vor dem ersten Screen-Render emittiert
- [ ] **TFLITE-03**: DiagnosticsScreen zeigt TFLite-Status reaktiv — aktualisiert sich sobald Initialisierung abgeschlossen ist

### Erkennungszonen

- [ ] **ZONE-01**: Im ZoneEditorScreen gezeichnete Zone wird in DataStore gespeichert und überlebt App-Neustart
- [ ] **ZONE-02**: Gespeicherte Zone wird beim Monitoring korrekt aus DataStore geladen und an CameraAnalyzer übergeben

### Ereignis-Verwaltung

- [ ] **EVENT-01**: Nutzer kann einzelne Ereignisse aus der Timeline löschen (Swipe-to-delete oder Delete-Button in EventDetailScreen)
- [ ] **EVENT-02**: Löschvorgang entfernt zugehörige EventTriggerEntities und das HavenEvent aus Room

### Sensor / Kamera Stabilität

- [ ] **SENSOR-01**: Nach TFLite-Fix: CameraAnalyzer läuft stabil ohne Crashes bei aktivem ML-Modus
- [ ] **SENSOR-02**: FusedMotionMonitor verursacht keine ungewollten Trigger während der Kalibrierungsphase

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

- **SEC-01**: Android Keystore Verschlüsselung aller Medien
- **SEC-02**: Telefon-Manipulations-Erkennung
- **SEC-03**: Audit-Logs

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
| TFLITE-01 | Phase 3 | Pending |
| TFLITE-02 | Phase 3 | Pending |
| TFLITE-03 | Phase 3 | Pending |
| ZONE-01 | Phase 3 | Pending |
| ZONE-02 | Phase 3 | Pending |
| EVENT-01 | Phase 3 | Pending |
| EVENT-02 | Phase 3 | Pending |
| SENSOR-01 | Phase 3 | Pending |
| SENSOR-02 | Phase 3 | Pending |

**Coverage:**
- v1 requirements: 9 total
- Mapped to phases: 9
- Unmapped: 0 ✓

---
*Requirements defined: 2026-03-31*
*Last updated: 2026-03-31 — traceability updated to Phase 3 after roadmap creation*
