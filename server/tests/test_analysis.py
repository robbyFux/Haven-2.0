"""
Tests for the analyze_event_task Celery task.

All tests call the task function directly (not via .delay()) to run synchronously
in-process without a Redis broker. ML backends are mocked to avoid requiring
ai-edge-litert or OpenRouter credentials in CI.

Test coverage:
  - AI_BACKEND=none returns {"status": "skipped"} without touching DB
  - media_path=None returns {"status": "no_media"}
  - TFLite backend: mock detector, verify AnalysisResult created
  - OpenRouter backend: mock analyze_frame_openrouter, verify AnalysisResult created
  - Encrypted media: verify decrypt_file is called before inference
  - Missing file returns error status without crashing

AI backend is now read from the DB via get_ai_backend_sync(); all tests patch
that function rather than settings.AI_BACKEND.
"""

import asyncio
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

import app.ml.detector  # ensure module is imported so patch("app.ml.detector.*") resolves
from app.services.crypto import encrypt_file
from app.tasks.analysis import analyze_event_task


# --------------------------------------------------------------------------- #
# AsyncSessionLocal mock factory                                               #
# --------------------------------------------------------------------------- #

def _make_mock_session_ctx(saved: dict | None = None):
    """
    Build a mock async context manager that simulates AsyncSessionLocal().

    The session supports:
      - execute(stmt) -> result with scalar_one_or_none() == None
      - add(obj) — captures obj into saved dict if provided
      - commit() — no-op coroutine
    """
    if saved is None:
        saved = {}

    mock_session = MagicMock()
    mock_session.execute = AsyncMock(return_value=MagicMock(scalar_one_or_none=MagicMock(return_value=None)))
    mock_session.add = MagicMock(side_effect=lambda obj: saved.update({"result": obj}))
    mock_session.commit = AsyncMock(return_value=None)

    mock_ctx = MagicMock()
    mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
    mock_ctx.__aexit__ = AsyncMock(return_value=False)

    return mock_ctx, saved


# --------------------------------------------------------------------------- #
# Tests                                                                        #
# --------------------------------------------------------------------------- #


def test_analyze_event_skipped_when_none():
    """When get_ai_backend_sync() returns 'none' the task returns skipped without any DB writes."""
    with patch("app.tasks.analysis.get_ai_backend_sync", return_value="none"):
        result = analyze_event_task(event_id=1, media_path="some/path.jpg", encryption_key_hex=None)

    assert result == {"status": "skipped"}


def test_analyze_event_no_media():
    """When media_path is None the task returns no_media without any DB writes."""
    with (
        patch("app.tasks.analysis.get_ai_backend_sync", return_value="tflite"),
        patch("app.tasks.notifications.send_notification_task"),
    ):
        result = analyze_event_task(event_id=2, media_path=None, encryption_key_hex=None)

    assert result == {"status": "no_media"}


def test_analyze_event_tflite(tmp_path):
    """TFLite backend: mocked detector produces detections and task returns analyzed."""
    # Create a fake JPEG file under tmp_path (acts as MEDIA_ROOT)
    fake_jpeg = b"\xff\xd8\xff\xe0FAKEJPEG"
    (tmp_path / "video.jpg").write_bytes(fake_jpeg)

    mock_detection = [{"label": "person", "confidence": 0.92, "bbox": [0.1, 0.1, 0.9, 0.9]}]
    mock_detector = MagicMock()
    mock_detector.detect.return_value = mock_detection

    mock_ctx, saved = _make_mock_session_ctx()

    # get_detector is imported lazily inside the task: patch its source module
    with (
        patch("app.tasks.analysis.get_ai_backend_sync", return_value="tflite"),
        patch("app.tasks.analysis.settings") as mock_settings,
        patch("app.ml.detector.get_detector", return_value=mock_detector),
        patch("app.tasks.analysis.AsyncSessionLocal", return_value=mock_ctx),
        patch("app.tasks.notifications.send_notification_task"),
    ):
        mock_settings.MEDIA_ROOT = str(tmp_path)

        result = analyze_event_task(event_id=101, media_path="video.jpg", encryption_key_hex=None)

    assert result["status"] == "analyzed"
    assert "person" in result["labels"]
    mock_detector.detect.assert_called_once_with(fake_jpeg)


