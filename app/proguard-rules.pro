# =============================================================================
# Lumora - R8 / ProGuard rules
# =============================================================================

# Keep source file names / line numbers for readable crash reports while still
# allowing full obfuscation of identifiers.
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# -----------------------------------------------------------------------------
# Kotlin / coroutines
# -----------------------------------------------------------------------------
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# -----------------------------------------------------------------------------
# kotlinx.serialization (catalog.json model)
# -----------------------------------------------------------------------------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class app.lumora.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
    <fields>;
}
-keepclasseswithmembers class app.lumora.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class app.lumora.**$$serializer { *; }
-keep,includedescriptorclasses class app.lumora.model.** { *; }

# -----------------------------------------------------------------------------
# Room
# -----------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# -----------------------------------------------------------------------------
# WorkManager: workers are instantiated reflectively
# -----------------------------------------------------------------------------
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class app.lumora.work.** { *; }

# -----------------------------------------------------------------------------
# Media3 / ExoPlayer
# -----------------------------------------------------------------------------
-dontwarn androidx.media3.**
-keep class androidx.media3.exoplayer.ExoPlayer { *; }
-keepclassmembers class androidx.media3.** { <init>(...); }

# -----------------------------------------------------------------------------
# OkHttp / Okio
# -----------------------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# -----------------------------------------------------------------------------
# Coil (reflection-free, but keeps its decoder registry predictable)
# -----------------------------------------------------------------------------
-dontwarn coil.**

# -----------------------------------------------------------------------------
# WallpaperService + app entry points are referenced from the manifest.
# -----------------------------------------------------------------------------
-keep class app.lumora.MainActivity { *; }
-keep class app.lumora.LumoraApplication { *; }
-keep class app.lumora.wallpaper.** { *; }
