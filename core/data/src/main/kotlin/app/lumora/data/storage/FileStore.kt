package app.lumora.data.storage

import android.content.Context
import app.lumora.model.MediaKind
import java.io.File
import java.util.Locale

/**
 * Owns every directory Lumora writes to.
 *
 * - `files/wallpapers`: imported + downloaded wallpapers (source of truth, never evicted).
 * - `files/thumbs`    : small WebP thumbnails (cheap to regenerate).
 * - `cache/downloads` : resumable `.part` files while downloading.
 * - `cache/enhance`   : transient output of the transcoder.
 */
class FileStore(context: Context) {

    private val appContext = context.applicationContext

    val wallpapersDir: File = File(appContext.filesDir, "wallpapers")
    val thumbsDir: File = File(appContext.filesDir, "thumbs")
    val downloadsDir: File = File(appContext.cacheDir, "downloads")
    val enhanceDir: File = File(appContext.cacheDir, "enhance")

    init {
        listOf(wallpapersDir, thumbsDir, downloadsDir, enhanceDir).forEach { dir ->
            if (!dir.exists()) dir.mkdirs()
        }
    }

    fun newWallpaperFile(baseName: String, extension: String): File {
        val safeBase = sanitize(baseName).ifBlank { "wallpaper" }
        val safeExt = sanitize(extension).ifBlank { "bin" }
        val stamp = System.currentTimeMillis()
        var candidate = File(wallpapersDir, "${stamp}_$safeBase.$safeExt")
        var index = 1
        while (candidate.exists()) {
            candidate = File(wallpapersDir, "${stamp}_${index}_$safeBase.$safeExt")
            index++
        }
        return candidate
    }

    fun thumbFileFor(wallpaperFile: File): File =
        File(thumbsDir, "${wallpaperFile.nameWithoutExtension}_thumb.webp")

    fun partFileFor(id: String): File = File(downloadsDir, "${sanitize(id)}.part")

    fun finalFileFor(id: String, extension: String): File =
        File(wallpapersDir, "${sanitize(id)}.${sanitize(extension).ifBlank { "bin" }}")

    fun enhanceOutputFor(source: File): File =
        File(enhanceDir, "${source.nameWithoutExtension}_enhanced.mp4")

    fun libraryBytes(): Long = sizeOf(wallpapersDir) + sizeOf(thumbsDir)

    fun cacheBytes(): Long = sizeOf(downloadsDir) + sizeOf(enhanceDir)

    fun clearDownloads(): Long {
        val freed = sizeOf(downloadsDir)
        downloadsDir.listFiles()?.forEach { it.deleteRecursively() }
        return freed
    }

    fun clearEnhanceCache(): Long {
        val freed = sizeOf(enhanceDir)
        enhanceDir.listFiles()?.forEach { it.deleteRecursively() }
        return freed
    }

    fun clearThumbs(): Long {
        val freed = sizeOf(thumbsDir)
        thumbsDir.listFiles()?.forEach { it.deleteRecursively() }
        return freed
    }

    /** Deletes the oldest cache files until the cache is under [limitBytes]. */
    fun trimCache(limitBytes: Long): Long {
        var current = cacheBytes()
        if (current <= limitBytes) return 0L
        var freed = 0L
        val files = (downloadsDir.listFiles()?.toList().orEmpty() +
            enhanceDir.listFiles()?.toList().orEmpty())
            .filter { it.isFile }
            .sortedBy { it.lastModified() }
        for (file in files) {
            if (current <= limitBytes) break
            val length = file.length()
            if (file.delete()) {
                freed += length
                current -= length
            }
        }
        return freed
    }

    fun deleteWithThumbnail(file: File) {
        runCatching { thumbFileFor(file).delete() }
        runCatching { file.delete() }
    }

    fun deleteQuietly(file: File?) {
        if (file != null) runCatching { file.delete() }
    }

    private fun sizeOf(dir: File): Long =
        if (!dir.exists()) 0L else dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun sanitize(value: String): String = value
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9._-]"), "-")
        .trim('-', '.')
        .take(48)

    companion object {
        fun extensionFor(kind: MediaKind, mimeType: String): String = when {
            mimeType.contains("webm") -> "webm"
            mimeType.contains("matroska") || mimeType.contains("mkv") -> "mkv"
            mimeType.contains("quicktime") || mimeType.contains("mov") -> "mov"
            mimeType.contains("3gpp") -> "3gp"
            kind == MediaKind.VIDEO -> "mp4"
            mimeType.contains("png") -> "png"
            mimeType.contains("webp") -> "webp"
            mimeType.contains("gif") -> "gif"
            mimeType.contains("bmp") -> "bmp"
            mimeType.contains("heic") || mimeType.contains("heif") -> "heic"
            mimeType.contains("avif") -> "avif"
            else -> "jpg"
        }
    }
}
