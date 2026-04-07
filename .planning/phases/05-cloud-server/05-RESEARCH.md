# Phase 5: Cloud-Server — Research

**Researched:** 2026-04-07
**Domain:** Python / FastAPI / PostgreSQL / Redis / Celery / Argon2 / TOTP / TFLite
**Confidence:** HIGH (all stack choices verified against current PyPI and official docs)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Tech Stack (locked)**
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

**Authentication (locked)**
- Multi-user: each user has a unique User-Key (API key) for Haven-app authentication
- Login/Registration: username + password (bcrypt hashing)
- 2FA: TOTP (RFC 6238) — pyotp; QR code enrollment via `/auth/2fa/setup`
- JWT access + refresh tokens for browser/API session (python-jose or authlib)
- User-Key is a separate long-lived API key for device-to-server upload (not the JWT)

**Multi-Device Support (locked)**
- Each user can register multiple Haven apps (App-Key + human-readable name)
- App-Key is a unique token scoped to one device; revocable per-device
- Events/videos are tagged with the originating App-Key
- App-Key format: `hav_<32 random hex chars>`

**User Data Encryption (locked)**
- Sensitive user content is encrypted at rest
- Key derivation: Argon2id from (cloud_password + username) — `argon2-cffi`
- Encrypted fields: stored media file content (AES-GCM); metadata stored plaintext for indexing
- Server never stores the plaintext cloud password

**Quota Management (locked)**
- Admin can set per-user storage quota (in MB/GB) and max event count
- Quota enforced on upload — return 429/413 when exceeded
- Admin panel: FastAPI route group `/admin/` protected by admin role

**Event + Video Upload (locked)**
- Endpoint: `POST /devices/{app_key}/events`
- Video upload: multipart/form-data; stored in server filesystem
- Events stored in PostgreSQL; video file path stored in DB
- Upload authenticated via App-Key header

**Optional AI Analysis (locked)**
- Runs asynchronously via Celery task after upload
- Backend 1: Local TFLite model (EfficientDet Lite 0) via `tflite-runtime` / `ai-edge-litert`
- Backend 2: Remote OpenRouter API (`google/gemini-flash-1.5`)
- Feature flag: `AI_BACKEND=none|tflite|openrouter` in config

**Cloud Notifications (locked)**
- After AI analysis (or immediately if AI disabled), Celery task sends notification
- Channels: Email (SMTP via aiosmtplib), Signal (signal-cli REST API), Pushover
- User configures enabled channels + credentials in settings

### Claude's Discretion
- Project layout: `server/` at repo root with `pyproject.toml` + `docker-compose.yml`
- Docker: multi-stage Dockerfile for production, `docker-compose.yml` for local dev
- `.env` file for secrets (DATABASE_URL, REDIS_URL, SECRET_KEY, etc.)
- API versioning: `/api/v1/` prefix
- Health check endpoint: `GET /health`
- OpenAPI docs at `/docs` (Swagger) and `/redoc`
- Pagination on list endpoints (cursor or offset)
- File storage: local filesystem in `MEDIA_ROOT` (configurable)

### Deferred Ideas (OUT OF SCOPE)
- Android Kotlin upload client (separate phase)
- WebRTC live stream
- Multi-device real-time sync
- S3/object-storage backend (only filesystem in this phase)
- Web dashboard UI (only REST API in this phase)
- Rate limiting beyond quota
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| CLOUD-01 | Multi-User-Backend mit User-Key-Authentifizierung | JWT + App-Key dual-auth pattern; bcrypt for password hashing |
| CLOUD-02 | Registrierung, Anmeldung und 2FA (TOTP) | pyotp TOTP flow; QR code via `qrcode` library |
| CLOUD-03 | Multi-Haven-App pro User — App-Key mit frei wählbarem Namen | App-Key as `hav_<32hex>` token in dedicated table; per-device revocation |
| CLOUD-04 | Verschlüsselung der Nutzerdaten (Argon2 key derivation) | argon2-cffi `hash_secret_raw` for 256-bit AES-GCM key; `cryptography` for AES-GCM |
| CLOUD-05 | Admin-definierte Quota pro User | Quota model + dependency-injection check in upload endpoint |
| CLOUD-06 | Übermittlung von Events und Videos (verschlüsselt, App-Key authentifiziert) | UploadFile + aiofiles; chunk-validated size; AES-GCM encrypt before persist |
| CLOUD-07 | Optionale KI-Auswertung (TFLite oder OpenRouter) | ai-edge-litert 2.1.3 (Python 3.12 support confirmed); OpenRouter `/api/v1/chat/completions` with base64 image |
| CLOUD-08 | Cloud-Event-Benachrichtigungen nach Analyse | aiosmtplib (SMTP); signal-cli REST POST; Pushover POST; all as Celery tasks |
</phase_requirements>

---

## Summary

