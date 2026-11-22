package app.lumora.data.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import app.lumora.data.media.ThumbnailFactory
import app.lumora.model.WallpaperTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Applies static wallpapers with a real crop/position transform and prepares the live
 * wallpaper (video) configuration.
 */
class WallpaperApplier(
    private val context: Context,
    private val liveConfig: LiveWallpaperConfig,
) {

    private val wallpaperManager: WallpaperManager = WallpaperManager.getInstance(context)

    suspend fun applyImage(
        file: File,
        target: WallpaperTarget,
        scale: Float = 1f,
        offsetXFraction: Float = 0f,
        offsetYFraction: Float = 0f,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val metrics = context.resources.displayMetrics
            val screenWidth = metrics.widthPixels.coerceAtLeast(1)
            val screenHeight = metrics.heightPixels.coerceAtLeast(1)
            val source = decodeSampled(file, screenWidth, screenHeight)
                ?: error("No se pudo decodificar la imagen")
            val composed = compose(
                source = source,
                width = screenWidth,
                height = screenHeight,
                scale = scale,
                offsetX = offsetXFraction,
                offsetY = offsetYFraction,
            )
            val which = when (target) {
                WallpaperTarget.HOME -> WallpaperManager.FLAG_SYSTEM
                WallpaperTarget.LOCK -> WallpaperManager.FLAG_LOCK
                WallpaperTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            }
            wallpaperManager.setBitmap(composed, null, true, which)
            if (composed !== source) composed.recycle()
            source.recycle()
        }
    }

    /**
     * Stores the video used by [app.lumora.wallpaper.LumoraWallpaperService] together with a
     * poster frame.
     *
     * The poster is what the engine paints until the decoder produces its first frame, and it is
     * also the fallback if playback fails. Without it the launcher would show an empty surface
     * (which users read as "solo sale un color").
     */
    suspend fun prepareLiveWallpaper(
        video: File,
        muted: Boolean,
        fpsLimit: Int,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(video.exists()) { "El archivo de video ya no existe" }
            liveConfig.posterPath = extractPoster(video)?.absolutePath
            liveConfig.lastError = null
            liveConfig.videoPath = video.absolutePath
            liveConfig.muted = muted
            liveConfig.fpsLimit = fpsLimit
            liveConfig.lastAppliedAt = System.currentTimeMillis()
        }
    }

    /** Extracts frame 0 (or the closest keyframe) as a screen-sized JPEG poster. */
    private fun extractPoster(video: File): File? = runCatching {
        val metrics = context.resources.displayMetrics
        val frame = ThumbnailFactory.videoFrame(
            video,
            maxOf(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(720),
        ) ?: return@runCatching null
        val width = metrics.widthPixels.coerceAtLeast(1)
        val height = metrics.heightPixels.coerceAtLeast(1)
        val cover = maxOf(width.toFloat() / frame.width, height.toFloat() / frame.height)
        val scaledWidth = (frame.width * cover).toInt().coerceAtLeast(1)
        val scaledHeight = (frame.height * cover).toInt().coerceAtLeast(1)
        val coverBitmap = Bitmap.createScaledBitmap(frame, scaledWidth, scaledHeight, true)
        val left = ((scaledWidth - width) / 2).coerceAtLeast(0)
        val top = ((scaledHeight - height) / 2).coerceAtLeast(0)
        val cropped = Bitmap.createBitmap(
            coverBitmap,
            left,
            top,
            width.coerceAtMost(scaledWidth),
            height.coerceAtMost(scaledHeight),
        )
        val output = File(context.filesDir, "live_poster.jpg")
        FileOutputStream(output).use { stream ->
            cropped.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        }
        if (cropped !== coverBitmap) cropped.recycle()
        if (coverBitmap !== frame) coverBitmap.recycle()
        frame.recycle()
        output
    }.getOrNull()

    /** True when any live wallpaper (Lumora or a third party) owns the home screen. */
    fun anyLiveWallpaperActive(): Boolean =
        runCatching { wallpaperManager.wallpaperInfo != null }.getOrDefault(false)

    /** Human readable name of the active live wallpaper, if any. */
    fun activeLiveWallpaperName(): String? {
        val info = runCatching { wallpaperManager.wallpaperInfo }.getOrNull() ?: return null
        val label = runCatching { info.loadLabel(context.packageManager)?.toString() }.getOrNull()
        return label?.takeIf { it.isNotBlank() } ?: info.packageName
    }

    /** Snapshot shown in Ajustes → Fondos animados. */
    fun liveDiagnostics(): LiveWallpaperDiagnostics {
        val path = liveConfig.videoPath
        val video = path?.let { File(it) }
        return LiveWallpaperDiagnostics(
            activeWallpaper = if (anyLiveWallpaperActive()) {
                activeLiveWallpaperName() ?: "Desconocido"
            } else {
                null
            },
            videoName = video?.name,
            videoExists = video?.exists() == true,
            videoBytes = if (video?.exists() == true) video.length() else 0L,
            posterAvailable = liveConfig.posterPath?.let { File(it).exists() } == true,
            lastError = liveConfig.lastError,
        )
    }

    fun liveWallpaperIntent(serviceComponent: ComponentName): Intent =
        Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, serviceComponent)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun isLumoraLiveWallpaperActive(serviceComponent: ComponentName?): Boolean {
        val info = runCatching { wallpaperManager.wallpaperInfo }.getOrNull() ?: return false
        val current = runCatching { info.component }.getOrNull() ?: return false
        return serviceComponent != null && current.packageName == serviceComponent.packageName &&
            current.className == serviceComponent.className
    }

    fun currentLiveVideoPath(): String? = liveConfig.videoPath

    private fun decodeSampled(file: File, targetWidth: Int, targetHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetWidth &&
            bounds.outHeight / (sample * 2) >= targetHeight
        ) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun compose(
        source: Bitmap,
        width: Int,
        height: Int,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
    ): Bitmap {
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.BLACK)
        val cover = maxOf(width.toFloat() / source.width, height.toFloat() / source.height)
        val finalScale = cover * scale.coerceIn(1f, 2.5f)
        val drawWidth = source.width * finalScale
        val drawHeight = source.height * finalScale
        val slackX = (drawWidth - width) / 2f
        val slackY = (drawHeight - height) / 2f
        val left = -slackX + offsetX.coerceIn(-1f, 1f) * slackX
        val top = -slackY + offsetY.coerceIn(-1f, 1f) * slackY
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(
            source,
            null,
            RectF(left, top, left + drawWidth, top + drawHeight),
            paint,
        )
        return output
    }
}


/** Debug snapshot of the live wallpaper state (rendered in Ajustes). */
data class LiveWallpaperDiagnostics(
    val activeWallpaper: String?,
    val videoName: String?,
    val videoExists: Boolean,
    val videoBytes: Long,
    val posterAvailable: Boolean,
    val lastError: String?,
)
