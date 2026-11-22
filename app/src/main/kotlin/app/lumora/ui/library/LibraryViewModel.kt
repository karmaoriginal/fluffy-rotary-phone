package app.lumora.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lumora.data.enhance.EnhanceAdvisor
import app.lumora.data.library.LibraryRepository
import app.lumora.data.settings.SettingsRepository
import app.lumora.data.wallpaper.WallpaperApplier
import app.lumora.model.LibraryStats
import app.lumora.model.LocalWallpaper
import app.lumora.model.WallpaperTarget
import app.lumora.work.EnhanceStatus
import app.lumora.work.EnhancementTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max

enum class LibraryFilter(val label: String) {
    ALL("Todo"),
    IMAGES("Fotos"),
    VIDEOS("Videos"),
    FAVORITES("Favoritos"),
}

data class EnhanceOffer(
    val wallpaperId: Long,
    val title: String,
    val reason: String,
    val targetLabel: String,
)

data class LibraryUiState(
    val loading: Boolean = true,
    val items: List<LocalWallpaper> = emptyList(),
    val filter: LibraryFilter = LibraryFilter.ALL,
    val stats: LibraryStats = LibraryStats(),
    val cacheBytes: Long = 0L,
    val enhancements: Map<Long, EnhanceStatus> = emptyMap(),
    val offer: EnhanceOffer? = null,
    val importing: Boolean = false,
    val applying: Boolean = false,
    val message: String? = null,
) {
    val visibleItems: List<LocalWallpaper>
        get() = when (filter) {
            LibraryFilter.ALL -> items
            LibraryFilter.IMAGES -> items.filter { !it.isVideo }
            LibraryFilter.VIDEOS -> items.filter { it.isVideo }
            LibraryFilter.FAVORITES -> items.filter { it.favorite }
        }
}

class LibraryViewModel(
    private val libraryRepository: LibraryRepository,
    private val settingsRepository: SettingsRepository,
    private val tracker: EnhancementTracker,
    @Suppress("unused") private val downloads: app.lumora.data.store.DownloadCoordinator,
    @Suppress("unused") private val applier: WallpaperApplier,
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            libraryRepository.wallpapers.collect { items ->
                _state.update { it.copy(items = items, loading = false) }
            }
        }
        viewModelScope.launch {
            libraryRepository.stats.collect { stats ->
                _state.update { it.copy(stats = stats) }
            }
        }
        viewModelScope.launch {
            tracker.observe().collect { enhancements ->
                _state.update { it.copy(enhancements = enhancements) }
            }
        }
        refreshCacheUsage()
    }

    fun onFilterChange(filter: LibraryFilter) = _state.update { it.copy(filter = filter) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun dismissOffer() = _state.update { it.copy(offer = null) }

    fun refreshCacheUsage() {
        viewModelScope.launch {
            val cache = libraryRepository.cacheBytes()
            _state.update { it.copy(cacheBytes = cache) }
        }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(importing = true) }
            val result = libraryRepository.importFromUri(uri)
            result.onSuccess { wallpaper ->
                _state.update {
                    it.copy(importing = false, message = "Añadido: ${wallpaper.name}")
                }
                refreshCacheUsage()
                maybeOfferEnhancement(wallpaper)
            }.onFailure { error ->
                _state.update {
                    it.copy(importing = false, message = error.message ?: "No se pudo importar")
                }
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            libraryRepository.delete(id)
            _state.update { it.copy(message = "Wallpaper eliminado") }
            refreshCacheUsage()
        }
    }

    fun rename(id: Long, name: String) {
        viewModelScope.launch {
            libraryRepository.rename(id, name)
            _state.update { it.copy(message = "Renombrado") }
        }
    }

    fun toggleFavorite(wallpaper: LocalWallpaper) {
        viewModelScope.launch {
            libraryRepository.toggleFavorite(wallpaper.id, !wallpaper.favorite)
        }
    }

    fun acceptOffer() {
        val offer = _state.value.offer ?: return
        _state.update { it.copy(offer = null) }
        startEnhancement(offer.wallpaperId)
    }

    fun startEnhancement(wallpaperId: Long) {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            tracker.schedule(wallpaperId, settings.enhanceTarget.height)
            _state.update { it.copy(message = "Mejorando en segundo plano…") }
        }
    }

    fun cancelEnhancement(wallpaperId: Long) {
        tracker.cancel(wallpaperId)
        _state.update { it.copy(message = "Mejora cancelada") }
    }

    fun applyImage(wallpaper: LocalWallpaper, target: WallpaperTarget) {
        viewModelScope.launch {
            _state.update { it.copy(applying = true) }
            val result = applier.applyImage(File(wallpaper.filePath), target)
            libraryRepository.markApplied(wallpaper.id)
            _state.update {
                it.copy(
                    applying = false,
                    message = result.fold(
                        onSuccess = { "Fondo aplicado" },
                        onFailure = { error -> error.message ?: "No se pudo aplicar" },
                    ),
                )
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            val freed = libraryRepository.clearCaches()
            refreshCacheUsage()
            _state.update {
                it.copy(message = "Caché liberada: ${app.lumora.util.Formatters.bytes(freed)}")
            }
        }
    }

    private suspend fun maybeOfferEnhancement(wallpaper: LocalWallpaper) {
        if (!wallpaper.isVideo) return
        val settings = settingsRepository.settings.first()
        if (!settings.autoEnhance) return
        val bitrate = if (wallpaper.durationMs > 0) {
            wallpaper.sizeBytes * 8_000L / max(wallpaper.durationMs, 1L)
        } else {
            0L
        }
        val recommendation = EnhanceAdvisor.recommend(
            width = wallpaper.width,
            height = wallpaper.height,
            bitrate = bitrate,
            target = settings.enhanceTarget,
        ) ?: return
        _state.update {
            it.copy(
                offer = EnhanceOffer(
                    wallpaperId = wallpaper.id,
                    title = wallpaper.name,
                    reason = recommendation.reason,
                    targetLabel = settings.enhanceTarget.label,
                ),
            )
        }
    }
}
