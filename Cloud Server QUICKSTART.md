# Haven Cloud Server – Quickstart

Der Haven-Backend-Stack besteht aus drei Komponenten:

| Komponente | Technologie | Port | Zweck |
|-----------|-------------|------|-------|
| **API** | FastAPI + Uvicorn | 8000 | REST-API für die Android-App |
| **Worker** | Celery + Redis | — | Benachrichtigungen, Medienverarbeitung |
| **Web UI** | Django + HTMX | 8080 | Browser-Oberfläche für Admins/Nutzer |

---

## Voraussetzungen

- Docker und Docker Compose (v2)
- Python 3.12+ (nur für lokale Entwicklung ohne Docker)

---

## 1. Umgebungsvariablen anlegen

```bash
cp .env.example .env
```

Pflichtfelder anpassen:

```ini
# Datenbank – unverändert lassen wenn Docker Compose verwendet wird
DATABASE_URL=postgresql+asyncpg://haven:haven@db:5432/haven

# Redis – unverändert lassen wenn Docker Compose verwendet wird
REDIS_URL=redis://redis:6379/0

# Geheimer Schlüssel – MUSS auf beiden Diensten identisch sein (API + Web UI)
# Mindestens 64 zufällige Zeichen, z. B.:
#   python -c "import secrets; print(secrets.token_hex(32))"
SECRET_KEY=change-me-to-random-64-chars

# Optionale Benachrichtigungskanäle
SMTP_HOST=
SMTP_PORT=587
SMTP_USER=
SMTP_PASSWORD=
SMTP_FROM=haven@example.com

SIGNAL_API_URL=          # z. B. http://signal-cli:8080
SIGNAL_SENDER=           # registrierte Signal-Nummer im E.164-Format
SIGNAL_AUTH_TOKEN=       # Bearer-Token des signal-cli REST API

# Pushover wird per Nutzer in der Web UI konfiguriert (App Token + User Key).
# PUSHOVER_APP_TOKEN hier ist ein optionaler globaler Fallback, wird aber nicht mehr empfohlen.
# PUSHOVER_APP_TOKEN=
```

> **Wichtig:** `SECRET_KEY` wird von der Web UI für TOTP-Schlüssel-Ableitung (Fernet) verwendet und muss mit dem Wert in der FastAPI-API übereinstimmen.

---

## 2. Stack starten

```bash
docker compose up --build -d
```

Beim ersten Start werden automatisch:
- PostgreSQL-Datenbank initialisiert
- Alle Services gestartet (db → redis → app + worker → webui)

### Datenbankmigrationen ausführen

```bash
docker compose exec app alembic upgrade head
```

### Ersten Admin-Nutzer anlegen

```bash
docker compose exec app python -c "
import asyncio, secrets
from passlib.context import CryptContext
from sqlalchemy.ext.asyncio import AsyncSession
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
        print('Admin angelegt:', user.username, '/ key:', user.user_key)

asyncio.run(create_admin())
"
```

> Alternativ: Über `POST /auth/register` in der API registrieren und dann in der Web UI unter Admin → Nutzer `is_admin` aktivieren.

---

## 3. Dienste prüfen

| URL | Beschreibung |
|-----|-------------|
| `http://localhost:8000/docs` | FastAPI Swagger UI (API-Dokumentation) |
| `http://localhost:8000/health` | Health-Check Endpunkt |
| `http://localhost:8080/` | Web UI (leitet zu `/events/` weiter) |
| `http://localhost:8080/accounts/login/` | Direkt zum Login |
| `http://localhost:8080/admin/` | Admin-Dashboard (nur für `is_admin=True`) |

---

## 4. Web UI – Erste Schritte

1. `http://localhost:8080` aufrufen → Weiterleitung zum Login
2. Mit Admin-Zugangsdaten einloggen
3. Unter **Admin → Nutzer** weitere Nutzer anlegen und Quoten setzen
4. Unter **Einstellungen → Benachrichtigungen** Kanäle konfigurieren (E-Mail, Signal, Pushover)
   - Pushover: **App Token** (neue App unter `pushover.net/apps` anlegen) + **User Key** (persönlicher Key von `pushover.net`) — beide Felder sind Pflicht

### Optionale TOTP 2FA aktivieren

Nach dem Login: **Profil → 2FA einrichten** → QR-Code mit Authenticator-App scannen.

---

## 5. Android-App verbinden

In der Haven 2.0 Android-App (Einstellungen → Cloud-Server):

```
API-URL: http://<server-ip>:8000
```

Der Android-Client authentifiziert sich über `POST /auth/login` und speichert JWT-Token lokal.

---

## Lokale Entwicklung (ohne Docker)

```bash
# Virtuelle Umgebung anlegen
python -m venv .venv
source .venv/bin/activate

# Alle Abhängigkeiten (API + Web UI + Dev-Tools)
pip install -e ".[web,dev]"

# PostgreSQL und Redis lokal starten (oder via Docker)
docker compose up db redis -d

# API starten
DATABASE_URL=postgresql+asyncpg://haven:haven@localhost:5432/haven \
REDIS_URL=redis://localhost:6379/0 \
SECRET_KEY=dev-secret-key \
uvicorn app.main:app --reload --port 8000

# Web UI starten (separates Terminal)
cd webui
SECRET_KEY=dev-secret-key \
DATABASE_URL=postgresql://haven:haven@localhost:5432/haven \
python manage.py runserver 8080
```

### Tests ausführen

```bash
# FastAPI-Tests
pytest tests/ -v

# Web UI-Tests
cd webui
pytest tests/ -v
```

---

## Produktivbetrieb

Für den Produktivbetrieb `docker-compose.yml` anpassen:

```yaml
# API: Reload deaktivieren, Gunicorn verwenden
app:
  command: gunicorn app.main:app -w 4 -k uvicorn.workers.UvicornWorker --bind 0.0.0.0:8000

# Web UI: läuft bereits mit Gunicorn (2 Worker, siehe Dockerfile.webui)
```

Zusätzlich empfohlen:
- Nginx als Reverse Proxy vor Port 8000 und 8080 (SSL-Terminierung)
- `DEBUG=false` und `ALLOWED_HOSTS=yourdomain.com` in `.env`
- `SECRET_KEY` mit mindestens 64 zufälligen Zeichen
