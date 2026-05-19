---
created: 2026-05-18T00:00:00
title: Release-Script für APK und Cloud-Server
area: tooling
files: []
---

## Problem

Es gibt kein automatisiertes Script, das einen vollständigen Release-Prozess für Haven 2.0 abwickelt. Releases müssen manuell erstellt werden, was fehleranfällig und aufwändig ist.

## Solution

Ein Shell-Script (oder mehrere) erstellen, das folgende Schritte automatisiert:

1. **Versionsnummer bestimmen und setzen:**
   - Versionsnummer interaktiv abfragen oder als Parameter übergeben (z.B. `./release.sh 1.2.0`)
   - `versionName` und `versionCode` in `app/build.gradle.kts` aktualisieren
   - Versionnummer auch im Cloud-Server setzen (z.B. `server/version.py` oder `pyproject.toml`)

2. **APK bauen:**
   - `./gradlew :app:assembleRelease` (signiertes Release-APK)
   - APK-Datei mit Versionsnummer umbenennen: `haven-v1.2.0.apk`

3. **Cloud-Server Docker-Images bauen:**
   - `docker compose build` im Cloud-Server-Verzeichnis
   - Images als `.tar.gz` exportieren: `docker save ... | gzip > haven-cloud-v1.2.0.tar.gz`
   - Alternativ: Images auf Docker Hub pushen und im Release referenzieren

4. **GitHub Release erstellen:**
   - Git-Tag setzen: `git tag v1.2.0`
   - Tag pushen: `git push origin v1.2.0`
   - GitHub Release via `gh release create` anlegen mit:
     - APK als Asset
     - Docker-Image-Archiv als Asset
     - Auto-generierte Changelog-Notes aus Commits seit letztem Tag

5. **Voraussetzungen:** `gh` CLI installiert, GitHub-Authentifizierung, Android SDK / Keystore für signiertes APK, Docker.