Phase 5 builds a standalone self-hosted Python server in `server/` at the repo root. The stack is a well-established production pattern: FastAPI handles HTTP, SQLAlchemy 2.0 async with asyncpg drives PostgreSQL, Celery workers process AI analysis and notifications asynchronously with Redis as broker/backend. All libraries are current and actively maintained; package versions verified against PyPI on 2026-04-07.

The two highest-complexity areas are (1) user data encryption — deriving a 256-bit AES-GCM key from Argon2id on every request where the user provides their cloud password, which means the encryption key must either be passed from the HTTP layer into the Celery task (via the task payload) or re-derived inside the worker; and (2) the TFLite/Python environment — `tflite-runtime` only supports up to Python 3.11, but its replacement `ai-edge-litert 2.1.3` (latest as of April 2026) ships wheels for Python 3.12 on Linux x86_64 and works with the same `.tflite` model files. Use `ai-edge-litert` instead of `tflite-runtime`.

The rest of the stack is straightforward modern FastAPI practice: domain-based routing with `APIRouter`, lifespan context manager for startup/shutdown, `AsyncSession` dependency injection via `Annotated[AsyncSession, Depends(get_db)]`, JWT via `python-jose[cryptography]`, TOTP via `pyotp`, and pytest with `httpx.AsyncClient` + dependency overrides for async testing.

**Primary recommendation:** Build `server/` as a domain-organized FastAPI app. Use `ai-edge-litert` (not `tflite-runtime`) for TFLite inference. Pass the Argon2-derived encryption key as a parameter inside Celery task payloads (not re-derived in worker — worker has no access to the request password unless explicitly passed).

---

## Standard Stack

### Core
| Library | Version (verified) | Purpose | Why Standard |
|---------|-------------------|---------|--------------|
| fastapi | 0.135.3 | Web framework | De facto async Python API framework |
| pydantic | 2.12.5 | Data validation / serialization | FastAPI native; v2 required |
| sqlalchemy | 2.0.49 | ORM (async) | SQLAlchemy 2.0 async is the current API |
| alembic | 1.18.4 | DB migrations | Standard SQLAlchemy migration tool |
| asyncpg | 0.31.0 | PostgreSQL async driver | Required by SQLAlchemy async for PostgreSQL |
| celery | 5.6.3 | Task queue | Standard async task queue for Python |
| redis (py) | via celery[redis] | Celery broker + backend | Redis is the standard Celery broker |
| uvicorn | 0.44.0 | ASGI server (dev) | Official FastAPI recommendation |
| gunicorn | 25.3.0 | Process manager (prod) | Manages Uvicorn worker processes |
| python-jose[cryptography] | 3.5.0 | JWT encoding/decoding | Standard FastAPI JWT choice |
| passlib[bcrypt] | 1.7.4 + bcrypt 5.0.0 | Password hashing | Standard FastAPI auth pattern |
| argon2-cffi | 25.1.0 | Argon2id key derivation | Best KDF for encryption keys (not bcrypt) |
| cryptography | latest | AES-GCM encryption | pyca/cryptography; Fernet/AESGCM primitives |
| pyotp | 2.9.0 | TOTP 2FA | RFC 6238; `valid_window=1` for ±30s drift |
| qrcode[pil] | 8.2 | QR code for 2FA setup | Generates PNG for authenticator app enrollment |
| pillow | 12.2.0 | Image processing (TFLite + QR) | Required by qrcode + image preprocessing |
| aiosmtplib | 5.1.0 | Async SMTP email | Non-blocking email; Python 3.10+ |
| aiofiles | 25.1.0 | Async file I/O | Non-blocking media read/write |
| httpx | 0.28.1 | HTTP client (OpenRouter + Signal) | Async HTTP; also used in tests |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| ai-edge-litert | 2.1.3 | TFLite inference (Python 3.12) | When `AI_BACKEND=tflite`; replaces `tflite-runtime` |
| pytest | latest | Test runner | All tests |
| pytest-asyncio | 1.3.0 | Async test support | Required for `async def` tests |
| python-multipart | latest | Multipart file upload parsing | Required by FastAPI `UploadFile` |
| pydantic-settings | latest | Settings from .env | Replaces manual dotenv loading |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| python-jose | authlib | authlib is more actively maintained but heavier; python-jose sufficient for HS256/RS256 JWT |
| passlib[bcrypt] | raw bcrypt | passlib provides a stable API wrapper; use passlib for password verification |
| aiosmtplib | fastapi-mail | fastapi-mail wraps aiosmtplib; use aiosmtplib directly for simpler dependency tree |
| ai-edge-litert | tensorflow (full) | Full TF is 400MB+; ai-edge-litert is inference-only, same .tflite model format |
| tflite-runtime | ai-edge-litert | tflite-runtime has no Python 3.12 wheels; ai-edge-litert 2.1.3 does — always prefer ai-edge-litert |

