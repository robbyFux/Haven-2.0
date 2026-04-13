"""
Celery AI analysis task for Haven Cloud.

analyze_event_task processes an uploaded event by:
1. Loading the media file bytes from MEDIA_ROOT
2. Decrypting if an encryption_key_hex was provided (AES-256-GCM)
3. Running AI inference via the configured backend (tflite or openrouter)
4. Storing the result in the AnalysisResult table
5. Chaining to the notification task

AI_BACKEND values:
  "none"       — skip analysis entirely, return {"status": "skipped"}
  "tflite"     — run local EfficientDet Lite 0 inference via get_detector()
  "openrouter" — send frame to OpenRouter vision API via analyze_frame_openrouter()
"""

import asyncio
import json
import logging
import os

from app.celery_app import celery_app
from app.config import settings
from app.database import AsyncSessionLocal
from app.models.event import AnalysisResult
from app.services.ai_settings import get_ai_backend_sync, get_openrouter_settings_sync

logger = logging.getLogger(__name__)


@celery_app.task(name="app.tasks.analysis.analyze_event_task")
def analyze_event_task(
    event_id: int,
    media_path: str | None,
    encryption_key_hex: str | None,
) -> dict:
    """
    Run AI analysis on an uploaded event media file and store the result.

    Called asynchronously by the events router after a successful upload.
    When AI_BACKEND is "none" or no media is present, the task exits early
    without writing an AnalysisResult row.

    @param event_id: database ID of the Event to analyse
    @param media_path: MEDIA_ROOT-relative path to the media file, or None
    @param encryption_key_hex: hex-encoded 32-byte AES key if file is encrypted, else None
    @return: dict with "status" key: "skipped", "no_media", or "analyzed"
    """
    logger.info("analyze_event_task: started for event_id=%d media_path=%r", event_id, media_path)

    # Read AI backend from DB (admin_panel_aisettings) so WebUI changes take
    # effect without a Celery worker restart.
    ai_backend = get_ai_backend_sync()
    logger.info("analyze_event_task: get_ai_backend_sync() returned %r for event_id=%d", ai_backend, event_id)

    if ai_backend == "none":
        logger.info("analyze_event_task: ai_backend=none — skipping for event_id=%d", event_id)
        return {"status": "skipped"}

    if media_path is None:
        # No media to analyse — skip inference but still dispatch notification.
        logger.info("analyze_event_task: media_path is None — no media to analyse for event_id=%d", event_id)
        from app.tasks.notifications import send_notification_task  # noqa: PLC0415
        send_notification_task.delay(event_id)
        return {"status": "no_media"}

    # --- Load media bytes from filesystem (synchronous) ---
    full_path = os.path.join(settings.MEDIA_ROOT, media_path)
    file_exists = os.path.isfile(full_path)
    logger.info(
        "analyze_event_task: video path=%r exists=%s for event_id=%d",
        full_path,
        file_exists,
        event_id,
    )
    try:
        with open(full_path, "rb") as fh:
            file_data = fh.read()
        logger.debug("analyze_event_task: read %d bytes from %r for event_id=%d", len(file_data), full_path, event_id)
    except OSError as exc:
        logger.error("analyze_event_task: cannot read media file %s: %s", full_path, exc)
        return {"status": "error", "detail": str(exc)}

    # --- Decrypt if the file was stored encrypted ---
    if encryption_key_hex:
        from app.services.crypto import decrypt_file

        try:
            file_data = decrypt_file(file_data, bytes.fromhex(encryption_key_hex))
            logger.debug("analyze_event_task: decryption successful for event_id=%d, %d bytes", event_id, len(file_data))
        except Exception as exc:  # noqa: BLE001
            logger.error("analyze_event_task: decryption failed for event %d: %s", event_id, exc)
            return {"status": "error", "detail": f"Decryption failed: {exc}"}

    # --- Run inference ---
    labels: list[str] = []
    confidence: float | None = None
    description: str | None = None
    raw: dict = {}

    if ai_backend == "tflite":
        logger.info("analyze_event_task: branch=tflite for event_id=%d", event_id)
        from app.ml.detector import get_detector

        logger.debug("analyze_event_task: calling get_detector(ai_backend='tflite') for event_id=%d", event_id)
        detector = get_detector(ai_backend=ai_backend)
        logger.info("analyze_event_task: get_detector() returned %r for event_id=%d", detector, event_id)
        if detector is None:
            logger.warning("analyze_event_task: TFLite detector not available for event %d", event_id)
            return {"status": "error", "detail": "TFLite detector not available"}

        try:
            logger.debug("analyze_event_task: calling detector.detect() for event_id=%d, input_size=%d bytes", event_id, len(file_data))
            detections = detector.detect(file_data)
            logger.info("analyze_event_task: detector.detect() raw output for event_id=%d: %r", event_id, detections)
            labels = [d["label"] for d in detections]
            confidence = max((d["confidence"] for d in detections), default=None)
            raw = {"detections": detections}
        except Exception as exc:  # noqa: BLE001
            logger.exception("analyze_event_task: TFLite inference failed for event %d", event_id)
            return {"status": "error", "detail": f"Inference failed: {exc}"}

    elif ai_backend == "openrouter":
        logger.info("analyze_event_task: branch=openrouter for event_id=%d", event_id)
        from app.ml.openrouter import analyze_frame_openrouter

        # Read OpenRouter credentials from DB so WebUI changes are picked up.
        openrouter_api_key, openrouter_model = get_openrouter_settings_sync()
        logger.debug("analyze_event_task: openrouter model=%r api_key_set=%s for event_id=%d", openrouter_model, bool(openrouter_api_key), event_id)

        try:
            result = analyze_frame_openrouter(
                file_data,
                openrouter_model,
                openrouter_api_key,
            )
            logger.info("analyze_event_task: openrouter raw result for event_id=%d: %r", event_id, result)
            labels = result.get("labels", [])
            description = result.get("description")
            raw = result
        except Exception as exc:  # noqa: BLE001
            logger.exception("analyze_event_task: OpenRouter call failed for event %d", event_id)
            return {"status": "error", "detail": f"OpenRouter failed: {exc}"}

    else:
        logger.warning("analyze_event_task: unknown ai_backend=%r for event_id=%d — skipping inference", ai_backend, event_id)
        return {"status": "skipped", "detail": f"unknown backend: {ai_backend}"}

    # --- Persist AnalysisResult ---
    logger.info(
        "analyze_event_task: saving AnalysisResult for event_id=%d backend=%r labels=%r confidence=%s",
        event_id, ai_backend, labels, confidence,
    )
    _save_analysis_result(
        event_id=event_id,
        backend=ai_backend,
        labels=labels,
        confidence=confidence,
        description=description,
        raw_result=raw,
    )
    logger.info("analyze_event_task: AnalysisResult saved for event_id=%d", event_id)

    # --- Chain to notification task ---
    # Lazy import to avoid forward-reference ImportError:
    # notifications.py is a stub until plan 05-07 implements it.
    from app.tasks.notifications import send_notification_task  # noqa: PLC0415

    send_notification_task.delay(event_id)

    return {"status": "analyzed", "labels": labels}


