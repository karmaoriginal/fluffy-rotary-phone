package app.lumora.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lumora.AppContainer
import app.lumora.model.AppSettings
import app.lumora.model.EnhanceTarget
import app.lumora.model.FrameRateLimit
import app.lumora.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<AppSettings> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _cacheBytes = MutableStateFlow(0L)
    val cacheBytes: StateFlow<Long> = _cacheBytes.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        refreshCacheUsage()
    }

    fun setThemeMode(mode: ThemeMode) = launch { container.settings.setThemeMode(mode) }

    fun setDynamicColor(enabled: Boolean) = launch { container.settings.setDynamicColor(enabled) }

    fun setAmoled(enabled: Boolean) = launch { container.settings.setAmoled(enabled) }

    fun setMuteVideo(enabled: Boolean) = launch { container.settings.setMuteVideo(enabled) }

    fun setFrameRate(limit: FrameRateLimit) = launch { container.settings.setFrameRateLimit(limit) }

    fun setWifiOnly(enabled: Boolean) = launch { container.settings.setWifiOnly(enabled) }

    fun setAutoEnhance(enabled: Boolean) = launch { container.settings.setAutoEnhance(enabled) }

    fun setEnhanceTarget(target: EnhanceTarget) = launch { container.settings.setEnhanceTarget(target) }

    fun setAiUpscaler(enabled: Boolean) = launch { container.settings.setAiUpscaler(enabled) }

    fun setCacheLimit(mb: Int) = launch { container.settings.setCacheLimitMb(mb) }

    fun clearCaches() {
        viewModelScope.launch {
            val freed = container.clearAllCaches()
            refreshCacheUsage()
            _message.value = "Caché liberada: ${app.lumora.util.Formatters.bytes(freed)}"
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun refreshCacheUsage() {
        viewModelScope.launch { _cacheBytes.value = container.cacheSizeBytes() }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