**Installation (minimal core):**
```bash
pip install fastapi[standard] "sqlalchemy[asyncio]" alembic asyncpg celery[redis] \
    uvicorn[standard] gunicorn "python-jose[cryptography]" "passlib[bcrypt]" bcrypt \
    argon2-cffi cryptography pyotp "qrcode[pil]" pillow aiosmtplib aiofiles httpx \
    pydantic-settings python-multipart pytest pytest-asyncio
# For TFLite backend:
pip install ai-edge-litert
```

**Version verification:** All versions confirmed against PyPI on 2026-04-07.

---

## Architecture Patterns

### Recommended Project Structure
```
server/
├── pyproject.toml              # project metadata + dependencies
├── .env                        # secrets (gitignored)
├── .env.example                # committed template
├── docker-compose.yml          # app + postgres + redis + celery
├── Dockerfile                  # multi-stage: builder + runtime
├── alembic.ini
├── alembic/
│   ├── env.py                  # async Alembic env
│   └── versions/               # migration scripts
└── app/
    ├── main.py                 # FastAPI app factory + lifespan
    ├── config.py               # pydantic-settings Settings
    ├── database.py             # AsyncEngine, AsyncSession, get_db dependency
    ├── celery_app.py           # Celery instance configuration
    ├── models/                 # SQLAlchemy ORM models (one file per domain)
    │   ├── user.py
    │   ├── device.py
    │   └── event.py
    ├── schemas/                # Pydantic v2 request/response models
    │   ├── auth.py
    │   ├── device.py
    │   └── event.py
    ├── routers/                # APIRouter modules (one per domain)
    │   ├── auth.py             # /api/v1/auth/*
    │   ├── devices.py          # /api/v1/devices/*
    │   ├── events.py           # /api/v1/events/*
    │   ├── admin.py            # /api/v1/admin/*
    │   └── health.py           # /health
    ├── dependencies/           # Shared FastAPI Depends functions
    │   ├── auth.py             # get_current_user, require_admin, verify_app_key
    │   └── quota.py            # check_upload_quota
    ├── services/               # Business logic (no HTTP/DB awareness)
    │   ├── crypto.py           # argon2 key derivation + AES-GCM encrypt/decrypt
    │   ├── totp.py             # pyotp enrollment + verification
    │   └── storage.py          # file save/load to MEDIA_ROOT
    ├── tasks/                  # Celery task definitions
    │   ├── analysis.py         # analyze_event_task
    │   └── notifications.py    # send_notification_task
    └── ml/
        └── detector.py         # TFLite / OpenRouter inference (lazy singleton)
```

### Pattern 1: FastAPI App Factory with Lifespan
**What:** Create app via factory function; use `@asynccontextmanager` lifespan for startup/shutdown.
**When to use:** Always — enables proper resource init (DB pool, TFLite model load) and clean testing.

```python
# app/main.py
from contextlib import asynccontextmanager
from fastapi import FastAPI
from app.routers import auth, devices, events, admin, health
from app.ml.detector import get_detector

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup: pre-load TFLite model if AI_BACKEND=tflite
    get_detector()  # lazy singleton — first call loads model
    yield
    # Shutdown: cleanup if needed

def create_app() -> FastAPI:
    app = FastAPI(
        title="Haven Cloud",
        version="1.0.0",
        lifespan=lifespan,
    )
    app.include_router(health.router)
    app.include_router(auth.router, prefix="/api/v1/auth", tags=["auth"])
    app.include_router(devices.router, prefix="/api/v1/devices", tags=["devices"])
    app.include_router(events.router, prefix="/api/v1/events", tags=["events"])
    app.include_router(admin.router, prefix="/api/v1/admin", tags=["admin"])
    return app

app = create_app()
```

### Pattern 2: SQLAlchemy Async Session Dependency
**What:** `get_db` generator yields `AsyncSession`; FastAPI handles commit/rollback lifecycle.
**When to use:** Every endpoint needing DB access.

```python
# app/database.py
from sqlalchemy.ext.asyncio import AsyncSession, create_async_engine, async_sessionmaker
from app.config import settings

engine = create_async_engine(settings.DATABASE_URL, pool_pre_ping=True)
AsyncSessionLocal = async_sessionmaker(engine, expire_on_commit=False)

async def get_db():
    async with AsyncSessionLocal() as session:
        yield session
        # FastAPI auto-commits on success, rolls back on exception

# Usage in endpoint:
from typing import Annotated
from fastapi import Depends
DbSession = Annotated[AsyncSession, Depends(get_db)]

@router.get("/")
async def list_events(db: DbSession):
    result = await db.execute(select(Event))
    ...
```

**DATABASE_URL format:** `postgresql+asyncpg://user:pass@host/dbname`

### Pattern 3: Dual Authentication (JWT + App-Key)
**What:** Two separate auth paths: JWT Bearer for human users (web/API sessions), App-Key header for Haven devices.
**When to use:** Distinguish user-facing endpoints from device upload endpoints.

