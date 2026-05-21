# Haven 2.0 ProGuard rules

# Keep Room entities
-keep class org.havenapp.main.storage.entity.** { *; }

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep CameraX
-keep class androidx.camera.** { *; }

# TFLite runtime (wird intern von MediaPipe genutzt)
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.**

# MediaPipe Tasks Vision (ObjectDetector, BitmapImageBuilder, BaseOptions, RunningMode)
# Nutzt JNI und Reflection — vollständige Package-Erhaltung nötig
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# Flogger (transitiv via MediaPipe) — darf NICHT umbenannt werden.
# Flogger.forEnclosingClass() sucht sich selbst per Stack-Walk;
# jede Umbenennung durch R8 bricht diesen Mechanismus (ExceptionInInitializerError).
-keep class com.google.common.flogger.** { *; }
-dontwarn com.google.common.flogger.**

# Protobuf Lite (transitiv via MediaPipe) — nutzt Reflection auf eigene Felder per Quellname.
# R8 darf weder Klassennamen noch Felder umbenennen (26/119 Klassen betroffen lt. mapping.txt).
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**