def test_analyze_event_openrouter(tmp_path):
    """OpenRouter backend: mocked analyze_frame_openrouter produces labels and description."""
    fake_jpeg = b"\xff\xd8\xff\xe0FAKEJPEG2"
    (tmp_path / "frame.jpg").write_bytes(fake_jpeg)

    mock_openrouter_result = {
        "labels": ["car", "person"],
        "description": "A person is standing next to a car.",
    }

    mock_ctx, saved = _make_mock_session_ctx()

    # analyze_frame_openrouter is imported lazily: patch its source module
    with (
        patch("app.tasks.analysis.get_ai_backend_sync", return_value="openrouter"),
        patch("app.tasks.analysis.get_openrouter_settings_sync", return_value=("test-key", "google/gemini-flash-1.5")),
        patch("app.tasks.analysis.settings") as mock_settings,
        patch("app.ml.openrouter.analyze_frame_openrouter", return_value=mock_openrouter_result),
        patch("app.tasks.analysis.AsyncSessionLocal", return_value=mock_ctx),
        patch("app.tasks.notifications.send_notification_task"),
    ):
        mock_settings.MEDIA_ROOT = str(tmp_path)

        result = analyze_event_task(event_id=202, media_path="frame.jpg", encryption_key_hex=None)

    assert result["status"] == "analyzed"
    assert set(result["labels"]) == {"car", "person"}


def test_analyze_event_decrypts_media(tmp_path):
    """Encrypted media is decrypted with the provided key before inference."""
    plaintext = b"\xff\xd8\xff\xe0REALIMAGE"
    key = bytes(range(32))  # 32-byte test key
    key_hex = key.hex()
    encrypted = encrypt_file(plaintext, key)

    (tmp_path / "video.enc").write_bytes(encrypted)

    decrypted_spy = {}

    # Capture real decrypt_file before patching so spy can call it without recursion
    from app.services.crypto import decrypt_file as _real_decrypt_file

    def spy_decrypt(data: bytes, k: bytes) -> bytes:
        decrypted_spy["called"] = True
        decrypted_spy["key"] = k
        return _real_decrypt_file(data, k)

    mock_detector = MagicMock()
    mock_detector.detect.return_value = []

    mock_ctx, saved = _make_mock_session_ctx()

    with (
        patch("app.tasks.analysis.get_ai_backend_sync", return_value="tflite"),
        patch("app.tasks.analysis.settings") as mock_settings,
        patch("app.services.crypto.decrypt_file", side_effect=spy_decrypt),
        patch("app.ml.detector.get_detector", return_value=mock_detector),
        patch("app.tasks.analysis.AsyncSessionLocal", return_value=mock_ctx),
        patch("app.tasks.notifications.send_notification_task"),
    ):
        mock_settings.MEDIA_ROOT = str(tmp_path)

        result = analyze_event_task(
            event_id=303,
            media_path="video.enc",
            encryption_key_hex=key_hex,
        )

    assert decrypted_spy.get("called") is True
    assert decrypted_spy["key"] == key
    assert result["status"] == "analyzed"


def test_analyze_event_tflite_video(tmp_path):
    """TFLite backend with a .mp4 file: frames are extracted and detections aggregated."""
    fake_mp4 = b"\x00\x00\x00\x18ftypisom"  # plausible MP4 magic bytes (not a real video)
    (tmp_path / "clip.mp4").write_bytes(fake_mp4)

    # Two opaque frame sentinels — detector is mocked so actual content is irrelevant
    frame1 = MagicMock(name="frame1")
    frame2 = MagicMock(name="frame2")

    # Frame 1 → person; frame 2 → car (lower confidence than person)
    detection_frame1 = [{"label": "person", "confidence": 0.91, "bbox": [0.1, 0.1, 0.9, 0.9]}]
    detection_frame2 = [
        {"label": "car", "confidence": 0.75, "bbox": [0.2, 0.2, 0.8, 0.8]},
        {"label": "person", "confidence": 0.55, "bbox": [0.3, 0.3, 0.7, 0.7]},
    ]

    mock_detector = MagicMock()
    mock_detector.detect.side_effect = [detection_frame1, detection_frame2]

    mock_ctx, saved = _make_mock_session_ctx()

    with (
        patch("app.tasks.analysis.get_ai_backend_sync", return_value="tflite"),
        patch("app.tasks.analysis.settings") as mock_settings,
        patch("app.ml.detector.get_detector", return_value=mock_detector),
        patch("app.ml.detector.extract_frames_from_video", return_value=[frame1, frame2]),
        patch("app.tasks.analysis.AsyncSessionLocal", return_value=mock_ctx),
        patch("app.tasks.notifications.send_notification_task"),
    ):
        mock_settings.MEDIA_ROOT = str(tmp_path)

        result = analyze_event_task(event_id=501, media_path="clip.mp4", encryption_key_hex=None)

    assert result["status"] == "analyzed"
    # Both labels must be present
    assert "person" in result["labels"]
    assert "car" in result["labels"]
    # detect() called once per frame
    assert mock_detector.detect.call_count == 2
    # Highest-confidence person (0.91 from frame1) wins over 0.55 from frame2
    assert saved["result"].confidence == pytest.approx(0.91)