```python
# app/dependencies/auth.py

oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/api/v1/auth/login")

async def get_current_user(token: str = Depends(oauth2_scheme), db: DbSession = ...) -> User:
    # decode JWT with python-jose, load user from DB
    ...

async def verify_app_key(
    x_app_key: str = Header(..., alias="X-App-Key"),
    db: DbSession = ...,
) -> Device:
    # Look up hav_<32hex> in devices table, check is_active
    ...

async def require_admin(user: User = Depends(get_current_user)) -> User:
    if not user.is_admin:
        raise HTTPException(status_code=403)
    return user
```

### Pattern 4: Argon2id Key Derivation + AES-GCM Encryption
**What:** Derive a 256-bit key from `(password + username)` using Argon2id; use it for AES-GCM file encryption.
**When to use:** Upload endpoint (encrypt before persist) and download endpoint (decrypt before return).

```python
# app/services/crypto.py
from argon2.low_level import hash_secret_raw, Type
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
import os

ARGON2_TIME_COST = 2
ARGON2_MEMORY_COST = 65536  # 64 MB
ARGON2_PARALLELISM = 2
ARGON2_HASH_LEN = 32         # 256-bit key

def derive_key(password: str, username: str) -> bytes:
    """Derive 256-bit AES key from password+username via Argon2id."""
    # Use username as salt (deterministic; no salt storage needed)
    salt = username.lower().encode("utf-8").ljust(16, b"\x00")[:16]
    return hash_secret_raw(
        secret=password.encode("utf-8"),
        salt=salt,
        time_cost=ARGON2_TIME_COST,
        memory_cost=ARGON2_MEMORY_COST,
        parallelism=ARGON2_PARALLELISM,
        hash_len=ARGON2_HASH_LEN,
        type=Type.ID,
    )

def encrypt_file(data: bytes, key: bytes) -> bytes:
    """Return nonce(12) + ciphertext."""
    nonce = os.urandom(12)
    ct = AESGCM(key).encrypt(nonce, data, None)
    return nonce + ct

def decrypt_file(data: bytes, key: bytes) -> bytes:
    nonce, ct = data[:12], data[12:]
    return AESGCM(key).decrypt(nonce, ct, None)
```

**Key lifecycle note:** The encryption key is derived per-request in the upload endpoint, used to encrypt the file bytes, then discarded. For Celery tasks that need to re-access encrypted content (e.g., decrypting a video frame for AI analysis), the derived key bytes must be passed as a Celery task argument in the `analyze_event_task.delay(event_id, encryption_key_hex)` call. The worker never derives the key independently — it cannot, because it has no access to the user's password.

### Pattern 5: Celery Task for AI Analysis + Notification
**What:** FastAPI enqueues task after upload; Celery worker picks up, runs inference, then sends notification.
**When to use:** All AI analysis and all external notifications.

```python
# app/celery_app.py
from celery import Celery
from app.config import settings

celery_app = Celery(
    "haven",
    broker=settings.REDIS_URL,
    backend=settings.REDIS_URL,
    include=["app.tasks.analysis", "app.tasks.notifications"],
)
celery_app.conf.task_serializer = "json"
celery_app.conf.accept_content = ["json"]

# app/tasks/analysis.py
from app.celery_app import celery_app

@celery_app.task(name="analyze_event")
def analyze_event_task(event_id: int, media_path: str, encryption_key_hex: str | None):
    from app.ml.detector import get_detector
    detector = get_detector()  # lazy singleton — loaded once per worker process
    ...
```

**Worker start command:** `celery -A app.celery_app worker --loglevel=info -c 2`

### Pattern 6: TFLite Inference (ai-edge-litert)
**What:** Lazy-load the model once per worker process; reuse interpreter across task calls.
**When to use:** `AI_BACKEND=tflite` only.

```python
# app/ml/detector.py
_detector = None

def get_detector():
    global _detector
    if _detector is None:
        _detector = HavenDetector()
    return _detector

class HavenDetector:
    def __init__(self):
        from ai_edge_litert.interpreter import Interpreter
        self._interpreter = Interpreter(model_path=str(settings.TFLITE_MODEL_PATH))
        self._interpreter.allocate_tensors()
        self._input_details = self._interpreter.get_input_details()
        self._output_details = self._interpreter.get_output_details()

    def detect(self, image_bytes: bytes) -> list[dict]:
        # Decode bytes → PIL → resize to model input shape → np.array
        # set_tensor → invoke → get_tensor
        ...
```

**Import:** `from ai_edge_litert.interpreter import Interpreter` (not `tflite_runtime.interpreter`).

### Pattern 7: OpenRouter Vision API
**What:** POST to `https://openrouter.ai/api/v1/chat/completions` with base64 image in message content.
**When to use:** `AI_BACKEND=openrouter` only.

