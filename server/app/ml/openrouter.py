"""
OpenRouter vision API client for Haven Cloud AI analysis.

Sends a JPEG frame to the OpenRouter chat completions endpoint using a vision-capable
model (e.g. google/gemini-flash-1.5) and parses the structured JSON response.

Called from Celery tasks (synchronous context), so this module uses httpx.Client
(synchronous) rather than httpx.AsyncClient.

Usage:
    result = analyze_frame_openrouter(jpeg_bytes, settings.OPENROUTER_MODEL, settings.OPENROUTER_API_KEY)
    # {"labels": ["person"], "description": "A person is walking near the door."}
"""

import base64
import json
import logging

import httpx

logger = logging.getLogger(__name__)

_OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"
_TIMEOUT_SECONDS = 30

_ANALYSIS_PROMPT = (
    "Analyze this security camera frame. "
    "Describe any persons, objects, or unusual activity. "
    'Respond with JSON: {"labels": ["person", ...], "description": "..."}'
)


def analyze_frame_openrouter(jpeg_bytes: bytes, model: str, api_key: str) -> dict:
    """
    Send a JPEG frame to OpenRouter's vision API and parse the detection result.

    Uses synchronous httpx.Client because this function is called from a Celery
    task (synchronous worker context — asyncio.run would work but is unnecessary).

    On any error (network failure, API error, JSON parse failure), returns a
    safe fallback dict so the caller can always store a result.

    @param jpeg_bytes: raw JPEG bytes of the image frame to analyse
    @param model: OpenRouter model identifier, e.g. "google/gemini-flash-1.5"
    @param api_key: OpenRouter API key (Bearer token)
    @return: dict with keys:
             - "labels": list[str] — detected object labels
             - "description": str — free-text description from the model
    """
    b64_image = base64.b64encode(jpeg_bytes).decode("utf-8")

    payload = {
        "model": model,
        "messages": [
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": _ANALYSIS_PROMPT},
                    {
                        "type": "image_url",
                        "image_url": {"url": f"data:image/jpeg;base64,{b64_image}"},
                    },
                ],
            }
        ],
    }

    try:
        with httpx.Client(timeout=_TIMEOUT_SECONDS) as client:
            response = client.post(
                _OPENROUTER_URL,
                headers={
                    "Authorization": f"Bearer {api_key}",
                    "Content-Type": "application/json",
                },
                json=payload,
            )
            response.raise_for_status()

        content = response.json()["choices"][0]["message"]["content"]

        # Try to parse structured JSON from the model response
        try:
            parsed = json.loads(content)
            labels = parsed.get("labels", [])
            description = parsed.get("description", content)
            # Ensure labels is always a list of strings
            if not isinstance(labels, list):
                labels = []
            labels = [str(l) for l in labels]
            return {"labels": labels, "description": description}
        except (json.JSONDecodeError, KeyError, TypeError):
            # Model returned plain text instead of JSON — use as description
            return {"labels": [], "description": content}

    except Exception as exc:  # noqa: BLE001
        logger.warning("OpenRouter analysis failed: %s", exc)
        return {"labels": [], "description": f"Analysis failed: {exc}"}
