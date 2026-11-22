package app.lumora.data.wallpaper

import android.content.Context

/**
 * Tiny SharedPreferences-backed config for the live wallpaper engine.
 *
 * The engine runs in its own thread and must be able to read the current video synchronously;
 * DataStore is a Flow-based API, so we mirror the handful of values it needs here.
 */
class LiveWallpaperConfig(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("lumora_live_wallpaper", Context.MODE_PRIVATE)

    var videoPath: String?
        get() = prefs.getString(KEY_VIDEO, null)
        set(value) = prefs.edit().putString(KEY_VIDEO, value).apply()

    var muted: Boolean
        get() = prefs.getBoolean(KEY_MUTED, true)
        set(value) = prefs.edit().putBoolean(KEY_MUTED, value).apply()

    var fpsLimit: Int
        get() = prefs.getInt(KEY_FPS, 60)
        set(value) = prefs.edit().putInt(KEY_FPS, value).apply()

    /** Dim factor applied while the launcher is in overview / not fully visible (battery saver). */
    var lastAppliedAt: Long
        get() = prefs.getLong(KEY_LAST_APPLIED, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_APPLIED, value).apply()

    fun registerListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_VIDEO = "video_path"
        const val KEY_MUTED = "muted"
        const val KEY_FPS = "fps_limit"
        const val KEY_LAST_APPLIED = "last_applied_at"
    }
}
