---
slug: mediapipe-protobuf-reflection
status: resolved
trigger: "release.sh erzeugtes APK: RuntimeException: Field typeUrl_ for com.google.protobuf.Any not found. Known fields are [public static final com.google.protobuf.Any com.google.protobuf.Any.g, public static volatile com.google.protobuf.H com.google.protobuf.Any.h]"
created: 2026-05-21
updated: 2026-05-21
---

## Symptoms

- App: Haven 2.0 v1.0.6 (9), Pixel 7 Pro, Android 16
- TFLite model: not available
- Detection mode: MOTION_ONLY (should be MOTION_AND_CAMERA with AI)
- Error in log: `RuntimeException: Field typeUrl_ for com.google.protobuf.Any not found. Known fields are [public static final com.google.protobuf.Any com.google.protobuf.Any.g, public static volatile com.google.protobuf.H com.google.protobuf.Any.h]`
- APK built via release.sh with R8 minification (isMinifyEnabled = true)
- MediaPipe Tasks Vision 0.10.29 used for ObjectDetector

## Context

This is the 4th R8/ProGuard issue in a series for this project:
1. MediaPipe classes stripped → fixed with `-keep class com.google.mediapipe.**`
2. Flogger renamed → `ExceptionInInitializerError` → fixed with `-keep class com.google.common.flogger.**`
3. Now Protobuf field renamed → `RuntimeException: Field typeUrl_ not found`

## Current Focus

hypothesis: "R8 renames com.google.protobuf.Any fields (typeUrl_ → g, value_ → h) because Protobuf is a transitive dependency of MediaPipe not covered by any ProGuard -keep rule. Protobuf uses reflection to access fields by their original names."
test: "Check R8 mapping.txt for com.google.protobuf.Any field mappings"
expecting: "typeUrl_ renamed to g, value_ renamed to h in mapping.txt"
next_action: "confirmed — root cause identified"

## Evidence

- timestamp: 2026-05-21T00:00:00Z
  source: app/build/outputs/mapping/release/mapping.txt (line 434503-434531)
  finding: |
    `com.google.protobuf.Any -> com.google.protobuf.Any:` — class name preserved (kept by transitive reference),
    but static fields are renamed:
      `com.google.protobuf.Any DEFAULT_INSTANCE -> g`
      `com.google.protobuf.Parser PARSER -> h`
    The error message shows `Any.g` and `Any.h` as the only "known fields", which matches these
    two renamed static fields exactly. Instance fields `typeUrl_` (String) and `value_` (ByteString)
    are NOT listed in the mapping at all — R8 omits instance fields from mapping when they are
    accessed only via reflection (or when they are inlined). Their absence from mapping while the
    reflection lookup fails at runtime confirms R8 has stripped or renamed them.

- timestamp: 2026-05-21T00:00:01Z
  source: app/proguard-rules.pro
  finding: |
    No `-keep class com.google.protobuf.**` rule present. The file contains keeps for:
    MediaPipe (`com.google.mediapipe.**`), Flogger (`com.google.common.flogger.**`),
    Room, Hilt, Kotlin coroutines, CameraX, TFLite — but NOT for `com.google.protobuf`.

- timestamp: 2026-05-21T00:00:02Z
  source: app/build/outputs/mapping/release/mapping.txt (protobuf class count)
  finding: |
    26 of 119 `com.google.protobuf.*` classes are renamed to single-letter names (e.g. `a`, `b`, `c`…`z`).
    Examples: `AbstractMessageLite -> c`, `AbstractMessageLite$Builder -> b`,
    `Any$Builder -> f`, `AnyOrBuilder -> g`, `ByteString -> n`, `CodedInputStream -> r`.
    This confirms R8 is aggressively renaming/shrinking the entire Protobuf Lite runtime.

## Resolution

root_cause: |
  `com.google.protobuf.*` (Protobuf Lite, transitive dependency of MediaPipe Tasks Vision 0.10.29)
  is not protected by any ProGuard `-keep` rule. R8 renames its fields and classes.
  `com.google.protobuf.Any` uses Java reflection internally to look up instance fields
  (`typeUrl_`, `value_`) by their source-code names via `Class.getDeclaredField()`. After R8
  renames/strips those fields, the reflection call throws `RuntimeException: Field typeUrl_ for
  com.google.protobuf.Any not found`. The two "known fields" in the error (`g`, `h`) are the
  two static fields R8 preserved under renamed identifiers.

fix: |
  Add to `app/proguard-rules.pro`:

  ```
  # Protobuf Lite (transitive via MediaPipe) — uses reflection on own fields by source name.
  # R8 must NOT rename or strip any protobuf class members.
  -keep class com.google.protobuf.** { *; }
  -dontwarn com.google.protobuf.**
  ```

  This is the minimal, correct fix. A narrower rule (e.g. `-keepclassmembers`) would be
  insufficient because R8 also renames class names themselves (26 classes renamed), and
  Protobuf's schema registration uses class names as keys.
