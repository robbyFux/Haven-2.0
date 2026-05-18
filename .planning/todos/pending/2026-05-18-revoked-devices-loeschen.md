---
created: 2026-05-18T00:00:00.000Z
title: Revoked Devices aus der Geräteliste löschen können
area: ui
files:
  - server/webui/devices/views.py
  - server/webui/devices/templates/devices/list.html
  - server/webui/devices/urls.py
---

## Problem

Im WebUI können Geräte über `device_revoke` (views.py:67) auf `is_active=False` gesetzt werden, aber nicht dauerhaft gelöscht werden. Revoked Devices häufen sich in der Liste an und können nur deaktiviert, nicht entfernt werden.

## Solution

Delete-Button nur für bereits revozierte Geräte (`is_active=False`) einblenden. Neuer View `device_delete` in `views.py`: prüft Ownership, löscht den DB-Eintrag (und damit den App-Key). URL-Route in `urls.py` ergänzen. Template `list.html` um den Delete-Button in der Revoked-Zeile erweitern (HTMX für inline removal wie beim Revoke-Flow).
