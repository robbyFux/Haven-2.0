---
slug: release-script
created: 2026-05-19
status: complete
---

# Quick Task: Release-Script für APK und Cloud-Server

## Goal
Release-Prozess für Haven 2.0 vollständig automatisieren:
APK bauen, Docker-Images exportieren, GitHub Release anlegen.

## Tasks

- [x] `release.sh` am Projekt-Root erstellen
- [x] `app/build.gradle.kts`: Signing-Config via Umgebungsvariablen ergänzen
- [x] `.gitignore`: `dist/` eintragen
- [x] Todo-Datei nach `done/` verschieben
- [x] Commit erstellen
