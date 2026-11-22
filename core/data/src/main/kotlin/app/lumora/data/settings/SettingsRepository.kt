package app.lumora.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.lumora.data.wallpaper.LiveWallpaperConfig
import app.lumora.model.AppSettings
import app.lumora.model.EnhanceTarget
import app.lumora.model.FrameRateLimit
import app.lumora.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "lumora_settings")

private object Keys {
    val THEME = stringPreferencesKey("theme_mode")
    val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    val AMOLED = booleanPreferencesKey("amoled")
    val MUTE_VIDEO = booleanPreferencesKey("mute_video")
    val FRAME_RATE = stringPreferencesKey("frame_rate_limit")
    val WIFI_ONLY = booleanPreferencesKey("wifi_only")
    val AUTO_ENHANCE = booleanPreferencesKey("auto_enhance")
    val CACHE_LIMIT = intPreferencesKey("cache_limit_mb")
    val ENHANCE_TARGET = stringPreferencesKey("enhance_target")
    val AI_UPSCALER = booleanPreferencesKey("ai_upscaler")
    val ONBOARDING = booleanPreferencesKey("onboarding_done")
}

/**
 * Single source of truth for user preferences (DataStore) plus a lightweight mirror in
 * SharedPreferences so the [android.service.wallpaper.WallpaperService] engine can read the
 * playback configuration synchronously from its own thread.
 */
class SettingsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val store = appContext.settingsDataStore
    private val liveConfig = LiveWallpaperConfig(appContext)

    val settings: Flow<AppSettings> = store.data.map { it.toSettings() }

    suspend fun edit(block: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val updated = block(prefs.toSettings())
            prefs[Keys.THEME] = updated.themeMode.name
            prefs[Keys.DYNAMIC_COLOR] = updated.dynamicColor
            prefs[Keys.AMOLED] = updated.amoled
            prefs[Keys.MUTE_VIDEO] = updated.muteVideo
            prefs[Keys.FRAME_RATE] = updated.frameRateLimit.name
            prefs[Keys.WIFI_ONLY] = updated.wifiOnly
            prefs[Keys.AUTO_ENHANCE] = updated.autoEnhance
            prefs[Keys.CACHE_LIMIT] = updated.cacheLimitMb
            prefs[Keys.ENHANCE_TARGET] = updated.enhanceTarget.name
            prefs[Keys.AI_UPSCALER] = updated.aiUpscaler
            prefs[Keys.ONBOARDING] = updated.onboardingDone
        }
        // Mirror the values the wallpaper engine needs (synchronous read from its own thread).
        val updated = store.data.first().toSettings()
        liveConfig.muted = updated.muteVideo
        liveConfig.fpsLimit = updated.frameRateLimit.fps
    }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it.copy(themeMode = mode) }

    suspend fun setDynamicColor(enabled: Boolean) = edit { it.copy(dynamicColor = enabled) }

    suspend fun setAmoled(enabled: Boolean) = edit { it.copy(amoled = enabled) }

    suspend fun setMuteVideo(enabled: Boolean) = edit { it.copy(muteVideo = enabled) }

    suspend fun setFrameRateLimit(limit: FrameRateLimit) = edit { it.copy(frameRateLimit = limit) }

    suspend fun setWifiOnly(enabled: Boolean) = edit { it.copy(wifiOnly = enabled) }

    suspend fun setAutoEnhance(enabled: Boolean) = edit { it.copy(autoEnhance = enabled) }

    suspend fun setCacheLimitMb(mb: Int) = edit { it.copy(cacheLimitMb = mb) }

    suspend fun setEnhanceTarget(target: EnhanceTarget) = edit { it.copy(enhanceTarget = target) }

    suspend fun setAiUpscaler(enabled: Boolean) = edit { it.copy(aiUpscaler = enabled) }

    suspend fun completeOnboarding() = edit { it.copy(onboardingDone = true) }

    private fun Preferences.toSettings(): AppSettings = AppSettings(
        themeMode = get(Keys.THEME)?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM,
        dynamicColor = get(Keys.DYNAMIC_COLOR) ?: true,
        amoled = get(Keys.AMOLED) ?: true,
        muteVideo = get(Keys.MUTE_VIDEO) ?: true,
        frameRateLimit = get(Keys.FRAME_RATE)?.let {
            runCatching { FrameRateLimit.valueOf(it) }.getOrNull()
        } ?: FrameRateLimit.FPS_60,
        wifiOnly = get(Keys.WIFI_ONLY) ?: false,
        autoEnhance = get(Keys.AUTO_ENHANCE) ?: true,
        cacheLimitMb = get(Keys.CACHE_LIMIT) ?: 256,
        enhanceTarget = get(Keys.ENHANCE_TARGET)?.let {
            runCatching { EnhanceTarget.valueOf(it) }.getOrNull()
        } ?: EnhanceTarget.HD_1080,
        aiUpscaler = get(Keys.AI_UPSCALER) ?: false,
        onboardingDone = get(Keys.ONBOARDING) ?: false,
    )
}
