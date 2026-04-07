# Phase 5: Cloud-Server — Context

**Gathered:** 2026-04-07
**Status:** Ready for planning
**Source:** User specification (plan-phase invocation)

<domain>
## Phase Boundary

Self-hosted Python backend that acts as the cloud companion to the Haven Android app.
The server stores events and videos uploaded by Haven devices, supports multiple users and multiple Haven devices per user, applies optional AI analysis to uploaded data, and sends notifications after analysis.

**This phase is a standalone Python project** — a new `server/` directory (or separate repo) alongside the existing Android app. It does NOT modify the Android app code; Android integration (upload client in Kotlin) is a follow-up phase.

**Out of scope for this phase:**
- Android-side upload client code (Kotlin)
- WebRTC live stream
- QR-Code device pairing
- Real-time push to Android

</domain>

<decisions>
## Implementation Decisions

### Tech Stack (locked)
- Language: Python
- Web Framework: FastAPI
- Data Validation: Pydantic v2
- ORM: SQLAlchemy (async)
- Migrations: Alembic
- Database: PostgreSQL
- Cache / Message Broker: Redis
- Task Queue: Celery
- ASGI Server: Uvicorn (dev), Gunicorn + Uvicorn workers (prod)
- Testing: Pytest + pytest-asyncio + httpx (async test client)

### Authentication (locked)
- Multi-user: each user has a unique User-Key (API key) for Haven-app authentication
- Login/Registration: username + password (bcrypt hashing)
- 2FA: TOTP (RFC 6238) — e.g. pyotp; QR code enrollment via `/auth/2fa/setup`
- JWT access + refresh tokens for browser/API session (python-jose or authlib)
- User-Key is a separate long-lived API key for device-to-server upload (not the JWT)

### Multi-Device Support (locked)
- Each user can register multiple Haven apps (App-Key + human-readable name)
- App-Key is a unique token scoped to one device; revocable per-device
- Events/videos are tagged with the originating App-Key

### User Data Encryption (locked)
- Sensitive user content is encrypted at rest
- Key derivation: Argon2id from (cloud_password + username) — `argon2-cffi`
- Encrypted fields: stored media file content (AES-GCM); metadata stored plaintext for indexing
- Server never stores the plaintext cloud password; derives encryption key on upload/download with user-provided password in request header or derived from session

### Quota Management (locked)
- Admin can set per-user storage quota (in MB or GB) and max event count
- Quota enforced on upload — return 429 / 413 when exceeded
- Admin panel: FastAPI route group `/admin/` protected by admin role
- Admin role flag on User model

### Event + Video Upload (locked)
- Endpoint: `POST /devices/{app_key}/events` — upload event metadata + optional video attachment
- Video upload: multipart/form-data or chunked upload; stored in server filesystem or object storage (configurable)
- Events stored in PostgreSQL; video file path stored in DB
- Upload authenticated via App-Key header

### Optional AI Analysis (locked)
- AI analysis runs asynchronously via Celery task after upload
- Two backends (configurable per-server):
  1. Local: TFLite model (EfficientDet Lite 0) — same model as Android app, run via `tflite-runtime`
  2. Remote: OpenRouter API (configurable model, e.g. `google/gemini-flash-1.5`)
- Analysis result stored in DB (labels, confidence, description)
- Feature flag: `AI_BACKEND=none|tflite|openrouter` in config

### Cloud Notifications (locked)
- After AI analysis completes (or immediately on upload if AI disabled), Celery task sends notification
- Channels (same as Phase 4 Android channels):
  1. Email: SMTP via `python-multipart` + `aiosmtplib` or `fastapi-mail`
  2. Signal: signal-cli REST API (`POST /v2/send`)
  3. Pushover: Pushover API (`POST https://api.pushover.net/1/messages.json`)
- User configures enabled channels + credentials in settings
- Notification includes: event type, severity, timestamp, device name, AI analysis summary (if available)

### Claude's Discretion
- Project layout: `server/` at repo root with `pyproject.toml` + `docker-compose.yml`
- Docker: multi-stage Dockerfile for production, `docker-compose.yml` for local dev (app + postgres + redis + celery)
- `.env` file for secrets (DATABASE_URL, REDIS_URL, SECRET_KEY, etc.)
- API versioning: `/api/v1/` prefix
- Health check endpoint: `GET /health`
- OpenAPI docs at `/docs` (Swagger) and `/redoc`
- Pagination on list endpoints (cursor or offset)
- File storage: local filesystem in `MEDIA_ROOT` (configurable), future S3-compatible object storage

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project Context
- `CLAUDE.md` — Project goals, architecture principles, constraints
- `.planning/REQUIREMENTS.md` — CLOUD-01 through CLOUD-08 definitions
- `.planning/ROADMAP.md` — Phase 5 goal and success criteria

### Phase 4 (existing notification channels for reference)
- `app/src/main/java/org/havenapp/main/notify/` — HavenAlertChannel interface, SignalRestChannel, MattermostChannel, PushoverChannel — server-side notification logic should mirror these channel concepts

</canonical_refs>

<specifics>
## Specific Ideas

- Use `argon2-cffi` for password hashing and key derivation (not bcrypt for key derivation — bcrypt output is too short for AES-256 keys)
- Celery worker for AI analysis should share the same TFLite model instance (lazy-loaded singleton) to avoid reload overhead
- OpenRouter integration: `POST https://openrouter.ai/api/v1/chat/completions` with image attachment (base64) for video frame analysis
- Admin quota check: middleware or dependency injection in upload endpoint
- 2FA: `pyotp.TOTP(secret).verify(code, valid_window=1)` — allow ±30s drift
- App-Key format: `hav_<32 random hex chars>` for easy identification

</specifics>

<deferred>
## Deferred Ideas

- Android Kotlin upload client (separate phase)
- WebRTC live stream
- Multi-device real-time sync
- S3/object-storage backend (only filesystem in this phase)
- Web dashboard UI (only REST API in this phase)
- Rate limiting beyond quota

</deferred>

---

*Phase: 05-cloud-server*
*Context gathered: 2026-04-07 from user specification*
