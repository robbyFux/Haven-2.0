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

# TFLite Task Library
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.task.** { *; }
-keep class org.tensorflow.lite.support.** { *; }
-dontwarn org.tensorflow.**