```python
# app/ml/openrouter.py
import httpx, base64

async def analyze_frame_openrouter(jpeg_bytes: bytes, model: str, api_key: str) -> str:
    b64 = base64.b64encode(jpeg_bytes).decode()
    payload = {
        "model": model,  # e.g. "google/gemini-flash-1.5"
        "messages": [
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": "Describe what you see. Focus on persons, objects, unusual activity."},
                    {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{b64}"}},
                ],
            }
        ],
    }
    async with httpx.AsyncClient() as client:
        resp = await client.post(
            "https://openrouter.ai/api/v1/chat/completions",
            headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
            json=payload,
            timeout=30.0,
        )
        resp.raise_for_status()
    return resp.json()["choices"][0]["message"]["content"]
```

**Note:** OpenRouter calls are made from within a Celery task (not inside a FastAPI request). Since Celery workers run in a synchronous context by default, use `asyncio.run()` around the async call, or use the synchronous `httpx.Client` instead.

### Pattern 8: File Upload with Quota Check
**What:** `UploadFile` + quota dependency + aiofiles + streaming read; raise 413 when size exceeded.
**When to use:** `POST /devices/{app_key}/events`.

```python
@router.post("/{app_key}/events")
async def upload_event(
    app_key: str,
    metadata: EventCreateSchema,
    video: UploadFile | None = File(None),
    device: Device = Depends(verify_app_key),
    _quota: None = Depends(check_upload_quota),  # raises 429/413 if exceeded
    db: DbSession = ...,
):
    # read + size-check video in chunks
    if video:
        chunks = []
        total = 0
        async for chunk in video:
            total += len(chunk)
            if total > settings.MAX_VIDEO_SIZE_BYTES:
                raise HTTPException(413, "Video too large")
            chunks.append(chunk)
        raw_bytes = b"".join(chunks)
        ...
```

### Anti-Patterns to Avoid
- **Synchronous SQLAlchemy in async context:** Never use `Session` (sync) in an async endpoint. Always use `AsyncSession` + `await`.
- **Storing plaintext cloud password:** Never. Not in DB, not in logs, not in Celery task args. Only derived key bytes.
- **Re-deriving encryption key in Celery worker:** Worker cannot re-derive — it doesn't know the user's password. Pass `encryption_key_hex` explicitly as a task argument.
- **Loading TFLite model per task invocation:** Model load is expensive (~100ms+). Use a module-level lazy singleton.
- **`on_event` deprecated startup handlers:** Use lifespan context manager instead; `@app.on_event` is deprecated in FastAPI.
- **`tflite_runtime.interpreter`:** This import fails on Python 3.12; use `ai_edge_litert.interpreter` instead.
- **Blocking `httpx` calls inside async endpoints:** Use `async with httpx.AsyncClient()`. For Celery tasks (sync context), use `httpx.Client` or `asyncio.run()`.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Password hashing | Custom bcrypt wrapper | `passlib[bcrypt]` | Timing-safe comparison, hash upgrade path |
| JWT encode/decode | Manual HMAC | `python-jose[cryptography]` | Handles exp, nbf, aud, signature; battle-tested |
| TOTP verification | Manual RFC 6238 | `pyotp.TOTP.verify(token, valid_window=1)` | Valid window, counter sync, base32 handling |
| AES-GCM encryption | `pycrypto` / manual | `cryptography` (pyca) | Only maintained crypto lib; `AESGCM` is high-level |
| Argon2 KDF | PBKDF2 | `argon2-cffi` `hash_secret_raw` | Argon2id is memory-hard; bcrypt output too short for 256-bit keys |
| DB migrations | Manual SQL scripts | Alembic | Auto-generates migration from model diffs |
| Async SMTP | smtplib in thread pool | `aiosmtplib` | Non-blocking; Python 3.10+ native async |
| File chunked reads | Manual seek/read | `UploadFile` + async for loop | FastAPI's UploadFile handles multipart boundaries |
| QR code for 2FA | Manual PNG drawing | `qrcode[pil]` | One-liner from provisioning URI to PNG bytes |
| Celery serialization | Custom pickle | JSON (task_serializer="json") | Pickle has RCE risk with untrusted brokers |

**Key insight:** The crypto stack (argon2-cffi + cryptography) has no good hand-rolled substitutes. Security is in the implementation details, not the algorithm name.

---

## Common Pitfalls

### Pitfall 1: tflite-runtime Has No Python 3.12 Wheels
**What goes wrong:** `pip install tflite-runtime` on Python 3.12 fails with "No matching distribution found".
**Why it happens:** Google stopped publishing tflite-runtime wheels beyond Python 3.11. The replacement is `ai-edge-litert`.
**How to avoid:** Always use `ai-edge-litert` in pyproject.toml. Change import from `tflite_runtime.interpreter` to `ai_edge_litert.interpreter`.
**Warning signs:** `pip install tflite-runtime` completes but `import tflite_runtime` fails at runtime, or install error with Python 3.12.

