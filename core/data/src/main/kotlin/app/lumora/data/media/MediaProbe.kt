package app.lumora.data.media

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import app.lumora.model.MediaKind
import java.io.File

data class MediaInfo(
    val mimeType: String,
    val kind: MediaKind,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val durationMs: Long,
    val bitrate: Long,
    val hasAudio: Boolean,
)

/** Supported import formats (mirrors the README). */
object MediaFormats {

    val imageMimeTypes = setOf(
        "image/jpeg",
        "image/jpg",
        "image/png",
        "image/webp",
        "image/heic",
        "image/heif",
        "image/avif",
        "image/gif",
        "image/bmp",
        "image/x-ms-bmp",
    )

    val videoMimeTypes = setOf(
        "video/mp4",
        "video/x-matroska",
        "video/mkv",
        "video/webm",
        "video/quicktime",
        "video/3gpp",
        "video/3gpp2",
        "video/x-msvideo",
        "video/m4v",
    )

    val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "avif", "gif", "bmp")
    val videoExtensions = setOf("mp4", "mkv", "webm", "mov", "3gp", "3gpp", "m4v")

    fun isSupported(mime: String?, extension: String?): MediaKind? {
        val normalizedMime = mime?.lowercase()?.substringBefore(';')?.trim()
        val ext = extension?.lowercase()
        return when {
            normalizedMime != null && normalizedMime in imageMimeTypes -> MediaKind.IMAGE
            normalizedMime != null && normalizedMime in videoMimeTypes -> MediaKind.VIDEO
            ext != null && ext in imageExtensions -> MediaKind.IMAGE
            ext != null && ext in videoExtensions -> MediaKind.VIDEO
            else -> null
        }
    }

    fun kindOfFile(file: File): MediaKind = if (file.extension.lowercase() in videoExtensions) {
        MediaKind.VIDEO
    } else {
        MediaKind.IMAGE
    }

    fun fallbackMime(kind: MediaKind, extension: String?): String {
        val ext = extension?.lowercase()
        return when {
            kind == MediaKind.VIDEO && ext == "webm" -> "video/webm"
            kind == MediaKind.VIDEO && ext == "mkv" -> "video/x-matroska"
            kind == MediaKind.VIDEO && (ext == "mov") -> "video/quicktime"
            kind == MediaKind.VIDEO && (ext == "3gp" || ext == "3gpp") -> "video/3gpp"
            kind == MediaKind.VIDEO -> "video/mp4"
            ext == "png" -> "image/png"
            ext == "webp" -> "image/webp"
            ext == "gif" -> "image/gif"
            ext == "bmp" -> "image/bmp"
            ext == "heic" || ext == "heif" -> "image/heic"
            ext == "avif" -> "image/avif"
            else -> "image/jpeg"
        }
    }
}

/** Reads metadata without touching the UI thread. */
object MediaProbe {

    fun probeFile(file: File, kind: MediaKind, mimeHint: String? = null): MediaInfo {
        val size = file.length()
        return if (kind == MediaKind.VIDEO) {
            probeVideo(file, size, mimeHint)
        } else {
            probeImage(file, size, mimeHint)
        }
    }

    fun probeVideo(file: File, size: Long = file.length(), mimeHint: String? = null): MediaInfo {
        val mmr = MediaMetadataRetriever()
        var width = 0
        var height = 0
        var duration = 0L
        var bitrate = 0L
        var mime = mimeHint
        var hasAudio = false
        try {
            mmr.setDataSource(file.absolutePath)
            width = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            height = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) {
                val swap = width
                width = height
                height = swap
            }
            duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            bitrate = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
            mime = mime ?: mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            hasAudio = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
        } catch (_: Throwable) {
            // Corrupt or unsupported file: keep the zeros and let the caller decide.
        } finally {
            runCatching { mmr.release() }
        }
        if (bitrate <= 0L && duration > 0L) {
            bitrate = (size * 8_000L) / duration
        }
        return MediaInfo(
            mimeType = mime ?: "video/mp4",
            kind = MediaKind.VIDEO,
            width = width,
            height = height,
            sizeBytes = size,
            durationMs = duration,
            bitrate = bitrate,
            hasAudio = hasAudio,
        )
    }

    fun probeImage(file: File, size: Long = file.length(), mimeHint: String? = null): MediaInfo {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeFile(file.absolutePath, options) }
        return MediaInfo(
            mimeType = mimeHint ?: MediaFormats.fallbackMime(MediaKind.IMAGE, file.extension),
            kind = MediaKind.IMAGE,
            width = options.outWidth.coerceAtLeast(0),
            height = options.outHeight.coerceAtLeast(0),
            sizeBytes = size,
            durationMs = 0L,
            bitrate = 0L,
            hasAudio = false,
        )
    }

    /** Queries the display name of a content Uri (Photo Picker / SAF). */
    fun displayName(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull()
    }
}
