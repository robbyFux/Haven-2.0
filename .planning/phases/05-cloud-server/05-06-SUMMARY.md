---
phase: 05-cloud-server
plan: "06"
subsystem: ai-analysis
tags: [python, celery, tflite, openrouter, httpx, aes-gcm, pytest, lazy-singleton]
dependency_graph:
  requires:
    - server/app/models/event.py (AnalysisResult model — from plan 01)
    - server/app/database.py (AsyncSessionLocal — from plan 01)
    - server/app/celery_app.py (celery_app instance — from plan 01)
    - server/app/config.py (AI_BACKEND, TFLITE_MODEL_PATH, OPENROUTER_* — from plan 01)
    - server/app/services/crypto.py (decrypt_file — from plan 03)
    - server/app/tasks/notifications.py (send_notification_task stub — from plan 04)
  provides:
    - server/app/ml/detector.py (HavenDetector lazy singleton, get_detector)
    - server/app/ml/openrouter.py (analyze_frame_openrouter synchronous client)
    - server/app/tasks/analysis.py (analyze_event_task Celery task — replaces plan 04 stub)
    - server/tests/test_analysis.py (6 tests for all task code paths)
  affects:
    - server/app/tasks/analysis.py (stub from plan 04 replaced with full implementation)
tech_stack:
  added:
    - ai-edge-litert (TFLite inference; already in pyproject.toml [ml] optional group)
    - httpx.Client synchronous (OpenRouter calls from Celery sync context)
  patterns:
    - Module-level lazy singleton for TFLite model (expensive init done once per worker process)
    - Import-inside-function for optional heavy dependencies (ai_edge_litert, PIL, numpy)
    - asyncio.run() to drive async SQLAlchemy session from Celery sync task
    - Module-level imports for mockable names (AsyncSessionLocal, AnalysisResult at top of analysis.py)
    - Lazy import chain: analysis.py → notifications.py via in-function import to avoid forward-reference ImportError
key_files:
  created:
    - server/app/ml/detector.py
    - server/app/ml/openrouter.py
    - server/tests/test_analysis.py
  modified:
    - server/app/tasks/analysis.py (stub from plan 04 replaced with full implementation)
decisions:
  - "AsyncSessionLocal and AnalysisResult imported at module level in analysis.py so tests can patch app.tasks.analysis.AsyncSessionLocal without AttributeError"
  - "get_detector and analyze_frame_openrouter remain lazy (inside function body) — they are optional heavy imports; tests patch their source modules (app.ml.detector, app.ml.openrouter)"
  - "decrypt_file patched at app.services.crypto (source) not app.tasks.analysis (lazy); real function captured before patch to avoid infinite recursion in spy"
  - "HavenDetector.__init__ imports ai_edge_litert inside __init__ to avoid ImportError when the package is not installed (graceful degradation)"
  - "openrouter.py uses httpx.Client (synchronous) not AsyncClient — Celery workers are synchronous by default; asyncio.run for an HTTP call would be wasteful"
  - "get_detector returns None if AI_BACKEND != tflite — avoids loading the model unnecessarily when not configured"
  - "_save_analysis_result checks for existing AnalysisResult before insert — idempotent on Celery task retry"
metrics:
  duration_minutes: 6
  completed_date: "2026-04-08"
  tasks_completed: 2
  files_created: 3
  files_modified: 1
---

# Phase 05 Plan 06: AI Analysis Pipeline Summary

**One-liner:** Celery analyze_event_task with TFLite lazy-singleton and synchronous OpenRouter client, AES-GCM decryption, asyncio.run DB persistence, and 6 passing tests.

## What Was Built

### Task 1: TFLite detector and OpenRouter client

**server/app/ml/detector.py** — TFLite lazy singleton:
- Module-level `_detector: HavenDetector | None = None`
- `get_detector()` returns None when `AI_BACKEND != "tflite"`, otherwise creates and caches a `HavenDetector`
- `HavenDetector.__init__`: imports `ai_edge_litert.interpreter.Interpreter` inside `__init__` (avoids ImportError when package absent), calls `allocate_tensors()`, captures `input_details` and `output_details`
- `HavenDetector.detect(image_bytes)`: PIL decode → resize to 320×320 → numpy uint8 → set_tensor/invoke/get_tensor → EfficientDet output parsing (boxes, classes, scores, count) → filters by confidence ≥ 0.3 → returns `list[dict]` with `label`, `confidence`, `bbox`
- COCO label subset covering security-relevant classes: person, bicycle, car, motorcycle, bus, truck, cat, dog, etc.

**server/app/ml/openrouter.py** — synchronous OpenRouter vision client:
- `analyze_frame_openrouter(jpeg_bytes, model, api_key) -> dict`
- Uses `httpx.Client(timeout=30)` (synchronous — Celery worker context)
- base64-encodes the JPEG, POST to `https://openrouter.ai/api/v1/chat/completions`
- Prompt requests structured JSON `{"labels": [...], "description": "..."}`
- Parses response: tries `json.loads(content)`, falls back to `{"labels": [], "description": content}` if plain text returned
- On any exception: returns `{"labels": [], "description": "Analysis failed: {exc}"}` — never crashes the worker

