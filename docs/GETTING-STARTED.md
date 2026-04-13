<!-- GSD:generated -->
<!-- generated-by: gsd-doc-writer -->

# Getting Started — Haven 2.0

Haven 2.0 besteht aus zwei unabhängigen Komponenten:

- **Android App** — Pflichtbestandteil; läuft vollständig offline, lokal.
- **Self-hosted Server** — Optional; für Cloud-Benachrichtigungen und Event-Archivierung via Browser.

Der Server wird nur benötigt, wenn der `CloudChannel` genutzt werden soll. Die App funktioniert ohne ihn vollständig.

---

## Voraussetzungen

### Android App

| Tool | Version | Hinweis |
|---|---|---|
| Android Studio | aktuell | Flamingo oder neuer |
| JDK | 17 | `java -version` muss `17.x` zeigen |
| Android SDK | compileSdk 35 | Im SDK Manager: "Android 15 (API 35)" |
| Gerät / Emulator | Android 8.0+ (API 26+) | Physisches Gerät empfohlen (Sensoren) |

JDK 17 prüfen:

```bash
java -version
# java version "17.x.x" ...
```

### Server (optional)

| Tool | Version |
|---|---|
| Docker | 24+ |
| Docker Compose | v2 (Plugin, `docker compose`) |

---

## Android App: Bauen und Installieren

### 1. Repository klonen

```bash
git clone <repo-url> "Haven 2.0"
cd "Haven 2.0"
```

### 2. Syntaxcheck (optional, ~35 s)

```bash
./gradlew :app:compileDebugKotlin
```

### 3. Debug-APK bauen

```bash
./gradlew :app:assembleDebug
```

Das APK landet unter `app/build/outputs/apk/debug/app-debug.apk`.

### 4. Auf Gerät installieren

```bash
# Gerät per USB verbinden, USB-Debugging aktivieren
./gradlew :app:installDebug
```

Alternativ APK-Datei manuell auf das Gerät übertragen und installieren.

---

## TFLite-Modell (optional)

Ohne das Modell läuft die App mit reiner Bewegungserkennung (`MOTION_ONLY`) — kein Crash, kein Fehler.

Das Modell ist **bereits in `app/src/main/assets/efficientdet_lite0.tflite` enthalten**, wenn es beim Klonen des Repos vorhanden war. Falls nicht (Datei fehlt oder ist leer):

```bash
curl -L -o app/src/main/assets/efficientdet_lite0.tflite \
  "https://storage.googleapis.com/download.tensorflow.org/models/tflite/task_library/object_detection/android/lite-model_efficientdet_lite0_detection_metadata_1.tflite"
```

Dateigröße: ~4 MB. Nach dem Download erneut `./gradlew :app:assembleDebug` ausführen.

---

## Erste Nutzung der App

### Berechtigungen

Beim ersten Start fragt die App folgende Berechtigungen ab:

| Berechtigung | Zweck |
|---|---|
| `CAMERA` | Kamera-Analyse, Videoclips |
| `RECORD_AUDIO` | Mikrofon-Überwachung |
| `POST_NOTIFICATIONS` | Systembenachrichtigungen (Android 13+) |

Alle Hardware-Features (`camera`, `microphone`, `accelerometer`, `gyroscope`) sind mit `android:required="false"` deklariert — die App startet auch auf Geräten ohne Kamera oder Gyroskop.

### Ablauf: Erster Start

1. App öffnen. Bei aktivierter PIN-Sperre erscheint `PinLockScreen` — beim Erststart ist kein PIN gesetzt.
2. **Monitor**-Tab ist aktiv. Auf **Starten** tippen.
3. Countdown läuft (Standard: 60 Sekunden). Danach startet der Kalibrierungs-Wizard (~10 s): jeder Sensor bestimmt seinen Noise-Floor.
4. Nach der Kalibrierung ist die Überwachung **AKTIV** (roter Badge).
5. Auf **Stoppen** tippen (oder via Systembenachrichtigung). Die letzten 30 Sekunden werden retroaktiv aus der Datenbank gelöscht (Stop-Cooldown verhindert Fehlalarme beim Herangehen).

### Benachrichtigungskanäle konfigurieren

Unter **Einstellungen → Benachrichtigungen** können ein oder mehrere Kanäle aktiviert werden:

