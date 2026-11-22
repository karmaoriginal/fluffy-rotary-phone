package app.lumora.ui.store

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lumora.data.library.LibraryRepository
import app.lumora.data.store.CatalogRepository
import app.lumora.data.store.CatalogSource
import app.lumora.data.store.DownloadCoordinator
import app.lumora.data.store.DownloadProgress
import app.lumora.data.store.StoreFavoritesRepository
import app.lumora.model.CatalogCategory
import app.lumora.model.CatalogItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class StoreSort(val label: String) {
    RECENT("Recientes"),
    POPULAR("Populares"),
    AZ("A–Z"),
}

data class StoreUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val categories: List<CatalogCategory> = emptyList(),
    val allItems: List<CatalogItem> = emptyList(),
    val query: String = "",
    val selectedCategory: String? = null,
    val sort: StoreSort = StoreSort.RECENT,
    val favoritesOnly: Boolean = false,
    val favorites: Set<String> = emptySet(),
    val downloadedIds: Set<String> = emptySet(),
    val downloads: Map<String, DownloadProgress> = emptyMap(),
    val source: CatalogSource = CatalogSource.NETWORK,
    val message: String? = null,
) {
    val items: List<CatalogItem>
        get() {
            val normalizedQuery = query.trim().lowercase()
            var filtered = allItems
            selectedCategory?.let { category ->
                filtered = filtered.filter { category in it.categories }
            }
            if (favoritesOnly) {
                filtered = filtered.filter { it.id in favorites }
            }
            if (normalizedQuery.isNotEmpty()) {
                filtered = filtered.filter { item ->
                    item.title.lowercase().contains(normalizedQuery) ||
                        item.author.lowercase().contains(normalizedQuery) ||
                        item.tags.any { it.lowercase().contains(normalizedQuery) } ||
                        item.categories.any { it.lowercase().contains(normalizedQuery) }
                }
            }
            return when (sort) {
                StoreSort.RECENT -> filtered.sortedByDescending { it.addedAt }
                StoreSort.POPULAR -> filtered.sortedByDescending { it.downloads }
                StoreSort.AZ -> filtered.sortedBy { it.title.lowercase() }
            }
        }
}

class StoreViewModel(
    private val catalogRepository: CatalogRepository,
    private val libraryRepository: LibraryRepository,
    private val downloads: DownloadCoordinator,
    private val favoritesRepository: StoreFavoritesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(StoreUiState())
    val state: StateFlow<StoreUiState> = _state.asStateFlow()

    init {
        load(forceRefresh = false)
        viewModelScope.launch {
            favoritesRepository.favorites.collect { favorites ->
                _state.update { it.copy(favorites = favorites) }
            }
        }
        viewModelScope.launch {
            libraryRepository.wallpapers.collect { items ->
                _state.update {
                    it.copy(
                        downloadedIds = items.mapNotNull { wallpaper ->
                            wallpaper.sourceId.takeIf { id -> id.isNotBlank() }
                        }.toSet(),
                    )
                }
            }
        }
        viewModelScope.launch {
            downloads.downloads.collect { progress ->
                _state.update { it.copy(downloads = progress) }
            }
        }
    }

    fun load(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    refreshing = forceRefresh,
                    loading = it.allItems.isEmpty(),
                )
            }
            val result = catalogRepository.load(forceRefresh)
            _state.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    categories = result.catalog.categories.ifEmpty { CatalogRepository.DEFAULT_CATEGORIES },
                    allItems = result.catalog.items,
                    source = result.source,
                    message = result.message,
                    downloadedIds = it.downloadedIds,
                )
            }
        }
    }

    fun onQueryChange(value: String) = _state.update { it.copy(query = value) }

    fun onCategorySelected(id: String?) = _state.update { it.copy(selectedCategory = id) }

    fun onSortChange(sort: StoreSort) = _state.update { it.copy(sort = sort) }

    fun toggleFavoritesOnly() = _state.update { it.copy(favoritesOnly = !it.favoritesOnly) }

    fun toggleFavorite(item: CatalogItem) {
        viewModelScope.launch { favoritesRepository.toggle(item.id) }
    }

    fun download(item: CatalogItem) {
        val started = downloads.enqueue(item)
        if (!started) {
            _state.update { it.copy(message = "La descarga ya está en curso") }
        }
    }

    fun cancelDownload(itemId: String) = downloads.cancel(itemId)

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
