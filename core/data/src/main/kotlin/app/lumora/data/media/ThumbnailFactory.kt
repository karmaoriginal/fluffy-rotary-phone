package app.lumora.data.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import app.lumora.model.MediaKind
import java.io.File

/**
 * Generates the small WebP thumbnails used by every grid.
 * Everything is decoded with `inSampleSize` first so we never allocate full-size bitmaps.
 */
object ThumbnailFactory {

    const val MAX_THUMB_DIM = 720

    fun createThumbnail(source: File, kind: MediaKind, dest: File): Boolean {
        val bitmap = runCatching {
            when (kind) {
                MediaKind.IMAGE -> decodeScaled(source, MAX_THUMB_DIM)
                MediaKind.VIDEO -> videoFrame(source)
            }
        }.getOrNull() ?: return false

        return runCatching {
            dest.parentFile?.mkdirs()
            dest.outputStream().use { out ->
                bitmap.compress(compressFormat(), 82, out)
            }
            true
        }.getOrDefault(false).also {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    fun decodeScaled(source: File, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        val longSide = maxOf(bounds.outWidth, bounds.outHeight)
        while (longSide / sample > maxDim * 2) sample *= 2

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(source.absolutePath, options) ?: return null
        val scale = maxDim.toFloat() / maxOf(decoded.width, decoded.height)
        return if (scale < 1f) {
            val width = (decoded.width * scale).toInt().coerceAtLeast(1)
            val height = (decoded.height * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(decoded, width, height, true)
            if (scaled !== decoded) decoded.recycle()
            scaled
        } else {
            decoded
        }
    }

    fun videoFrame(source: File, maxDim: Int = MAX_THUMB_DIM): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(source.absolutePath)
            val frame = retriever.getFrameAtTime(700_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return null
            val scale = maxDim.toFloat() / maxOf(frame.width, frame.height)
            if (scale < 1f) {
                val width = (frame.width * scale).toInt().coerceAtLeast(1)
                val height = (frame.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(frame, width, height, true)
                if (scaled !== frame) frame.recycle()
                scaled
            } else {
                frame
            }
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun compressFormat(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
}
