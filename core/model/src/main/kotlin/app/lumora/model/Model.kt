package app.lumora.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Kind of wallpaper Lumora can handle. */
@Serializable
enum class MediaKind {
    @SerialName("image")
    IMAGE,

    @SerialName("video")
    VIDEO,
}

/** Where a local wallpaper came from. */
enum class WallpaperOrigin {
    IMPORT,
    STORE,
}

/** Which target the wallpaper is applied to. */
enum class WallpaperTarget {
    HOME,
    LOCK,
    BOTH,
}

/** Theme preference. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/** Target height used by the (optional) enhancement pipeline. */
enum class EnhanceTarget(val height: Int, val label: String) {
    HD_1080(1080, "1080p"),
    QHD_1440(1440, "1440p"),
}

/** Frame rate ceiling for the live wallpaper. */
enum class FrameRateLimit(val fps: Int) {
    FPS_30(30),
    FPS_60(60),
}

// ---------------------------------------------------------------------------
// Store (remote catalog)
// ---------------------------------------------------------------------------

@Serializable
data class CatalogCategory(
    val id: String,
    val name: String,
    val accent: String = "#7B5CFF",
    val icon: String = "",
)

@Serializable
data class CatalogItem(
    val id: String,
    val title: String,
    val author: String = "Lumora Studio",
    val kind: MediaKind = MediaKind.IMAGE,
    val categories: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val width: Int = 1080,
    val height: Int = 1920,
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val thumbUrl: String = "",
    val previewUrl: String = "",
    val fullUrl: String,
    val license: String = "CC0-1.0",
    val licenseUrl: String = "https://creativecommons.org/publicdomain/zero/1.0/",
    val sourceUrl: String = "",
    val addedAt: String = "",
    val downloads: Int = 0,
    val featured: Boolean = false,
)

@Serializable
data class Catalog(
    val version: Int = 1,
    val updatedAt: String = "",
    val categories: List<CatalogCategory> = emptyList(),
    val items: List<CatalogItem> = emptyList(),
)

// ---------------------------------------------------------------------------
// Local library
// ---------------------------------------------------------------------------

data class LocalWallpaper(
    val id: Long,
    val name: String,
    val filePath: String,
    val kind: MediaKind,
    val origin: WallpaperOrigin,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val durationMs: Long,
    val category: String,
    val author: String,
    val license: String,
    val addedAt: Long,
    val lastAppliedAt: Long?,
    val applyCount: Int,
    val favorite: Boolean,
    val thumbPath: String?,
    val enhanced: Boolean,
    val sourceId: String,
) {
    val isVideo: Boolean get() = kind == MediaKind.VIDEO
}

data class LibraryStats(
    val images: Int = 0,
    val videos: Int = 0,
    val totalBytes: Long = 0L,
    val cacheBytes: Long = 0L,
)

// ---------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val amoled: Boolean = true,
    val muteVideo: Boolean = true,
    val frameRateLimit: FrameRateLimit = FrameRateLimit.FPS_60,
    val wifiOnly: Boolean = false,
    val autoEnhance: Boolean = true,
    val cacheLimitMb: Int = 256,
    val enhanceTarget: EnhanceTarget = EnhanceTarget.HD_1080,
    /** Optional AI upscaler backend, disabled by default (see docs/OPTIONAL_FFMPEG.md). */
    val aiUpscaler: Boolean = false,
    val onboardingDone: Boolean = false,
)
