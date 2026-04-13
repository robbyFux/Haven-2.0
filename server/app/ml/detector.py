"""
TFLite object detection inference wrapper for Haven Cloud.

Provides a lazy singleton pattern so the model is loaded once per worker process
and reused across all task invocations — model initialization is expensive (~100ms+).

Backend: ai-edge-litert (EfficientDet Lite 0, COCO-trained)
- Replaces deprecated tflite-runtime (no Python 3.12 wheels)
- Same .tflite model format; import path is ai_edge_litert.interpreter

Usage:
    detector = get_detector()  # returns HavenDetector or None
    if detector:
        results = detector.detect(jpeg_bytes)
        # [{"label": "person", "confidence": 0.87, "bbox": [y1, x1, y2, x2]}, ...]
"""

from __future__ import annotations

import io
import logging
import os
from typing import Optional

from app.config import settings

logger = logging.getLogger(__name__)

# Module-level lazy singleton — populated on first get_detector() call
_detector: Optional["HavenDetector"] = None


def get_detector(ai_backend: str | None = None) -> Optional["HavenDetector"]:
    """
    Return the lazy singleton HavenDetector, creating it on first call.

    Returns None if the effective AI backend is not 'tflite', or if model
    loading fails.  Subsequent calls return the cached instance without
    re-loading.

    @param ai_backend: the AI backend string read from the DB at call-time
                       (e.g. from get_ai_backend_sync()).  When None the
                       function falls back to settings.AI_BACKEND so that
                       behaviour is unchanged for callers that do not pass it.
    """
    global _detector
    effective_backend = ai_backend if ai_backend is not None else settings.AI_BACKEND
    logger.info("get_detector: ai_backend=%r effective_backend=%r", ai_backend, effective_backend)
    if effective_backend != "tflite":
        logger.info("get_detector: backend is not tflite — returning None")
        return None
    model_path = str(settings.TFLITE_MODEL_PATH)
    model_exists = os.path.isfile(model_path)
    logger.info("get_detector: model_path=%r exists=%s", model_path, model_exists)
    if _detector is None:
        logger.info("get_detector: initializing HavenDetector (first call)")
        try:
            _detector = HavenDetector()
            logger.info("get_detector: HavenDetector initialized successfully: %r", _detector)
        except Exception as exc:
            logger.exception("get_detector: HavenDetector init failed: %s", exc)
            return None
    else:
        logger.debug("get_detector: returning cached HavenDetector instance")
    return _detector


# COCO label subset relevant for security monitoring
# Index corresponds to EfficientDet Lite 0 class IDs (1-based, shifted to 0-based below)
_COCO_LABELS: dict[int, str] = {
    0: "person",
    1: "bicycle",
    2: "car",
    3: "motorcycle",
    4: "airplane",
    5: "bus",
    6: "train",
    7: "truck",
    8: "boat",
    14: "bird",
    15: "cat",
    16: "dog",
    17: "horse",
    18: "sheep",
    19: "cow",
}

# Confidence threshold — detections below this are discarded
_CONFIDENCE_THRESHOLD = 0.3

# EfficientDet Lite 0 expects 320×320 RGB input
_INPUT_SIZE = 320


class HavenDetector:
    """
    TFLite object detector wrapping EfficientDet Lite 0.

    Loads the model from settings.TFLITE_MODEL_PATH on construction.
    Uses ai_edge_litert.interpreter (Python 3.12 compatible replacement for tflite-runtime).

    The interpreter is not thread-safe — Celery workers run one task at a time per process,
    so no locking is required in the default single-threaded worker configuration.
    """

    def __init__(self) -> None:
        # Import inside __init__ to avoid ImportError when ai-edge-litert is not installed
        from ai_edge_litert.interpreter import Interpreter

        self._interpreter = Interpreter(model_path=str(settings.TFLITE_MODEL_PATH))
        self._interpreter.allocate_tensors()
        self._input_details = self._interpreter.get_input_details()
        self._output_details = self._interpreter.get_output_details()

    def detect(self, image_bytes: bytes) -> list[dict]:
        """
        Run object detection on raw image bytes (JPEG, PNG, or any PIL-supported format).

        @param image_bytes: raw bytes of the image file
        @return: list of detection dicts, each with keys:
                 - "label": str (COCO class name, e.g. "person")
                 - "confidence": float (0.0–1.0)
                 - "bbox": [y1, x1, y2, x2] (normalised 0.0–1.0)
                 Only detections above _CONFIDENCE_THRESHOLD are returned.
        """
        import numpy as np
        from PIL import Image

        logger.debug("HavenDetector.detect: input_size=%d bytes", len(image_bytes))

        # Decode image and resize to model input size
        img = Image.open(io.BytesIO(image_bytes)).convert("RGB")
        original_size = img.size
        img = img.resize((_INPUT_SIZE, _INPUT_SIZE))
        input_array = np.array(img, dtype=np.uint8)
        input_array = np.expand_dims(input_array, axis=0)  # shape: (1, 320, 320, 3)
        logger.debug("HavenDetector.detect: image decoded original_size=%s resized_to=%dx%d", original_size, _INPUT_SIZE, _INPUT_SIZE)

        # Run inference
        self._interpreter.set_tensor(self._input_details[0]["index"], input_array)
        self._interpreter.invoke()
        logger.debug("HavenDetector.detect: inference invoked")

        # Parse EfficientDet Lite 0 output tensors:
        #   output[0]: boxes    (1, N, 4) — [y1, x1, y2, x2] normalised
        #   output[1]: classes  (1, N)    — 0-based class index
        #   output[2]: scores   (1, N)    — confidence 0–1
        #   output[3]: count    (1,)      — number of valid detections
        boxes = self._interpreter.get_tensor(self._output_details[0]["index"])[0]
        classes = self._interpreter.get_tensor(self._output_details[1]["index"])[0]
        scores = self._interpreter.get_tensor(self._output_details[2]["index"])[0]
        count = int(self._interpreter.get_tensor(self._output_details[3]["index"])[0])
        logger.debug("HavenDetector.detect: raw detection count=%d (before threshold filtering)", count)

        results: list[dict] = []
        for i in range(count):
            confidence = float(scores[i])
            if confidence < _CONFIDENCE_THRESHOLD:
                continue
            class_id = int(classes[i])
            label = _COCO_LABELS.get(class_id, f"class_{class_id}")
            bbox = [float(v) for v in boxes[i]]  # [y1, x1, y2, x2]
            results.append({"label": label, "confidence": confidence, "bbox": bbox})

        logger.info(
            "HavenDetector.detect: results after threshold=%.2f: %r",
            _CONFIDENCE_THRESHOLD,
            [{"label": r["label"], "confidence": round(r["confidence"], 3)} for r in results],
        )
        return results