def test_analyze_event_tflite_video_no_frames(tmp_path):
    """When extract_frames_from_video returns empty list the task returns an error."""
    fake_mp4 = b"\x00\x00\x00\x18ftypisom"
    (tmp_path / "empty.mp4").write_bytes(fake_mp4)

    mock_detector = MagicMock()

    with (
        patch("app.tasks.analysis.get_ai_backend_sync", return_value="tflite"),
        patch("app.tasks.analysis.settings") as mock_settings,
        patch("app.ml.detector.get_detector", return_value=mock_detector),
        patch("app.ml.detector.extract_frames_from_video", return_value=[]),
        patch("app.tasks.notifications.send_notification_task"),
    ):
        mock_settings.MEDIA_ROOT = str(tmp_path)

        result = analyze_event_task(event_id=502, media_path="empty.mp4", encryption_key_hex=None)

    assert result["status"] == "error"
    assert "frames" in result["detail"].lower()
    mock_detector.detect.assert_not_called()


def test_analyze_event_missing_file(tmp_path):
    """A missing media file returns error status without raising an exception."""
    with (
        patch("app.tasks.analysis.get_ai_backend_sync", return_value="tflite"),
        patch("app.tasks.analysis.settings") as mock_settings,
    ):
        mock_settings.MEDIA_ROOT = str(tmp_path)

        result = analyze_event_task(
            event_id=404,
            media_path="nonexistent/file.jpg",
            encryption_key_hex=None,
        )

    assert result["status"] == "error"
    assert "detail" in result


# --------------------------------------------------------------------------- #
# Tests for ai_settings service                                                #
# --------------------------------------------------------------------------- #


def test_get_ai_backend_sync_reads_from_db():
    """get_ai_backend_sync() returns the DB value when the row exists."""
    from app.services.ai_settings import get_ai_backend_sync

    mock_row = MagicMock()
    mock_row.__getitem__ = MagicMock(return_value="tflite")

    mock_session = MagicMock()
    mock_result = MagicMock()
    mock_result.fetchone.return_value = mock_row
    mock_session.execute = AsyncMock(return_value=mock_result)

    mock_ctx = MagicMock()
    mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
    mock_ctx.__aexit__ = AsyncMock(return_value=False)

    with patch("app.services.ai_settings.AsyncSessionLocal", return_value=mock_ctx):
        result = get_ai_backend_sync()

    assert result == "tflite"


def test_get_ai_backend_sync_falls_back_to_env_on_missing_row():
    """get_ai_backend_sync() falls back to settings.AI_BACKEND when DB row is absent."""
    from app.services.ai_settings import get_ai_backend_sync

    mock_session = MagicMock()
    mock_result = MagicMock()
    mock_result.fetchone.return_value = None
    mock_session.execute = AsyncMock(return_value=mock_result)

    mock_ctx = MagicMock()
    mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
    mock_ctx.__aexit__ = AsyncMock(return_value=False)

    with (
        patch("app.services.ai_settings.AsyncSessionLocal", return_value=mock_ctx),
        patch("app.services.ai_settings.settings") as mock_settings,
    ):
        mock_settings.AI_BACKEND = "none"
        result = get_ai_backend_sync()

    assert result == "none"


def test_get_ai_backend_sync_falls_back_to_env_on_db_error():
    """get_ai_backend_sync() falls back to settings.AI_BACKEND on any DB exception."""
    from app.services.ai_settings import get_ai_backend_sync

    mock_session = MagicMock()
    mock_session.execute = AsyncMock(side_effect=Exception("connection refused"))

    mock_ctx = MagicMock()
    mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
    mock_ctx.__aexit__ = AsyncMock(return_value=False)

    with (
        patch("app.services.ai_settings.AsyncSessionLocal", return_value=mock_ctx),
        patch("app.services.ai_settings.settings") as mock_settings,
    ):
        mock_settings.AI_BACKEND = "none"
        result = get_ai_backend_sync()

    assert result == "none"
