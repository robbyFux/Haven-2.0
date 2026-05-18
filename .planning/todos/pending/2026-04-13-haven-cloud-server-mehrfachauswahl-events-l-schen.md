---
created: 2026-04-13T11:10:58.215Z
updated: 2026-05-18
title: Haven Cloud Server: Mehrfachauswahl für Events mit Bulk-Aktionen (Archivieren + Löschen)
area: ui
files:
  - server/webui/events/views.py
  - server/webui/events/templates/events/list.html
  - server/webui/events/templates/events/detail.html
---

## Problem

Im Haven Cloud Server WebUI können Events aktuell nur einzeln verwaltet werden. Es fehlt eine Möglichkeit, mehrere Events gleichzeitig auszuwählen und in einem Schritt zu **archivieren** oder zu **löschen** (Bulk-Aktionen).

## Solution

Checkboxen pro Event-Zeile in der Event-Liste ergänzen. „Alle auswählen"-Checkbox im Table-Header. Bulk-Aktionsleiste mit zwei Buttons: **Archivieren** und **Löschen** (jeweils mit Bestätigungsdialog). Die ausgewählten Event-IDs werden per POST an den Server geschickt.

Server-seitig: zwei Endpoints (oder ein kombinierter) in `events/views.py`:
- `bulk_archive`: setzt einen `archived`-Status auf den Events (kein Medien-Delete)
- `bulk_delete`: löscht Events + Medien vollständig

Archivierte Events sollten in der Standard-Übersicht ausgeblendet und über einen Filter/Tab wieder anzeigbar sein.
