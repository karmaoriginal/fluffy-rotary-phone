package app.lumora.wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
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
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultLoadControl
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

    private inner class LumoraEngine : Engine(),
        SharedPreferences.OnSharedPreferenceChangeListener {

        private val handler = Handler(Looper.getMainLooper())
        private val config = LiveWallpaperConfig(applicationContext)
        private var player: ExoPlayer? = null
        private var holder: SurfaceHolder? = null
        private var visible = false
        private var receiversRegistered = false
        private var loadedPath: String? = null

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
            drawPlaceholder()
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
            syncPlayback()
        }

        override fun onSurfaceChanged(
            surfaceHolder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int,
        ) {
            super.onSurfaceChanged(surfaceHolder, format, width, height)
            applyFrameRateLimit(surfaceHolder.surface)
        }

        override fun onSurfaceDestroyed(surfaceHolder: SurfaceHolder) {
            player?.setVideoSurface(null)
            holder = null
            super.onSurfaceDestroyed(surfaceHolder)
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            handler.post {
                player?.volume = if (config.muted) 0f else 1f
                applyFrameRateLimit(holder?.surface)
                syncPlayback()
            }
        }

        override fun onDestroy() {
            releasePlayer()
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
                if (!visible && player == null) drawPlaceholder()
                return
            }

            val file = File(path!!)
            if (!file.exists()) {
                player?.pause()
                drawPlaceholder()
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
            }
            exo.play()
        }

        private fun ensurePlayer(surfaceHolder: SurfaceHolder): ExoPlayer {
            player?.let { return it }
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(2_000, 8_000, 500, 1_000)
                .build()
            val exo = ExoPlayer.Builder(applicationContext)
                .setLoadControl(loadControl)
                .setWakeMode(C.WAKE_MODE_NONE)
                .setHandleAudioBecomingNoisy(false)
                .build()
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

        private fun drawPlaceholder() {
            val surfaceHolder = holder ?: return
            runCatching {
                val canvas: Canvas = surfaceHolder.lockCanvas() ?: return
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
                surfaceHolder.unlockCanvasAndPost(canvas)
            }
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