### Task 2: Celery analysis task and tests

**server/app/tasks/analysis.py** — replaces the plan 04 no-op stub:
- `@celery_app.task(name="app.tasks.analysis.analyze_event_task")`
- Early returns: `AI_BACKEND == "none"` → `{"status": "skipped"}`, `media_path is None` → `{"status": "no_media"}`
- Synchronous file read from `MEDIA_ROOT/media_path`; OSError → `{"status": "error"}`
- Decryption: if `encryption_key_hex` provided, calls `decrypt_file(data, bytes.fromhex(key_hex))`
- TFLite branch: `from app.ml.detector import get_detector` → `detector.detect(bytes)` → extracts labels + max confidence
- OpenRouter branch: `from app.ml.openrouter import analyze_frame_openrouter` → `analyze_frame_openrouter(bytes, model, key)`
- `_save_analysis_result()`: `asyncio.run()` wrapping async SQLAlchemy session; idempotent (skips if AnalysisResult already exists)
- Chains to `send_notification_task.delay(event_id)` via lazy in-function import (forward-reference safe)

**server/tests/test_analysis.py** — 6 tests:
- `test_analyze_event_skipped_when_none`: AI_BACKEND=none → `{"status": "skipped"}`
- `test_analyze_event_no_media`: media_path=None → `{"status": "no_media"}`
- `test_analyze_event_tflite`: mock detector → `{"status": "analyzed", "labels": ["person"]}`; verifies `detect()` called with correct bytes
- `test_analyze_event_openrouter`: mock `analyze_frame_openrouter` → `{"status": "analyzed", "labels": ["car", "person"]}`
- `test_analyze_event_decrypts_media`: real encrypt_file + spy on decrypt_file; verifies key passed correctly and decryption runs before inference
- `test_analyze_event_missing_file`: nonexistent path → `{"status": "error"}` without exception

## Verification Results

```
python3 -m pytest tests/test_analysis.py -v
tests/test_analysis.py::test_analyze_event_skipped_when_none PASSED
tests/test_analysis.py::test_analyze_event_no_media PASSED
tests/test_analysis.py::test_analyze_event_tflite PASSED
tests/test_analysis.py::test_analyze_event_openrouter PASSED
tests/test_analysis.py::test_analyze_event_decrypts_media PASSED
tests/test_analysis.py::test_analyze_event_missing_file PASSED
========================= 6 passed, 1 warning in 0.03s =========================

python3 -m pytest tests/ -v
======================== 44 passed, 2 warnings in 14.45s ========================
```

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Module-level import required for AsyncSessionLocal to be mockable**
- **Found during:** Task 2 — first test run raised `AttributeError: module 'app.tasks.analysis' does not have attribute 'AsyncSessionLocal'`
- **Issue:** `AsyncSessionLocal` was imported lazily inside the nested `_persist()` coroutine, making it impossible to patch at the analysis module level
- **Fix:** Moved `from app.database import AsyncSessionLocal` and `from app.models.event import AnalysisResult` to module-level imports in `analysis.py`; removed the duplicate lazy imports from `_persist()`
- **Files modified:** `server/app/tasks/analysis.py`
- **Commit:** `cddff64`

**2. [Rule 1 - Bug] Infinite recursion in decrypt spy due to patching at source module**
- **Found during:** Task 2 — `test_analyze_event_decrypts_media` hit `maximum recursion depth exceeded`
- **Issue:** The spy function called `from app.services.crypto import decrypt_file as real_decrypt` — but since `app.services.crypto.decrypt_file` was patched, that import returned the spy itself, not the real function
- **Fix:** Captured `real_decrypt_file = decrypt_file` before entering the `patch()` context manager; spy closes over the already-resolved reference
- **Files modified:** `server/tests/test_analysis.py`
- **Commit:** `cddff64`

**3. [Rule 2 - Missing] Plan specified 5 tests; implemented 6**
- Added `test_analyze_event_missing_file` covering the OSError path (file read failure returns `{"status": "error"}`) — this code path existed in the implementation and needed test coverage for correctness

## Known Stubs

- `server/app/tasks/notifications.py` `send_notification_task`: still a no-op stub (plan 05-07). The analysis task chains to it correctly; the chain will execute real notification logic once plan 05-07 is implemented.
- `HavenDetector.detect()` COCO label mapping covers security-relevant classes; full 80-class COCO mapping is not needed for the use case.

## Threat Flags

None — this plan adds no new network endpoints or auth paths. The analysis task is an internal Celery worker function that receives already-validated event_id and media_path from the trusted upload router.

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| Task 1 | `d8a5c11` | feat(05-06): TFLite lazy singleton detector and OpenRouter vision client |
| Task 2 | `cddff64` | feat(05-06): Celery AI analysis task and tests — 6 passing |

## Self-Check: PASSED

Files verified:
- `server/app/ml/detector.py` FOUND
- `server/app/ml/openrouter.py` FOUND
- `server/app/tasks/analysis.py` FOUND
- `server/tests/test_analysis.py` FOUND

Commits verified:
- `d8a5c11` FOUND
- `cddff64` FOUND
