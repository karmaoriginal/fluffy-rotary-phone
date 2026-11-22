package app.lumora.ui.preview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lumora.AppContainer
import app.lumora.data.enhance.EnhanceAdvisor
import app.lumora.data.store.DownloadProgress
import app.lumora.model.CatalogItem
import app.lumora.model.LocalWallpaper
import app.lumora.model.MediaKind
import app.lumora.model.WallpaperTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class PreviewUiState(
    val loading: Boolean = true,
    val local: LocalWallpaper? = null,
    val remote: CatalogItem? = null,
    val favorite: Boolean = false,
    val download: DownloadProgress? = null,
    val muted: Boolean = true,
    val fpsLimit: Int = 60,
    val applying: Boolean = false,
    val enhanceRunning: Boolean = false,
    val enhanceProgress: Float = 0f,
    val canEnhance: Boolean = false,
    val recommendReason: String? = null,
    val deleted: Boolean = false,
    val message: String? = null,
    val error: String? = null,
) {
    val title: String get() = local?.name ?: remote?.title ?: "Wallpaper"
    val author: String get() = local?.author ?: remote?.author ?: ""
    val license: String get() = local?.license ?: remote?.license ?: "CC0-1.0"
    val isVideo: Boolean get() = local?.isVideo ?: (remote?.kind == MediaKind.VIDEO)
    val isDownloaded: Boolean get() = local != null
    val mediaPath: String? get() = local?.filePath
    val remoteThumb: String get() = remote?.fullUrl ?: ""
}

class PreviewViewModel(
    private val container: AppContainer,
    private val source: String,
    private val id: String,
) : ViewModel() {

    private val _state = MutableStateFlow(PreviewUiState())
    val state: StateFlow<PreviewUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = container.settings.settings.first()
            _state.update {
                it.copy(muted = settings.muteVideo, fpsLimit = settings.frameRateLimit.fps)
            }
        }
        viewModelScope.launch {
            container.downloads.downloads.collect { progress ->
                _state.update { it.copy(download = progress[id]) }
            }
        }
        viewModelScope.launch {
            container.favorites.favorites.collect { favorites ->
                if (source == STORE) _state.update { it.copy(favorite = id in favorites) }
            }
        }
        viewModelScope.launch {
            container.enhancementTracker.observe().collect { statuses ->
                val localId = _state.value.local?.id ?: return@collect
                val status = statuses[localId]
                _state.update {
                    it.copy(
                        enhanceRunning = status != null,
                        enhanceProgress = status?.progress ?: 0f,
                    )
                }
            }
        }
        load()
    }

    private fun load() {
        viewModelScope.launch {
            if (source == LOCAL) {
                val wallpaper = id.toLongOrNull()?.let { container.library.find(it) }
                _state.update {
                    it.copy(
                        loading = false,
                        local = wallpaper,
                        favorite = wallpaper?.favorite ?: false,
                        error = if (wallpaper == null) "Wallpaper no encontrado" else null,
                    )
                }
            } else {
                val catalog = container.catalog.load().catalog
                val item = catalog.items.firstOrNull { it.id == id }
                val existing = container.library.findBySourceId(id)
                val favorites = container.favorites.favorites.first()
                _state.update {
                    it.copy(
                        loading = false,
                        remote = item,
                        local = existing,
                        favorite = id in favorites || existing?.favorite == true,
                        error = if (item == null && existing == null) "Elemento no encontrado" else null,
                        recommendReason = item?.let { catalogItem -> recommendationFor(catalogItem) },
                        canEnhance = existing != null && existing.isVideo,
                    )
                }
                if (existing != null && existing.isVideo) {
                    _state.update { it.copy(canEnhance = true) }
                }
            }
        }
    }

    private suspend fun recommendationFor(item: CatalogItem): String? {
        if (item.kind != MediaKind.VIDEO) return null
        val settings = container.settings.settings.first()
        val bitrate = if (item.durationMs > 0) {
            item.sizeBytes * 8_000L / item.durationMs.coerceAtLeast(1L)
        } else {
            0L
        }
        return EnhanceAdvisor.recommend(
            width = item.width,
            height = item.height,
            bitrate = bitrate,
            target = settings.enhanceTarget,
        )?.reason
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            val local = _state.value.local
            if (local != null) {
                val next = !local.favorite
                container.library.toggleFavorite(local.id, next)
                _state.update { it.copy(favorite = next, local = it.local?.copy(favorite = next)) }
            } else {
                container.favorites.toggle(id)
            }
        }
    }

    fun download() {
        val item = _state.value.remote ?: return
        container.downloads.enqueue(item)
    }

    fun cancelDownload() = container.downloads.cancel(id)

    fun setMuted(muted: Boolean) {
        _state.update { it.copy(muted = muted) }
        viewModelScope.launch { container.settings.setMuteVideo(muted) }
    }

    fun setFpsLimit(fps: Int) {
        _state.update { it.copy(fpsLimit = fps) }
        viewModelScope.launch {
            container.settings.setFrameRateLimit(
                if (fps <= 30) app.lumora.model.FrameRateLimit.FPS_30 else app.lumora.model.FrameRateLimit.FPS_60,
            )
        }
    }

    fun applyImage(
        target: WallpaperTarget,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
    ) {
        val local = _state.value.local ?: return
        viewModelScope.launch {
            _state.update { it.copy(applying = true) }
            val result = container.wallpaperApplier.applyImage(
                file = File(local.filePath),
                target = target,
                scale = scale,
                offsetXFraction = offsetX,
                offsetYFraction = offsetY,
            )
            container.library.markApplied(local.id)
            _state.update {
                it.copy(
                    applying = false,
                    message = result.fold(
                        onSuccess = {
                            val live = container.wallpaperApplier.activeLiveWallpaperName()
                            if (live != null) {
                                "Fondo aplicado. Ojo: «$live» sigue como fondo animado del inicio; " +
                                    "quítalo para ver este fondo ahí (ya se ve en la pantalla de bloqueo)"
                            } else {
                                "Fondo aplicado correctamente"
                            }
                        },
                        onFailure = { error -> error.message ?: "No se pudo aplicar el fondo" },
                    ),
                )
            }
        }
    }

    fun rename(name: String) {
        val local = _state.value.local ?: return
        viewModelScope.launch {
            container.library.rename(local.id, name)
            _state.update {
                it.copy(local = it.local?.copy(name = name.trim()), message = "Renombrado")
            }
        }
    }

    fun delete() {
        val local = _state.value.local ?: return
        viewModelScope.launch {
            container.library.delete(local.id)
            _state.update { it.copy(deleted = true) }
        }
    }

    fun startEnhancement() {
        val local = _state.value.local ?: return
        viewModelScope.launch {
            val settings = container.settings.settings.first()
            container.enhancementTracker.schedule(local.id, settings.enhanceTarget.height)
            _state.update { it.copy(message = "Mejorando en segundo plano…") }
        }
    }

    fun cancelEnhancement() {
        val local = _state.value.local ?: return
        container.enhancementTracker.cancel(local.id)
        _state.update { it.copy(message = "Mejora cancelada") }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun notify(text: String) = _state.update { it.copy(message = text) }

    companion object {
        const val LOCAL = "local"
        const val STORE = "store"
    }
}
