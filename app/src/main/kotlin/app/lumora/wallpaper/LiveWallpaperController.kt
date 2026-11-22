package app.lumora.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import app.lumora.data.wallpaper.WallpaperApplier
import java.io.File

/** Bridges the UI with the live wallpaper service. */
class LiveWallpaperController(
    private val context: Context,
    private val applier: WallpaperApplier,
) {

    private val component: ComponentName
        get() = ComponentName(context, LumoraWallpaperService::class.java)

    /** Stores the video and returns the intent that opens the system live wallpaper preview. */
    fun prepareAndBuildIntent(video: File, muted: Boolean, fpsLimit: Int): Intent {
        applier.prepareLiveWallpaper(video, muted, fpsLimit)
        return applier.liveWallpaperIntent(component)
    }

    fun chooserIntent(): Intent =
        Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun isActive(): Boolean = applier.isLumoraLiveWallpaperActive(component)

    fun currentVideo(): String? = applier.currentLiveVideoPath()
}