### Pitfall 2: Celery Cannot Access FastAPI AsyncSession
**What goes wrong:** Task tries to `await db.execute(...)` inside a Celery task function, which runs in a synchronous worker.
**Why it happens:** Celery worker processes are not an ASGI event loop; async SQLAlchemy won't work directly.
**How to avoid:** Inside Celery tasks, use `asyncio.run(some_async_fn())` to run async DB code, or use a separate synchronous SQLAlchemy engine for the worker. The cleaner pattern is to pass all needed data as task arguments (event JSON, media path, encryption key) and avoid DB access in the worker where possible. For result writes, use `asyncio.run()`.
**Warning signs:** `RuntimeError: no current event loop` in Celery worker logs.

### Pitfall 3: Argon2 KDF Salt Must Be Deterministic for Key Derivation
**What goes wrong:** Using a random salt for Argon2 key derivation means the derived key differs each time, making stored ciphertext undecipherable on the next request.
**Why it happens:** Password hashing uses random salt by design (to prevent rainbow tables). Key derivation must be deterministic.
**How to avoid:** Use username (or user's UUID) as the salt for key derivation. This is a separate use case from password hashing. The locked decision already specifies `(cloud_password + username)` as inputs — store the salt in the User model so it can be retrieved for decryption.
**Warning signs:** Files that encrypt successfully fail to decrypt on subsequent requests.

### Pitfall 4: pytest-asyncio Event Loop Scope in Version 1.x
**What goes wrong:** Tests fail with `ScopeMismatch` or `RuntimeError: Event loop is closed` when mixing fixture scopes.
**Why it happens:** pytest-asyncio 1.x (major version bump from 0.x) changed the default `asyncio_mode` and event loop scope handling. The old `@pytest.fixture(scope="session")` async fixtures require `loop_scope="session"` in newer versions.
**How to avoid:** Add to `pyproject.toml`:
```toml
[tool.pytest.ini_options]
asyncio_mode = "auto"
```
Scope async fixtures explicitly: `@pytest_asyncio.fixture(loop_scope="session")` for session-scoped DB setups.
**Warning signs:** `DeprecationWarning: The event_loop fixture is deprecated` or test isolation failures.

### Pitfall 5: Gunicorn + Uvicorn Worker Count with Celery on Same Host
**What goes wrong:** Running 4 Gunicorn workers + 4 Celery workers on a small VPS causes OOM if each loads the TFLite model.
**Why it happens:** TFLite model (EfficientDet Lite 0) is ~4MB but the interpreter runtime adds ~100-200MB RAM per process.
**How to avoid:** Set Celery worker concurrency to 1 or 2 (`-c 2`) and `--prefork` pool. Keep Gunicorn workers at `2 * cpu_cores + 1`. For TFLite, use `--pool=prefork --concurrency=1` per Celery worker process.
**Warning signs:** Celery worker OOM kills or slow inference due to model reloading.

### Pitfall 6: Alembic async env.py Misconfiguration
**What goes wrong:** `alembic revision --autogenerate` produces empty migrations even when models have changed.
**Why it happens:** Alembic's default `env.py` uses synchronous engine; async SQLAlchemy requires an async-compatible Alembic env.
**How to avoid:** Configure `alembic/env.py` with `run_async_migrations()` using `AsyncEngine.run_sync(do_run_migrations)` pattern. Import all SQLAlchemy models before `Base.metadata` in `env.py` so autogenerate can detect them.
**Warning signs:** `alembic upgrade head` runs successfully but DB schema doesn't match models.

### Pitfall 7: OpenRouter Async Client in Celery Sync Context
**What goes wrong:** `await httpx.AsyncClient().post(...)` inside a `@celery_app.task` function raises `RuntimeError: coroutine was never awaited`.
**Why it happens:** Celery task functions are synchronous by default.
**How to avoid:** Use `httpx.Client` (sync) for OpenRouter calls within Celery tasks, or wrap in `asyncio.run()`. Do not use `async def` task functions without explicitly configuring Celery's async support.

---

## Code Examples

### TOTP 2FA Enrollment Flow
```python
# app/services/totp.py
import pyotp, qrcode, io

def generate_totp_secret() -> str:
    return pyotp.random_base32()

def get_totp_uri(secret: str, username: str, issuer: str = "Haven") -> str:
    return pyotp.TOTP(secret).provisioning_uri(name=username, issuer_name=issuer)

def get_totp_qr_png(uri: str) -> bytes:
    img = qrcode.make(uri)
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()

def verify_totp(secret: str, token: str) -> bool:
    return pyotp.TOTP(secret).verify(token, valid_window=1)  # ±30s

# Enrollment flow:
# 1. POST /auth/2fa/setup → generate secret, store in user row (totp_secret), return QR PNG
# 2. POST /auth/2fa/verify → verify token with secret, set user.totp_enabled=True
# 3. POST /auth/login → if totp_enabled, require X-TOTP-Token header after password check
```

### JWT Token Pair (Access + Refresh)
```python
# app/services/jwt.py
from jose import JWTError, jwt
from datetime import datetime, timedelta, timezone
from app.config import settings

def create_access_token(subject: str) -> str:
    expire = datetime.now(timezone.utc) + timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES)
    return jwt.encode({"sub": subject, "exp": expire, "type": "access"},
                      settings.SECRET_KEY, algorithm="HS256")

def create_refresh_token(subject: str) -> str:
    expire = datetime.now(timezone.utc) + timedelta(days=settings.REFRESH_TOKEN_EXPIRE_DAYS)
    return jwt.encode({"sub": subject, "exp": expire, "type": "refresh"},
                      settings.SECRET_KEY, algorithm="HS256")

def decode_token(token: str) -> dict:
    return jwt.decode(token, settings.SECRET_KEY, algorithms=["HS256"])
```

### pytest Async Test Pattern
```python
# tests/conftest.py
import pytest_asyncio
from httpx import AsyncClient, ASGITransport
from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession, async_sessionmaker
from app.main import create_app
from app.database import get_db

TEST_DATABASE_URL = "postgresql+asyncpg://test:test@localhost/haven_test"

@pytest_asyncio.fixture(loop_scope="session")
async def async_client():
    app = create_app()
    engine = create_async_engine(TEST_DATABASE_URL)
    TestSession = async_sessionmaker(engine, expire_on_commit=False)

    async def override_get_db():
        async with TestSession() as session:
            yield session

    app.dependency_overrides[get_db] = override_get_db
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        yield client
```

### docker-compose.yml (dev)
```yaml
services:
  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_USER: haven
      POSTGRES_PASSWORD: haven
      POSTGRES_DB: haven
    ports: ["5432:5432"]

  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]

  app:
    build: .
    command: uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
    volumes: [".:/app"]
    ports: ["8000:8000"]
    env_file: .env
    depends_on: [db, redis]

  worker:
    build: .
    command: celery -A app.celery_app worker --loglevel=info -c 2
    volumes: [".:/app"]
    env_file: .env
    depends_on: [db, redis]
```

### Alembic async env.py (key section)
```python
# alembic/env.py
from sqlalchemy.ext.asyncio import async_engine_from_config

def run_migrations_online() -> None:
    connectable = async_engine_from_config(config.get_section(config.config_ini_section))

    async def do_run_migrations(connection):
        await connection.run_sync(run_migrations)

    import asyncio
    asyncio.run(connectable.connect().__aenter__()  # simplified — see Alembic async docs)
    # Full pattern: use connectable.begin() + run_sync
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `tflite_runtime.interpreter` | `ai_edge_litert.interpreter` | 2024 (LiteRT rebrand) | Must update import; same .tflite model works |
| `@app.on_event("startup")` | `@asynccontextmanager lifespan` | FastAPI 0.93+ | on_event deprecated; lifespan is current standard |
| SQLAlchemy 1.4 async | SQLAlchemy 2.0 `AsyncSession` | 2023 | 2.0 API is stable and recommended; 1.4 async was experimental |
| pytest-asyncio `event_loop` fixture | `loop_scope` in fixture decorator | pytest-asyncio 0.23+ / 1.x | Old fixture deprecated; causes warnings in 1.x |
| `passlib.hash.bcrypt` | `passlib[bcrypt]` + `bcrypt` 4.x | bcrypt 4.0 broke passlib | Requires explicit `bcrypt` dependency alongside passlib |
| Pydantic v1 `.dict()` | Pydantic v2 `.model_dump()` | Pydantic 2.0 | `.dict()` still works but deprecated; use `.model_dump()` |

**Deprecated/outdated:**
- `tflite-runtime`: No Python 3.12 wheels. Replaced by `ai-edge-litert`.
- `@app.on_event`: Deprecated in FastAPI. Use `lifespan` context manager.
- `python-jose` `JOSE` class: The `JWTError` / `jwt.encode/decode` API remains stable; no replacement needed yet but authlib is gaining traction.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Python 3.12 | Server runtime | ✓ | 3.12.3 | — |
| pip3 | Package install | ✓ | 24.0 | — |
| Docker | Container builds | ✗ | — | Run services natively for dev; install Docker for production deploy |
| docker-compose | Dev stack orchestration | ✗ | — | Run postgres/redis locally or via snap/flatpak |
| PostgreSQL | Database | ✗ | — | Install `postgresql` via apt, or use Docker when available |
| Redis | Celery broker | ✗ | — | Install `redis-server` via apt, or use Docker when available |
| ai-edge-litert | TFLite inference | available on PyPI | 2.1.3 | Skip TFLite; use OpenRouter or AI_BACKEND=none |

**Missing dependencies with no fallback:**
- None — all missing tools can be installed or have alternatives.

**Missing dependencies with fallback:**
- Docker / docker-compose: Not installed on dev machine. Install via `apt install docker.io docker-compose` or use services natively for development. Production deployment assumes Docker.
- PostgreSQL: Not running locally. Install `postgresql` or use `docker run postgres:16-alpine`. Required for integration tests.
- Redis: Not running locally. Install `redis-server` or `docker run redis:7-alpine`. Required for Celery.

---

## Open Questions

1. **Encryption key in Celery task: pass as hex string or avoid?**
   - What we know: Worker cannot re-derive the key (no access to password); key must be passed as task argument.
   - What's unclear: Is it acceptable to put the 256-bit key in the Celery/Redis task queue (Redis is on the same host, not publicly exposed)?
   - Recommendation: Accept this for now — the threat model is a self-hosted server. Document it as a known trust boundary. Alternatively, skip video decryption in the worker and only analyze unencrypted event metadata.

2. **Argon2id KDF latency on upload path**
   - What we know: Argon2id with `time_cost=2, memory_cost=65536` takes ~200-500ms per derivation.
   - What's unclear: Is this acceptable latency for every video upload request?
   - Recommendation: Accept for initial implementation. If unacceptable, cache the derived key in the user's JWT session (store in Redis with session TTL) so it's derived only at login, not per-upload.

3. **TOTP secret storage encryption**
   - What we know: TOTP secret (base32, ~20 bytes) must be stored in DB; if DB is compromised, secrets are exposed.
   - What's unclear: Should TOTP secrets be encrypted at rest (with a server-side secret key, not user-derived)?
   - Recommendation: Encrypt TOTP secrets with a server-side `SECRET_KEY` using Fernet (from `cryptography`). Add to Wave 0 database design.

---

## Sources

### Primary (HIGH confidence)
- PyPI registry (pip3 index versions, 2026-04-07) — all version numbers verified directly
- [FastAPI official docs — lifespan](https://fastapi.tiangolo.com/reference/apirouter/) — lifespan context manager pattern
- [FastAPI official docs — file uploads](https://fastapi.tiangolo.com/tutorial/request-files/) — UploadFile pattern
- [FastAPI official docs — async tests](https://fastapi.tiangolo.com/advanced/async-tests/) — httpx AsyncClient
- [argon2-cffi docs](https://argon2-cffi.readthedocs.io/) — hash_secret_raw API
- [pyotp GitHub](https://github.com/pyauth/pyotp) — provisioning_uri, verify API
- [ai-edge-litert PyPI](https://pypi.org/project/ai-edge-litert/) — Python 3.12 support confirmed (2.1.3)
- [OpenRouter image inputs docs](https://openrouter.ai/docs/guides/overview/multimodal/images) — base64 image format

### Secondary (MEDIUM confidence)
- [FastAPI + SQLAlchemy 2.0 async patterns (Medium, Dec 2025)](https://dev-faizan.medium.com/fastapi-sqlalchemy-2-0-modern-async-database-patterns-7879d39b6843)
- [Testcontainers with FastAPI and asyncpg](https://lealre.github.io/fastapi-testcontainer-asyncpg/) — pytest async fixture pattern
- [TestDriven.io — FastAPI and Celery](https://testdriven.io/blog/fastapi-and-celery/) — Celery worker setup
- [OneUptime — FastAPI + PostgreSQL + Celery docker-compose (Feb 2026)](https://oneuptime.com/blog/post/2026-02-08-how-to-set-up-a-fastapi-postgresql-celery-stack-with-docker-compose/view)
- [LiteRT Python 3.12 issue tracker](https://github.com/google-ai-edge/LiteRT/issues/469) — confirmed 3.12 support in 2.x

### Tertiary (LOW confidence — flag for validation)
- TOTP secret at-rest encryption: inferred from general security practice; no official FastAPI guidance found.

---

## Project Constraints (from CLAUDE.md)

The following CLAUDE.md directives apply to the `server/` Python project:

| Directive | Applies How |
|-----------|------------|
| No Google Cloud Services | No Firebase, no GCP APIs; OpenRouter is opt-in with user's own key |
| Privacy-first / local-first | All data stays on the self-hosted server; no third-party analytics |
| No new Java | N/A (Python server); Android-side client is a separate phase |
| Tech Stack locked | See User Constraints section above |
| `server/` directory layout | pyproject.toml at `server/pyproject.toml`; docker-compose.yml at `server/docker-compose.yml` |
| GSD workflow enforcement | All implementation must go through `/gsd:execute-phase`; no direct repo edits outside GSD |

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all versions verified directly via pip3 index on 2026-04-07
- Architecture: HIGH — domain-based FastAPI structure is well-established, patterns from official docs
- Argon2 key derivation lifecycle: MEDIUM — core pattern is sound; edge case around Celery task key passing is a design decision, not a technical unknown
- TFLite / ai-edge-litert: HIGH — PyPI confirms Python 3.12 wheels in 2.1.3
- Pitfalls: HIGH — most verified against official changelogs or known breaking changes

**Research date:** 2026-04-07
**Valid until:** 2026-05-07 (stable stack; main risk is pytest-asyncio 1.x API still settling)
