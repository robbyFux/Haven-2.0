---
status: awaiting_human_verify
trigger: "TFLite model warning still shows after docker compose up --build, even though the file exists at server/efficientdet_lite0.tflite and server/app/efficientdet_lite0.tflite"
created: 2026-04-13T00:00:00Z
updated: 2026-04-13T00:00:00Z
---

## Current Focus

hypothesis: webui container cannot see the model file because it is not mounted
test: confirmed — docker-compose.yml webui service only mounts ./media and ./webui, not the model file
expecting: adding volume mount to webui service will resolve the warning
next_action: patch docker-compose.yml to add model volume mount to webui

## Symptoms

expected: AI settings page shows no warning when model file exists at server/efficientdet_lite0.tflite
actual: Warning shown: "TFLite-Modell nicht gefunden. Bitte kopieren Sie die Datei nach: /app/efficientdet_lite0.tflite"
errors: No error — just the warning banner persisting
reproduction: File exists at server/efficientdet_lite0.tflite. Run docker compose up --build -d. Open AI settings → warning shown.
started: Just implemented, never worked.

## Eliminated

- hypothesis: .dockerignore excludes the .tflite file
  evidence: No .dockerignore file exists in server/
  timestamp: 2026-04-13T00:00:00Z

- hypothesis: The Dockerfile doesn't COPY the .tflite file
  evidence: docker-compose.yml mounts .:/app for app and worker, so the file is present in those containers. The issue is specifically the webui container.
  timestamp: 2026-04-13T00:00:00Z

## Evidence

- timestamp: 2026-04-13T00:00:00Z
  checked: server/docker-compose.yml — webui service volume mounts
  found: webui only mounts ./media:/app/media and ./webui:/app/webui. No model file mount.
  implication: os.path.isfile("./efficientdet_lite0.tflite") in webui container always returns False.

- timestamp: 2026-04-13T00:00:00Z
  checked: server/webui/admin_panel/views.py line 200-205
  found: model_missing check uses os.path.isfile(os.environ.get("TFLITE_MODEL_PATH", "./efficientdet_lite0.tflite")) — runs inside the webui container filesystem
  implication: File must be accessible at that path inside the webui container for the warning to clear.

- timestamp: 2026-04-13T00:00:00Z
  checked: server/app service volume mounts
  found: app and worker both mount .:/app (full server dir), so efficientdet_lite0.tflite is present at /app/efficientdet_lite0.tflite in those containers.
  implication: Fix is to add the same mount (or a targeted file mount) to the webui service.

## Resolution

root_cause: webui container does not have the model file mounted. The os.path.isfile() check in webui/admin_panel/views.py runs inside the webui container, which only mounts ./media and ./webui — the .tflite file at server/efficientdet_lite0.tflite is never visible to it.
fix: Add volume mount `- ./efficientdet_lite0.tflite:/app/efficientdet_lite0.tflite:ro` to the webui service in docker-compose.yml
verification: pending
files_changed: [server/docker-compose.yml]