| Kanal | Benötigte Daten |
|---|---|
| Signal | signal-cli REST API URL + Bearer JWT |
| Mattermost | Incoming Webhook URL |
| Pushover | User Key + App Token |
| Cloud (Haven Server) | Server URL + App-Key (aus dem Web UI) |

---

## Rauchtest: Ersten Trigger auslösen

Nach dem Aktivieren der Überwachung:

1. Gerät leicht schütteln (Beschleunigungssensor) oder kurz in die Kamera tippen (Lichtwechsel).
2. Im **Timeline**-Tab erscheint ein neuer Eintrag mit Trigger-Typ, Severity und Timestamp.
3. Auf den Eintrag tippen → `EventDetailScreen` zeigt alle Trigger-Details und ggf. den aufgezeichneten Videoclip.

Wenn kein Trigger erscheint: Sensitivity unter **Einstellungen** auf `HIGH` erhöhen.

---

## Server (optional): Docker-Compose-Setup

### 1. In das Server-Verzeichnis wechseln

```bash
cd server/
```

### 2. `.env`-Datei anlegen

```bash
cp .env.example .env
```

Mindestens diese Werte in `.env` anpassen:

```dotenv
SECRET_KEY=<zufälliger 64-Zeichen-String>
DATABASE_URL=postgresql+asyncpg://haven:haven@db:5432/haven
REDIS_URL=redis://redis:6379/0
```

Den `DATABASE_URL`-Host auf `db` (Compose-Servicename) setzen — nicht `localhost`.

Optionale Einstellungen für Benachrichtigungen und KI-Backend: siehe [`docs/CONFIGURATION.md`](CONFIGURATION.md).

### 3. Container starten

```bash
docker compose up -d
```

Beim ersten Start werden Images gebaut (~2–3 min). Danach laufen fünf Services:

| Service | URL | Zweck |
|---|---|---|
| `app` (FastAPI) | `http://localhost:8000` | REST API |
| `webui` (Django) | `http://localhost:8080` | Browser-Dashboard |
| `db` (PostgreSQL) | Port 5432 | Datenbank |
| `redis` | Port 6379 | Celery Broker |
| `worker` (Celery) | — | KI-Analyse + Notifications |

### 4. Datenbank initialisieren

```bash
docker compose exec app alembic upgrade head
```

### 5. Admin-Account anlegen

```bash
docker compose exec webui python manage.py createsuperuser
```

### 6. Ersten Login

Browser: `http://localhost:8080` → mit dem angelegten Superuser anmelden.

### 7. Gerät registrieren

1. Im Web UI: **Devices → Add Device** → Name vergeben → App-Key (`hav_<64hex>`) wird generiert.
2. In der Android App: **Einstellungen → CloudChannel** → Server URL + App-Key eintragen.

### Logs prüfen

```bash
docker compose logs -f app worker
```

---

## Häufige Setup-Probleme

### Gradle-Build schlägt fehl: `Could not resolve ...`

Gradle-Cache leeren und erneut versuchen:

```bash
./gradlew clean :app:assembleDebug
```

### `app` Container startet nicht — `DATABASE_URL`-Fehler

Sicherstellen, dass in `.env` der Host `db` (nicht `localhost`) steht:

```
DATABASE_URL=postgresql+asyncpg://haven:haven@db:5432/haven
```

### Kein Trigger trotz Bewegung

- Sensitivity unter **Einstellungen** auf `HIGH` setzen.
- Kalibrierungszeit abwarten (Standard: 10 s nach dem Countdown).
- **Diagnostics**-Screen prüfen: zeigt berechnete Noise-Floor-Werte und ob das TFLite-Modell geladen wurde.

### TFLite-Modell nicht erkannt

`DiagnosticsScreen` zeigt `MediaPipe: nicht verfügbar`. Prüfen:

```bash
ls -lh app/src/main/assets/efficientdet_lite0.tflite
# Erwartet: ~4 MB
```

Falls fehlend: Download-Befehl aus dem Abschnitt [TFLite-Modell](#tflite-modell-optional) ausführen und App neu bauen.

---

## Nächste Schritte

- [`docs/ARCHITECTURE.md`](ARCHITECTURE.md) — Systemarchitektur, Komponentendiagramm, Datenfluss
- [`docs/CONFIGURATION.md`](CONFIGURATION.md) — Alle Android-Einstellungen und Server-Umgebungsvariablen im Detail
