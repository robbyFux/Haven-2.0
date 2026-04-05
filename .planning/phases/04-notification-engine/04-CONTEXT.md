# Phase 4: NotificationEngine — Context

**Gathered:** 2026-04-05
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 4 delivers the outbound alerting system:

1. **NotificationEngine** — Signal (signal-cli REST API) + Mattermost (Incoming Webhook) als konfigurierbare Kanäle. Jeder Kanal unabhängig aktivierbar mit eigenen Verbindungsdaten.
2. **Globale NotificationRule** — Ein Schwellwert (minSeverity), ein Anti-Flood-Cooldown, und eine Trigger-Typ-Whitelist steuern gemeinsam, wann und was gesendet wird.
3. **Heartbeat** — Konfigurierbares "Ich lebe noch"-Intervall pro Kanal (Signal + Mattermost).
4. **Media Attachments** — Optionaler JPEG-Standbild-Anhang (letztes CameraAnalyzer-Frame) bei CAMERA-Triggern.
5. **Settings-Gruppierung** — Bestehende 10+ Sektionen werden in Cards pro Kategorie gegliedert (Erkennung / Aufzeichnung / Sicherheit / Benachrichtigungen / App).
6. **App-Logging-Level** — Normal/Debug Toggle: steuert sowohl Capture (was in AppLogger-Buffer geht) als auch Display (was DiagnosticsScreen zeigt).

Phase 4 schließt **nicht** ein: WebRTC, Multi-Device Pairing, Zeitpläne (Armed/Disarmed), Quiet Hours, Verschlüsselung der Anhänge vor dem Senden.

</domain>

<decisions>
## Implementation Decisions

### Notification Channels

- **D-01:** Zwei Kanäle: `SignalRestChannel` + `MattermostChannel`. Beide implementieren das `NotificationChannel`-Interface aus CLAUDE.md (`send(event, attachment)` + `sendHeartbeat()`).
- **D-02:** Kanalverwaltung in SettingsScreen: Pro Kanal eine **Card mit AlertDialog-Konfiguration** — analog zum bestehenden PIN-Dialog-Pattern. Klick auf "Signal konfigurieren" / "Mattermost konfigurieren" öffnet einen AlertDialog mit den benötigten Feldern.
  - Signal-Dialog-Felder: Server-URL, Absender-Nummer, Empfänger-Nummer, optionaler Bearer-JWT-Token.
  - Mattermost-Dialog-Felder: Webhook-URL.
- **D-03:** Alle Verbindungsdaten werden in DataStore gespeichert (bestehende `KEY_`-Konvention in `SettingsRepository`). Kein Android Keystore für diese Konfigurationsfelder in Phase 4.
- **D-04:** Claude's Discretion: Genaues Layout des AlertDialogs (Felder-Reihenfolge, Validierung, Speichern-Button) — Orientierung am bestehenden `PinSetupDialog`.

### NotificationRule (Globale Regel)

- **D-05:** Eine einzige globale `NotificationRule` gilt für alle aktivierten Kanäle:
  - `minSeverity: Severity` — Mindestschwellwert (LOW / MEDIUM / HIGH / CRITICAL). Events unterhalb werden nicht gesendet.
  - `cooldownMs: Long` — Anti-Flood: Nach einer Benachrichtigung wird für diese Dauer keine weitere gesendet. Konfigurierbar: Off / 1 min / 5 min / 15 min / 30 min.
  - `triggerTypes: Set<TriggerType>` — Whitelist welche Sensortypen Benachrichtigungen auslösen. MultiSelect-Checkboxen in der Regelkonfiguration. Sinnvolle Defaults: CAMERA, CAMERA_PERSON, CAMERA_PET, CAMERA_VEHICLE, MICROPHONE aktiviert; LIGHT, ACCELEROMETER, GYROSCOPE deaktiviert.
  - `attachMedia: Boolean` — Ob bei CAMERA-Triggern ein JPEG-Standbild angehängt wird. Gilt nur für SignalRestChannel (Mattermost: kein Attachment).
- **D-06:** Die NotificationRule wird in DataStore persistiert (einzelne Felder, nicht serialisiertes Objekt).
- **D-07:** `quietHours` aus CLAUDE.md-Architektur wird in Phase 4 **nicht** implementiert — deferred.

### Heartbeat

