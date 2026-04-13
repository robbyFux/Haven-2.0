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

    # For video files use the helper:
    frames = extract_frames_from_video(video_bytes, n_frames=5)
    all_detections = [d for frame in frames for d in detector.detect(frame)]
"""

from __future__ import annotations

import io
import logging
import os
import tempfile
from typing import TYPE_CHECKING, Optional

from app.config import settings

if TYPE_CHECKING:
    import numpy as np

logger = logging.getLogger(__name__)

# Module-level lazy singleton — populated on first get_detector() call
_detector: Optional["HavenDetector"] = None

# Number of evenly-spaced frames extracted from a video for analysis
_VIDEO_FRAME_COUNT = 5


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


def extract_frames_from_video(video_bytes: bytes, n_frames: int = _VIDEO_FRAME_COUNT) -> list[np.ndarray]:
    """
    Extract N evenly-spaced frames from a video file using OpenCV.

    Writes the video bytes to a temporary file (cv2.VideoCapture requires a
    file path, not an in-memory buffer), reads the requested frames, then
    removes the temp file.

    @param video_bytes: raw bytes of the video file (MP4, AVI, etc.)
    @param n_frames: number of frames to extract, spread across the full duration
    @return: list of (H, W, 3) uint8 numpy arrays in BGR colour order (cv2 native).
             Returns an empty list if the video cannot be opened or has no frames.
    """
    import cv2
    import numpy as np

    frames: list[np.ndarray] = []

    # cv2.VideoCapture does not support in-memory buffers reliably across platforms,
    # so write to a named temp file and delete it afterwards.
    with tempfile.NamedTemporaryFile(suffix=".mp4", delete=False) as tmp:
        tmp.write(video_bytes)
        tmp_path = tmp.name

    try:
        cap = cv2.VideoCapture(tmp_path)
        if not cap.isOpened():
            logger.warning("extract_frames_from_video: cannot open video (%d bytes)", len(video_bytes))
            return frames

        total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        if total_frames <= 0:
            logger.warning("extract_frames_from_video: video has no readable frame count")
            cap.release()
            return frames

        # Clamp n_frames to total_frames so we never seek past the end
        actual_n = min(n_frames, total_frames)
        # Compute evenly-spaced frame indices across [0, total_frames)
        step = total_frames / actual_n
        indices = [int(i * step) for i in range(actual_n)]

        for idx in indices:
            cap.set(cv2.CAP_PROP_POS_FRAMES, idx)
            ret, frame = cap.read()
            if ret and frame is not None:
                frames.append(frame)

        cap.release()
        logger.info(
            "extract_frames_from_video: extracted %d/%d frames from %d-byte video",
            len(frames), actual_n, len(video_bytes),
        )
    finally:
        os.unlink(tmp_path)

    return frames


# Full COCO 90-class label map (0-based indices) for EfficientDet Lite 0.
# Covers all classes the model can output — prevents "class_N" fallback labels.
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
    9: "traffic light",
    10: "fire hydrant",
    11: "stop sign",
    12: "parking meter",
    13: "bench",
    14: "bird",
    15: "cat",
    16: "dog",
    17: "horse",
    18: "sheep",
    19: "cow",
    20: "elephant",
    21: "bear",
    22: "zebra",
    23: "giraffe",
    24: "backpack",
    25: "umbrella",
    26: "handbag",
    27: "tie",
    28: "suitcase",
    29: "frisbee",
    30: "skis",
    31: "snowboard",
    32: "sports ball",
    33: "kite",
    34: "baseball bat",
    35: "baseball glove",
    36: "skateboard",
    37: "surfboard",
    38: "tennis racket",
    39: "bottle",
    40: "wine glass",
    41: "cup",
    42: "fork",
    43: "knife",
    44: "spoon",
    45: "bowl",
    46: "banana",
    47: "apple",
    48: "sandwich",
    49: "orange",
    50: "broccoli",
    51: "carrot",
    52: "hot dog",
    53: "pizza",
    54: "donut",
    55: "cake",
    56: "chair",
    57: "couch",
    58: "potted plant",
    59: "bed",
    60: "dining table",
    61: "toilet",
    62: "tv",
    63: "laptop",
    64: "mouse",
    65: "remote",
    66: "keyboard",
    67: "cell phone",
    68: "microwave",
    69: "oven",
    70: "toaster",
    71: "sink",
    72: "refrigerator",
    73: "book",
    74: "clock",
    75: "vase",
    76: "scissors",
    77: "teddy bear",
    78: "hair drier",
    79: "toothbrush",
    # Indices 80–89 are unused in COCO but included for completeness
    80: "hair brush",
    81: "blanket",
    82: "bridge",
    83: "book",
    84: "box",
    85: "counter",
    86: "desk",
    87: "door",
    88: "fruit",
    89: "gravel",
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

    def detect(self, image_input: "bytes | bytearray | np.ndarray") -> list[dict]:
        """
        Run object detection on a single image.

        Accepts either raw image bytes (JPEG/PNG/any PIL-supported format) or a
        numpy array in BGR format with shape (H, W, 3) as returned by
        cv2.VideoCapture.read().  BGR arrays are converted to RGB automatically.

        @param image_input: raw image bytes or a (H, W, 3) uint8 numpy array (BGR)
        @return: list of detection dicts, each with keys:
                 - "label": str (COCO class name, e.g. "person")
                 - "confidence": float (0.0-1.0)
                 - "bbox": [y1, x1, y2, x2] (normalised 0.0-1.0)
                 Only detections above _CONFIDENCE_THRESHOLD are returned.
        """
        import numpy as np
        from PIL import Image

        if isinstance(image_input, (bytes, bytearray)):
            logger.debug("HavenDetector.detect: input_size=%d bytes", len(image_input))
            img = Image.open(io.BytesIO(image_input)).convert("RGB")
        else:
            # numpy array (H, W, 3) in BGR — cv2 convention; flip to RGB for PIL
            frame_rgb = image_input[:, :, ::-1].copy() if image_input.ndim == 3 and image_input.shape[2] == 3 else image_input
            img = Image.fromarray(frame_rgb.astype(np.uint8)).convert("RGB")
            logger.debug("HavenDetector.detect: input numpy array shape=%s", image_input.shape)

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
        #   output[2]: scores   (1, N)    — confidence 0-1
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