def _save_analysis_result(
    event_id: int,
    backend: str,
    labels: list[str],
    confidence: float | None,
    description: str | None,
    raw_result: dict,
) -> None:
    """
    Persist an AnalysisResult row to the database using asyncio.run().

    Celery workers run synchronously by default, so we spin a new event loop
    for the async SQLAlchemy session.

    @param event_id: FK to events.id
    @param backend: "tflite" or "openrouter"
    @param labels: list of detected labels
    @param confidence: highest confidence score, or None
    @param description: free-text description (OpenRouter), or None
    @param raw_result: full raw result dict for debugging
    """

    async def _persist() -> None:
        from sqlalchemy import select

        async with AsyncSessionLocal() as session:
            # Check if result already exists (idempotent on retry)
            existing = await session.execute(
                select(AnalysisResult).where(AnalysisResult.event_id == event_id)
            )
            if existing.scalar_one_or_none() is not None:
                logger.info(
                    "_save_analysis_result: AnalysisResult already exists for event %d, skipping",
                    event_id,
                )
                return

            result = AnalysisResult(
                event_id=event_id,
                backend=backend,
                labels=json.dumps(labels),
                confidence=confidence,
                description=description,
                raw_result=json.dumps(raw_result),
            )
            session.add(result)
            await session.commit()

    asyncio.run(_persist())
