---
created: 2026-04-13T14:27:09.735Z
title: SMTP-Server Konfiguration über Admin-User in der WebUI
area: ui
files: []
---

## Problem

Derzeit gibt es keine Möglichkeit, SMTP-Server-Einstellungen (für E-Mail-Benachrichtigungen) direkt über die Web-UI zu konfigurieren. Admin-User müssen die SMTP-Konfiguration manuell in Umgebungsvariablen oder Konfigurationsdateien vornehmen, was die Einrichtung erschwert.

## Solution

Ein Admin-Bereich in der Django Web-UI implementieren, der SMTP-Einstellungen (Host, Port, User, Passwort, TLS/SSL) persistent speichert und beim E-Mail-Versand verwendet. Einstellungen sollten verschlüsselt in der Datenbank abgelegt und nur für Admin-User zugänglich sein.
