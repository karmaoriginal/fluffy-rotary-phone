package app.lumora.ui.store

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.lumora.AppContainer
import app.lumora.designsystem.icons.LumoraIcons
import app.lumora.model.CatalogItem
import app.lumora.ui.ViewModelFactories
import app.lumora.ui.components.CategoryChips
import app.lumora.ui.components.Chip
import app.lumora.ui.components.CircleIconButton
import app.lumora.ui.components.ScreenHeader
import app.lumora.ui.components.SearchField
import app.lumora.ui.components.ShimmerTile
import app.lumora.ui.components.StoreCard
import app.lumora.designsystem.components.EmptyState

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.StoreScreen(
    container: AppContainer,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope,
    onOpenItem: (CatalogItem) -> Unit,
) {
    val context = LocalContext.current
    val viewModel: StoreViewModel = viewModel(factory = remember { ViewModelFactories.store(container) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        val message = state.message
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "Tienda",
                subtitle = "Arte original y contenido CC0 · ${state.allItems.size} piezas",
                trailing = {
                    CircleIconButton(
                        icon = Icons.Filled.Refresh,
                        contentDescription = "Actualizar catálogo",
                        onClick = { viewModel.load(forceRefresh = true) },
                    )
                },
            )
            SearchField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StoreSort.entries.forEach { sort ->
                    Chip(
                        label = sort.label,
                        selected = state.sort == sort,
                        onClick = { viewModel.onSortChange(sort) },
                    )
                }
                Chip(
                    label = "Favoritos",
                    selected = state.favoritesOnly,
                    onClick = { viewModel.toggleFavoritesOnly() },
                )
            }
            CategoryChips(
                categories = state.categories.map { it.id to it.name },
                selectedId = state.selectedCategory,
                onSelect = viewModel::onCategorySelected,
            )
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.loading -> StoreShimmer()
                    state.items.isEmpty() -> EmptyState(
                        icon = LumoraIcons.Sparkle,
                        title = "Nada por aquí todavía",
                        description = "Prueba con otra categoría, limpia la búsqueda o pulsa actualizar para traer el catálogo más reciente.",
                    )

                    else -> Box(modifier = Modifier.fillMaxSize()) {
                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalItemSpacing = 12.dp,
                        ) {
                            items(
                                items = state.items,
                                key = { item -> item.id },
                                contentType = { item -> item.kind.name },
                            ) { item ->
                                val shared = with(this@StoreScreen) {
                                    Modifier.sharedElement(
                                        rememberSharedContentState(key = "store-${item.id}"),
                                        animatedVisibilityScope,
                                    )
                                }
                                StoreCard(
                                    item = item,
                                    downloaded = item.id in state.downloadedIds,
                                    download = state.downloads[item.id],
                                    favorite = item.id in state.favorites,
                                    sharedModifier = shared,
                                    onOpen = { onOpenItem(item) },
                                    onFavorite = { viewModel.toggleFavorite(item) },
                                    onDownload = { viewModel.download(item) },
                                    onCancelDownload = { viewModel.cancelDownload(item.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp),
        )
    }
}

@Composable
private fun StoreShimmer() {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp,
        userScrollEnabled = false,
    ) {
        items(count = 8) { index ->
            val ratio = if (index % 3 == 0) 0.66f else 1.24f
            ShimmerTile(aspectRatio = ratio)
        }
    }
}
