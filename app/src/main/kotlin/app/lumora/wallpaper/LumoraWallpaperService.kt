package app.lumora.wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.service.wallpaper.WallpaperService
import android.view.Surface
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import app.lumora.data.wallpaper.LiveWallpaperConfig
import java.io.File

/**
 * Live wallpaper engine backed by ExoPlayer.
 *
 * Battery first:
 *  - playback only while the launcher is visible and the screen is on;
 *  - paused in power-save mode;
 *  - hardware decoding, gapless looping (`REPEAT_MODE_ONE`), optional mute;
 *  - the display refresh rate is capped to the user's 30/60 fps preference.
 */
class LumoraWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = LumoraEngine()

    override fun onDestroy() {
        super.onDestroy()
    }

    private companion object {
        const val MAX_RETRIES = 2
        const val FRAME_WATCHDOG_MS = 5_000L
    }

    private inner class LumoraEngine : Engine(),
        SharedPreferences.OnSharedPreferenceChangeListener {

        private val handler = Handler(Looper.getMainLooper())
        private val config = LiveWallpaperConfig(applicationContext)
        private var player: ExoPlayer? = null
        private var holder: SurfaceHolder? = null
        private var visible = false
        private var receiversRegistered = false
        private var loadedPath: String? = null
        private var posterBitmap: Bitmap? = null
        private var posterLoadedFrom: String? = null
        private var retries = 0
        private var renderedFirstFrame = false
        private var watchdogArmedFor: ExoPlayer? = null

        private val screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                handler.post { syncPlayback() }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setOffsetNotificationsEnabled(false)
            setTouchEventsEnabled(false)
            config.registerListener(this)
            registerReceivers()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            syncPlayback()
        }

        override fun onSurfaceCreated(surfaceHolder: SurfaceHolder) {
            super.onSurfaceCreated(surfaceHolder)
            holder = surfaceHolder
            player?.setVideoSurface(surfaceHolder.surface)
            applyFrameRateLimit(surfaceHolder.surface)
            // Paint the poster immediately so the launcher never shows an empty surface.
            drawPoster()
            handler.postDelayed({ syncPlayback() }, 80)
        }

        override fun onSurfaceChanged(
            surfaceHolder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int,
        ) {
            super.onSurfaceChanged(surfaceHolder, format, width, height)
            applyFrameRateLimit(surfaceHolder.surface)
            handler.postDelayed({ syncPlayback() }, 100)
        }

        override fun onSurfaceDestroyed(surfaceHolder: SurfaceHolder) {
            player?.setVideoSurface(null)
            holder = null
            super.onSurfaceDestroyed(surfaceHolder)
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            // Our own bookkeeping writes must not re-enter playback synchronisation.
            if (key == "last_error" || key == "last_applied_at") return
            handler.post {
                if (key == null || key == "poster_path") {
                    posterLoadedFrom = null
                    posterBitmap?.recycle()
                    posterBitmap = null
                }
                retries = 0
                player?.volume = if (config.muted) 0f else 1f
                applyFrameRateLimit(holder?.surface)
                syncPlayback()
            }
        }

        override fun onDestroy() {
            releasePlayer()
            posterBitmap?.recycle()
            posterBitmap = null
            unregisterReceivers()
            config.unregisterListener(this)
            handler.removeCallbacksAndMessages(null)
            super.onDestroy()
        }

        private fun syncPlayback() {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            val interactive = powerManager?.isInteractive ?: true
            val powerSave = powerManager?.isPowerSaveMode ?: false
            val surfaceHolder = holder
            val path = config.videoPath

            val shouldPlay = visible && interactive && !powerSave &&
                !path.isNullOrBlank() && surfaceHolder != null

            if (!shouldPlay) {
                player?.pause()
                if (!visible && player == null) drawPoster()
                return
            }

            val file = File(path!!)
            if (!file.exists()) {
                player?.pause()
                config.lastError = "El video ya no existe: ${file.name}"
                drawPoster()
                return
            }

            val exo = ensurePlayer(surfaceHolder!!)
            exo.volume = if (config.muted) 0f else 1f
            if (loadedPath != file.absolutePath) {
                val dataSourceFactory = DefaultDataSource.Factory(applicationContext)
                val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(Uri.fromFile(file)))
                exo.setMediaSource(mediaSource)
                exo.prepare()
                loadedPath = file.absolutePath
                renderedFirstFrame = false
            }
            exo.play()
            armFrameWatchdog(exo)
        }

        private val playerListener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                retries = 0
                renderedFirstFrame = true
                if (config.lastError != null) config.lastError = null
            }

            override fun onPlayerError(error: PlaybackException) {
                config.lastError = "${error.errorCodeName}: ${error.message.orEmpty().take(140)}"
                retries += 1
                if (retries <= MAX_RETRIES) {
                    handler.postDelayed({ restartPlayback() }, 900L * retries)
                } else {
                    releasePlayer()
                    drawPoster()
                }
            }
        }

        /**
         * Some launchers hand the engine a surface that never receives decoded frames. If no frame
         * arrives, drop the player and keep the poster so the user still sees their wallpaper.
         */
        private fun armFrameWatchdog(exo: ExoPlayer) {
            if (watchdogArmedFor === exo) return
            watchdogArmedFor = exo
            handler.postDelayed({
                if (player === exo && !renderedFirstFrame && visible) {
                    config.lastError = "Sin imagen tras 5 s (codec: ${codecLabel(exo)})"
                    releasePlayer()
                    drawPoster()
                }
            }, FRAME_WATCHDOG_MS)
        }

        private fun codecLabel(exo: ExoPlayer): String = runCatching {
            exo.videoFormat?.sampleMimeType ?: "desconocido"
        }.getOrDefault("desconocido")

        private fun restartPlayback() {
            releasePlayer()
            syncPlayback()
        }

        private fun ensurePlayer(surfaceHolder: SurfaceHolder): ExoPlayer {
            player?.let { return it }
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(2_000, 8_000, 500, 1_000)
                .build()
            // Fall back to a software decoder when the hardware decoder refuses the
            // wallpaper surface (some OEM decoders only render to app windows).
            val renderers = DefaultRenderersFactory(applicationContext)
                .setEnableDecoderFallback(true)
            val exo = ExoPlayer.Builder(applicationContext, renderers)
                .setLoadControl(loadControl)
                .setWakeMode(C.WAKE_MODE_NONE)
                .setHandleAudioBecomingNoisy(false)
                .build()
            exo.addListener(playerListener)
            exo.repeatMode = Player.REPEAT_MODE_ONE
            exo.volume = if (config.muted) 0f else 1f
            exo.playWhenReady = false
            exo.setVideoSurface(surfaceHolder.surface)
            applyFrameRateLimit(surfaceHolder.surface)
            player = exo
            return exo
        }

        private fun applyFrameRateLimit(surface: Surface?) {
            if (surface == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
            runCatching {
                surface.setFrameRate(
                    config.fpsLimit.toFloat(),
                    Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                )
            }
        }

        /**
         * Paints the stored poster frame (or, if there is none, a soft brand gradient) on the
         * wallpaper surface. This is the visible content until the decoder renders a frame, so a
         * failing decoder degrades to the first frame of the video instead of a flat colour.
         */
        private fun drawPoster() {
            val surfaceHolder = holder ?: return
            val bitmap = posterBitmap()
            runCatching {
                val canvas: Canvas = surfaceHolder.lockCanvas() ?: return
                if (bitmap != null && !bitmap.isRecycled) {
                    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
                    val cover = maxOf(
                        canvas.width.toFloat() / bitmap.width,
                        canvas.height.toFloat() / bitmap.height,
                    )
                    val drawWidth = bitmap.width * cover
                    val drawHeight = bitmap.height * cover
                    val left = (canvas.width - drawWidth) / 2f
                    val top = (canvas.height - drawHeight) / 2f
                    canvas.drawBitmap(
                        bitmap,
                        null,
                        RectF(left, top, left + drawWidth, top + drawHeight),
                        paint,
                    )
                } else {
                    drawFallbackGradient(canvas)
                }
                surfaceHolder.unlockCanvasAndPost(canvas)
            }
        }

        private fun posterBitmap(): Bitmap? {
            val path = config.posterPath ?: return null
            if (path == posterLoadedFrom) return posterBitmap
            posterBitmap?.recycle()
            posterBitmap = runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
            posterLoadedFrom = path
            return posterBitmap
        }

        private fun drawFallbackGradient(canvas: Canvas) {
            val paint = Paint().apply {
                shader = LinearGradient(
                    0f,
                    0f,
                    0f,
                    canvas.height.toFloat(),
                    intArrayOf(
                        Color.parseColor("#101B33"),
                        Color.parseColor("#241040"),
                        Color.parseColor("#08080D"),
                    ),
                    null,
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRect(
                0f,
                0f,
                canvas.width.toFloat(),
                canvas.height.toFloat(),
                paint,
            )
        }

        private fun registerReceivers() {
            if (receiversRegistered) return
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            ContextCompat.registerReceiver(
                this@LumoraWallpaperService,
                screenReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            receiversRegistered = true
        }

        private fun unregisterReceivers() {
            if (!receiversRegistered) return
            runCatching { unregisterReceiver(screenReceiver) }
            receiversRegistered = false
        }

        private fun releasePlayer() {
            renderedFirstFrame = false
            watchdogArmedFor = null
            player?.let { exo ->
                runCatching {
                    exo.setVideoSurface(null)
                    exo.stop()
                    exo.release()
                }
            }
            player = null
            loadedPath = null
        }
    }
}
