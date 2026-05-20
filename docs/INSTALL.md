# Haven 2.0 – Installationsanleitung

Dieses Dokument beschreibt die Installation des **unsignierten APKs** auf Android und des **Cloud-Servers** via Docker auf Linux.

---

## Android-App (unsigniertes APK)

### Voraussetzungen

Android 8.0 oder neuer. Das APK ist **nicht signiert** — Android zeigt eine Sicherheitswarnung, die Installation ist aber problemlos möglich.

### Schritt 1 – Installation aus unbekannten Quellen erlauben

Die Einstellung ist pro App (dem Programm, mit dem das APK geöffnet wird) zu setzen:

**Android 8–9:**
> Einstellungen → Apps & Benachrichtigungen → Spezieller App-Zugriff → Unbekannte Apps installieren → Dateimanager → *Aus dieser Quelle zulassen*

**Android 10+:**
> Einstellungen → Apps → Spezieller App-Zugriff → Unbekannte Apps installieren → Dateimanager → *Aus dieser Quelle zulassen*

### Schritt 2 – APK auf das Gerät übertragen

Wähle eine der folgenden Methoden:

| Methode | Befehl / Vorgehen |
|---|---|
| USB (ADB) | `adb install haven-v1.0.1.apk` |
| USB (Dateiübertragung) | APK in den internen Speicher kopieren |
| Download | APK-Link im Browser des Geräts öffnen |

### Schritt 3 – APK installieren

- **Via ADB:** Installation läuft automatisch nach `adb install`.
- **Via Dateimanager:** APK antippen → *Installieren* → Warnung bei unbekannter Quelle bestätigen.

### Schritt 4 – App starten

Haven 2.0 erscheint im App-Drawer. Beim ersten Start werden Kamera-, Mikrofon- und Benachrichtigungs-Berechtigungen abgefragt.

> **Hinweis:** Um die App später wieder zu deinstallieren: Einstellungen → Apps → Haven → Deinstallieren.

---

## Cloud-Server (Docker auf Linux)

### Voraussetzungen

- Docker Engine 24+ und Docker Compose v2 (`docker compose version`)
- Freie Ports: **8000** (API) und **8080** (Web UI)
- Mindestens 1 GB freier RAM

### Schritt 1 – Docker-Images laden

```bash
docker load < haven-cloud-v1.0.1.tar.gz
```

Danach sind folgende Images lokal verfügbar:

```
haven-api:1.0.1
haven-webui:1.0.1
```

### Schritt 2 – Konfigurationsverzeichnis anlegen

```bash
mkdir haven-server && cd haven-server
```

`docker-compose.yml` anlegen:

```yaml
services:
  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_USER: haven
      POSTGRES_PASSWORD: haven
      POSTGRES_DB: haven
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U haven -d haven"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  app:
    image: haven-api:1.0.1
    command: uvicorn app.main:app --host 0.0.0.0 --port 8000
    ports:
      - "8000:8000"
    volumes:
      - ./media:/app/media
    env_file: .env
    depends_on:
      db:
        condition: service_healthy
      redis:
        condition: service_healthy

  worker:
    image: haven-api:1.0.1
    command: celery -A app.celery_app worker --loglevel=info --concurrency=2
    volumes:
      - ./media:/app/media
    env_file: .env
    depends_on:
      db:
        condition: service_healthy
      redis:
        condition: service_healthy

  webui:
    image: haven-webui:1.0.1
    command: >
      sh -c "python manage.py migrate --noinput &&
             python manage.py runserver 0.0.0.0:8080"
    ports:
      - "8080:8080"
    volumes:
      - ./media:/app/media
    env_file: .env
    depends_on:
      db:
        condition: service_healthy

volumes:
  postgres_data:
```

### Schritt 3 – Umgebungsvariablen setzen

`.env` Datei anlegen:

```ini
# Datenbank (Werte müssen mit dem db-Service oben übereinstimmen)
DATABASE_URL=postgresql+asyncpg://haven:haven@db:5432/haven
REDIS_URL=redis://redis:6379/0

# Geheimer Schlüssel – mind. 64 zufällige Zeichen, MUSS auf API und Web UI identisch sein
# Generieren: python3 -c "import secrets; print(secrets.token_hex(32))"
SECRET_KEY=hier-einen-langen-zufaelligen-schluessel-eintragen

# Django-spezifisch
DJANGO_SECRET_KEY=${SECRET_KEY}
ALLOWED_HOSTS=localhost,127.0.0.1

# Optionale Benachrichtigungskanäle (leer lassen zum Deaktivieren)
SMTP_HOST=
SMTP_PORT=587
SMTP_USER=
SMTP_PASSWORD=
SMTP_FROM=haven@example.com

SIGNAL_API_URL=
SIGNAL_SENDER=
SIGNAL_AUTH_TOKEN=
```

### Schritt 4 – Stack starten

```bash
docker compose up -d
```

### Schritt 5 – Datenbank initialisieren

```bash
docker compose exec app alembic upgrade head
```

### Schritt 6 – Ersten Admin-Nutzer anlegen

```bash
docker compose exec app python -c "
import asyncio, secrets
from passlib.context import CryptContext
from app.database import AsyncSessionLocal
from app.models.user import User

pwd = CryptContext(schemes=['bcrypt'], deprecated='auto')

async def create_admin():
    async with AsyncSessionLocal() as db:
        user = User(
            username='admin',
            password_hash=pwd.hash('changeme'),
            user_key='haven_u_' + secrets.token_hex(16),
            is_admin=True,
        )
        db.add(user)
        await db.commit()
        print('Admin angelegt. User-Key:', user.user_key)

asyncio.run(create_admin())
"
```

> Passwort danach sofort unter **Web UI → Profil → Passwort ändern** ändern.

### Schritt 7 – Dienste prüfen

| URL | Beschreibung |
|---|---|
| `http://localhost:8000/health` | API Health-Check |
| `http://localhost:8000/docs` | API-Dokumentation (Swagger) |
| `http://localhost:8080/` | Web UI |

### Android-App verbinden

In der Haven 2.0 App unter **Einstellungen → Cloud-Server**:

```
API-URL: http://<server-ip>:8000
```

---

## Updates

Beim Update auf eine neue Version:

```bash
# Neue Images laden
docker load < haven-cloud-v1.0.2.tar.gz

# Image-Tags in docker-compose.yml aktualisieren, dann:
docker compose up -d

# Datenbankmigrationen ausführen
docker compose exec app alembic upgrade head
```
