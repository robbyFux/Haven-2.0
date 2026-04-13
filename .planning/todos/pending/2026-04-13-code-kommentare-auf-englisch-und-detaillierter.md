---
created: 2026-04-13T11:50:21.608Z
title: Code-Kommentare auf Englisch standardisieren und ausbauen
area: general
files: []
---

## Problem

Die Codebase hat gemischte Kommentar-Sprache (Deutsch/Englisch). KDoc-Klassen-Dokumentation tendiert zu Englisch, Inline-Algorithmus-Kommentare zu Deutsch. Außerdem sind viele algorithmisch interessante Stellen (Sensorfusion, pHash, EMA, Noise-Floor-Kalibrierung) zu wenig kommentiert — der Code erklärt das „Was", aber nicht immer das „Warum" und „Wie".

## Solution

1. Alle bestehenden deutschen Inline-Kommentare ins Englische übersetzen (konsistent mit class-level KDoc).
2. Algorithmisch dichte Stellen mit erklärenden Kommentaren ausbauen:
   - `SensorFusionEngine.kt` — Complementary Filter, alpha-Wahl
   - `LightMonitor.kt` — EMA alpha, EMA-Snap-Logik
   - `FusedMotionMonitor.kt` — Noise-Floor 90. Perzentil, Warmup-Guard
   - `CameraAnalyzer.kt` — 3-Stufen-Pipeline, Luma-Crop, pHash-Bestätigung
   - `PerceptualHashDetector.kt` — aHash-Algorithmus, Hamming-Distanz
   - `HavenObjectDetector.kt` — MediaPipe-Init, Graceful Degradation
3. KDoc `@param`/`@return`-Tags auf public functions wo fehlend ergänzen.
