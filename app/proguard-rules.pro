# Keep MediaCodec / MediaMuxer / MediaExtractor reflection-free and intact.
-dontwarn org.jetbrains.annotations.**

# Media3 uses reflection for extractors and renderers.
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Compose runtime is already handled by AGP, but keep view models reachable.
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>();
}

# Data classes used as navigation arguments keep their parameter names.
-keepnames class com.f7developer.videofit.core.model.** { *; }