- **D-08:** Heartbeat wird pro Kanal konfiguriert: Off / 15 min / 30 min / 60 min.
- **D-09:** Heartbeat läuft als periodische WorkManager-Task oder als wiederholter `delay` in einem Coroutine-Loop im MonitorService (Claude's Discretion: Implementierungsstrategie).
- **D-10:** Heartbeat-Nachricht enthält: Zeitstempel, App-Version, aktuellen MonitorState.

### Media Attachments

- **D-11:** Nur Standbild (JPEG) — kein Video-Clip. Letztes Kamera-Frame aus `CameraAnalyzer` wird als JPEG base64-kodiert in den Signal-REST-Body eingebettet.
- **D-12:** `CameraAnalyzer` hält das letzte `ImageProxy`-Bitmap als `ByteArray?` (nullable, nur wenn CAMERA-Trigger aufgetreten). `NotificationRouter` liest dieses bei Bedarf.
- **D-13:** Attachment nur bei CAMERA-Triggern (CAMERA, CAMERA_PERSON, CAMERA_PET, CAMERA_VEHICLE). Bei anderen Trigger-Typen kein Anhang, auch wenn `attachMedia = true`.

### Settings-Gruppierung

- **D-14:** SettingsScreen wird in **Cards pro Kategorie** umgebaut. Jede Card ist ein `surfaceVariant`-Container mit `RoundedCornerShape(12.dp)` — konsistent mit bestehenden Card-Konventionen aus CLAUDE.md.
- **D-15:** Innerhalb jeder Card bleiben die bestehenden `SettingsSection`-Blöcke (titleSmall, primary color) als Unterüberschriften erhalten.
- **D-16:** Fünf Kategorie-Cards:
  1. **Erkennung** — Sensitivity, Kamera, Detektionsmodus, Zone, Sensoren, Licht-Unterdrückung
  2. **Aufzeichnung** — Countdown, Kalibrierung, Clip-Dauer
  3. **Sicherheit** — PIN, Auto-Lock, Medienverschlüsselung
  4. **Benachrichtigungen** — Signal, Mattermost, NotificationRule (neu Phase 4)
  5. **App** — Sprache, Logging-Level (neu Phase 4), Diagnose, Version

### App-Logging Level

- **D-17:** Zwei Stufen: **Normal** und **Debug**.
  - **Normal:** `AppLogger.log()` ignoriert `Level.DEBUG`-Aufrufe — kein Eintrag im Ring-Buffer, kein Forwarding an `android.util.Log`.
  - **Debug:** Alle 4 Levels (DEBUG/INFO/WARN/ERROR) werden gecaptured und angezeigt.
- **D-18:** Aktuelles Level wird in DataStore persistiert (`KEY_LOG_LEVEL`). Default: Normal.
- **D-19:** `AppLogger` liest das Level aus einem injiziertem `Flow<LogLevel>` oder einer atomaren Variable (Claude's Discretion: Thread-safe Implementierung). Kein Neustart nötig — Änderung wirkt sofort.
- **D-20:** DiagnosticsScreen zeigt weiterhin alle gecaptureten Einträge ohne zusätzlichen Filter — der Filter ist jetzt im Capture-Layer, nicht im Display-Layer.

### Claude's Discretion

- Genaues Layout der Kanal-Konfigurations-Dialoge (Felder, Validierung)
- Heartbeat-Implementierungsstrategie (WorkManager vs. MonitorService-Coroutine)
- Thread-sichere Implementierung des LogLevel-Checks in `AppLogger`
- Signal-REST-API-Fehlerbehandlung (Retry-Strategie, Timeout)
- Anzahl der Signal-Empfängernummern in Phase 4 (empfohlen: eine Nummer, erweiterbar)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Architektur-Spezifikation (NotificationEngine)
- `CLAUDE.md` §NotificationEngine — Interface-Definitionen, `NotificationRule`-Felder, Signal-REST-API-Endpunkt, Mattermost-Payload-Format

### Bestehende Settings-Integration (zu erweitern)
- `app/src/main/java/org/havenapp/main/storage/SettingsRepository.kt` — DataStore-Pattern für neue Keys
- `app/src/main/java/org/havenapp/main/ui/settings/SettingsScreen.kt` — Bestehende Sektionen (umbauen zu Cards), `SettingsSection`/`SensorToggleRow`-Composables wiederverwenden
- `app/src/main/java/org/havenapp/main/ui/settings/SettingsViewModel.kt` — StateFlow-Pattern für neue Settings

### AppLogger (zu erweitern)
- `app/src/main/java/org/havenapp/main/storage/AppLogger.kt` — LogLevel-Filter einbauen

### CameraAnalyzer (Attachment-Quelle)
- `app/src/main/java/org/havenapp/main/media/CameraAnalyzer.kt` — Letztes Frame für JPEG-Attachment

### Event-Typen und Severity
- `app/src/main/java/org/havenapp/main/events/TriggerType.kt` — Alle TriggerType-Werte für Whitelist
- `app/src/main/java/org/havenapp/main/events/Severity.kt` — LOW/MEDIUM/HIGH/CRITICAL
- `app/src/main/java/org/havenapp/main/events/TriggerEvent.kt` — Event-Felder

### Bestehende Dialog-Referenz
- `app/src/main/java/org/havenapp/main/ui/settings/SettingsScreen.kt` §PinSetupDialog — AlertDialog-Pattern für Kanal-Konfiguration übernehmen

### MonitorService (Heartbeat + Routing)
- `app/src/main/java/org/havenapp/main/MonitorService.kt` — Trigger-Verarbeitungs-Loop; hier `NotificationRouter.route()` aufrufen

### Neue Packages (zu erstellen)
- `app/src/main/java/org/havenapp/main/notify/` — `NotificationChannel.kt`, `SignalRestChannel.kt`, `MattermostChannel.kt`, `NotificationRouter.kt`, `NotificationRule.kt`

### String Resources
- `app/src/main/res/values/strings.xml` — neue Labels für Benachrichtigungs-Settings
- `app/src/main/res/values-de/strings.xml` — deutsche Übersetzungen

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `PinSetupDialog` in `SettingsScreen.kt` — AlertDialog mit OutlinedTextField-Feldern: direkt als Vorlage für Signal/Mattermost-Konfigurations-Dialoge
- `SettingsSection(title, content)` composable — bleibt innerhalb der neuen Category-Cards erhalten
- `SensorToggleRow` composable — für "Kanal aktiviert"-Toggles und Trigger-Typ-Checkboxen wiederverwendbar
- `AppLogger` Ring-Buffer mit `StateFlow<List<Entry>>` — nur LogLevel-Check in `log()` ergänzen

### Established Patterns
- DataStore: `KEY_X = typePreferencesKey("x")` + `Flow<T>` in `SettingsRepository` + `suspend fun setX()` — für alle neuen NotificationRule- und Kanal-Settings
- MonitorService liest Settings via `.first()` Snapshot bei Session-Start — `NotificationRouter` analog initialisieren
- `RecentTriggerState`-Pattern (plain Kotlin object) — falls ein prozess-globaler Cooldown-State nötig ist
- Cards: `surfaceVariant` containerColor + `RoundedCornerShape(12.dp)` — konsistent in Timeline und EventDetail

### Integration Points
- `MonitorService.kt`: Nach `eventRepository.recordTrigger()` → `notificationRouter.route(event)` aufrufen
- `CameraAnalyzer.kt`: Letztes valides Bitmap als `ByteArray?` halten, via Flow oder SharedState an Router übergeben
- `SettingsRepository.kt`: ~10 neue DataStore-Keys (Signal-URL, Signal-Sender, Signal-Recipient, Signal-JWT, Mattermost-URL, minSeverity, cooldownMs, triggerTypeWhitelist als String-Set, attachMedia, heartbeatIntervalSignal, heartbeatIntervalMattermost, logLevel)
- `HavenNavGraph.kt`: Keine neuen Routes nötig (Konfiguration per Dialog, kein Sub-Screen)

</code_context>

<specifics>
## Specific Ideas

**Aus der Diskussion:**
- Signal-Empfänger: zunächst eine Nummer — erweiterbar in zukünftiger Phase
- Attachment: `< 200 KB JPEG` — geeignet für mobile Alerts; Video-Clip bleibt lokal und ist über EventDetailScreen abrufbar
- Trigger-Typ-Whitelist-Defaults: CAMERA, CAMERA_PERSON, CAMERA_PET, CAMERA_VEHICLE, MICROPHONE = aktiviert; LIGHT, ACCELEROMETER, GYROSCOPE = deaktiviert (zu viele False Positives)
- Settings-Cards: Kategorie-Card ohne eigene Klick-Aktion — nur visueller Container, kein Expand

**User phrasing (DE):**
- "Überfluten der Kanäle verhindern" → Anti-Flood Cooldown (D-05)
- "Schwellwerte (niedrig bis kritisch) konfigurierbar" → `minSeverity` in NotificationRule (D-05)
- "Gruppierung in der Konfiguration" → Category-Cards (D-14–D-16)
- "Zwei Stufen Normal und Debug" → Capture + Display Level (D-17–D-20)

</specifics>

<deferred>
## Deferred Ideas

- **Quiet Hours** (`quietHours: TimeRange?`) — in CLAUDE.md architekturiert, aber nicht in Phase 4 umgesetzt → Phase 5
- **Mehrere Signal-Empfänger** — Phase 4 unterstützt eine Empfängernummer; Liste mit Add/Remove → spätere Phase
- **Video-Clip als Anhang** — Clips sind verschlüsselt (.enc); Dekryptierung + Senden komplex → Phase 5
- **SignalIntentChannel** — Fallback via lokale Signal-App-Intent (CLAUDE.md P2) → Phase 5
- **NotificationRule per Kanal** — Phase 4 hat globale Regel; pro-Kanal Override → Phase 5
- **Timeline-Level Filter** (Events nach Sensor-Typ filtern, nicht nur Trigger innerhalb Event) — aus Phase 3 CONTEXT.md Deferred → Phase 5/UX

</deferred>

---

*Phase: 04-notification-engine*
*Context gathered: 2026-04-05*
