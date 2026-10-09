# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep Room entities
-keep class com.resq.app.data.entity.** { *; }

# Keep data models
-keep class com.resq.app.data.model.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Kotlin
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}

# Keep generic signatures for Room
-keepattributes Signature
-keepattributes *Annotation*

# Android
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder {
    *** rewind();
}

# Sherpa-ONNX JNI — TTS, ASR, and VAD all live under the same package
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclassmembers class com.k2fsa.sherpa.onnx.** {
    native <methods>;
    <fields>;
    <init>(...);
}

# Keep ONNX Runtime classes referenced by Sherpa
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# MapLibre
-keep class org.maplibre.android.** { *; }
-dontwarn org.maplibre.android.**
-keep class com.mapbox.** { *; }
-dontwarn com.mapbox.**

# PMTiles protocol (if using a separate PMTiles plugin, keep its classes)
-keep class com.protomaps.** { *; }
-dontwarn com.protomaps.**