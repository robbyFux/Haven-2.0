# Phase 4: NotificationEngine — Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.

**Date:** 2026-04-05
**Mode:** Text mode (--text flag)

---

## Area 1: Channel config UI

**Question:** Wie gibt der Nutzer die Verbindungsdaten für Signal und Mattermost ein?

**Options presented:**
1. Inline in SettingsScreen — Textfelder direkt in der langen Einstellungsseite
2. Dedizierte Sub-Screens per Kanal (wie ZoneEditorScreen)
3. Card mit Expand/Inline-Dialog

**Selected:** 3 — Card mit AlertDialog (Klick öffnet AlertDialog analog PIN-Dialog)

**Follow-up:** Dialog oder Expand-in-place?
- 1. Aufklappbare Card (Expand in-place)
- 2. Inline-Dialog (Modal)

**Selected:** Not answered — captured as Claude's Discretion (AlertDialog analog PIN-Dialog)

---

## Area 2: Threshold & Cooldown scope

**Question:** Werden Schwellwerte und Anti-Flood per-Kanal oder global konfiguriert?

**Options presented:**
1. Globale NotificationRule (ein Schwellwert + ein Cooldown für alle Kanäle)
2. Pro-Kanal unabhängig (Signal und Mattermost jeweils eigene Regel)
3. Hybrid: globaler Schwellwert + pro-Kanal Cooldown

**Selected:** 1 — Globale NotificationRule

---

## Area 3: Settings grouping style

**Question:** Wie werden die 10+ Sektionen in SettingsScreen gruppiert?

**Options presented:**
1. Section-Header mit visueller Trennung (minimale Änderung)
2. Cards pro Kategorie
3. Beide: Card-Container + Section-Header innerhalb

**Selected:** 3 — Cards pro Kategorie + Section-Header innerhalb

---

## Area 4: Logging level behavior

**Question:** Was bewirkt der Schalter "Normal/Debug"?

**Options presented:**
1. Nur DiagnosticsScreen-Filter (AppLogger loggt alles, nur Anzeige gefiltert)
2. Capture-Level (Normal ignoriert DEBUG-Aufrufe im Buffer)
3. Beides: Capture + Display (Normal = kein DEBUG im Buffer, Debug = alles)

**Selected:** 3 — Capture + Display

---

## Area 5: Heartbeat & Media Attachments

**Question:** Heartbeat und Media Attachments — in Phase 4 oder defer?

**Options presented:**
1. Beide in Phase 4 (Heartbeat: konfigurierbares Intervall, Attachments: JPEG)
2. Nur Heartbeat in Phase 4, Attachments defer
3. Beides defer — nur Kanal + Regel + Threshold

**Selected:** 1 — Beide in Phase 4

---

## Additional Area: Trigger-Typ-Filter

**Question:** Wie werden Trigger-Typen für Benachrichtigungen gefiltert?

**Options presented:**
1. Nur Severity-Schwellwert — kein Typ-Filter
2. Typ-Whitelist in der NotificationRule (MultiSelect-Checkboxen, User-konfigurierbar)
3. Automatischer Typ-Filter: nur KI-Ereignisse + MICROPHONE (nicht konfigurierbar)

**Selected:** 2 — Typ-Whitelist (MultiSelect-Checkboxen)

---

## Additional Area: Attachment-Typ

**Question:** Was wird bei Mediensendung angehängt?

**Options presented:**
1. Nur Standbild (JPEG) — letztes CameraAnalyzer-Frame
2. Nur Video-Clip (.mp4, erfordert Dekryptierung)
3. Standbild für CAMERA-Trigger, kein Clip
4. Claude's Discretion

**Selected:** 1 — Nur Standbild (JPEG)

---

## Deferred (not discussed)

- Quiet Hours
- Signal-Empfänger (mehrere Nummern)
