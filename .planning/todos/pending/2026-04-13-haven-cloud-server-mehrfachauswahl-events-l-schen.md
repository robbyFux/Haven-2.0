---
created: 2026-04-13T11:10:58.215Z
title: Haven Cloud Server: Mehrfachauswahl für Events mit Bulk-Delete
area: ui
files:
  - server/webui/events/views.py
  - server/webui/events/templates/events/detail.html
---

## Problem

Im Haven Cloud Server WebUI können Events aktuell nur einzeln gelöscht werden. Es fehlt eine Möglichkeit, mehrere Events gleichzeitig auszuwählen und in einem Schritt zu löschen (Bulk-Delete).

## Solution

Checkboxen pro Event-Zeile in der Event-Liste ergänzen. „Alle auswählen"-Checkbox im Table-Header. Bulk-Delete-Button (mit Bestätigungsdialog) der die ausgewählten Event-IDs per POST/DELETE an den Server schickt. Server-seitig: neuer Endpoint oder erweiterter bestehender Endpoint in `events/views.py` der eine Liste von IDs entgegennimmt und alle zugehörigen Events + Medien löscht.
