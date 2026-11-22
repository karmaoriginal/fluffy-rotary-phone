package app.lumora.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.lumora.AppContainer
import app.lumora.designsystem.components.EmptyState
import app.lumora.designsystem.components.ShimmerBox
import app.lumora.designsystem.icons.LumoraIcons
import app.lumora.ui.ViewModelFactories
import app.lumora.ui.components.Chip
import app.lumora.ui.components.LocalCard
import app.lumora.ui.components.ScreenHeader
import app.lumora.util.Formatters

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.LibraryScreen(
    container: AppContainer,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope,
    onOpenLocal: (Long) -> Unit,
) {
    val context = LocalContext.current
    val viewModel: LibraryViewModel = viewModel(factory = remember { ViewModelFactories.library(container) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showImportSheet by remember { mutableStateOf(false) }

    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) viewModel.import(uri)
    }
    val pickFile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.import(uri)
    }

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
                title = "Biblioteca",
                subtitle = buildString {
                    append("${state.stats.images} fotos · ${state.stats.videos} videos · ")
                    append(Formatters.bytes(state.stats.totalBytes))
                },
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Caché: ${Formatters.bytes(state.cacheBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { viewModel.clearCache() }) {
                    Text("Limpiar caché", style = MaterialTheme.typography.labelSmall)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LibraryFilter.entries.forEach { filter ->
                    Chip(
                        label = filter.label,
                        selected = state.filter == filter,
                        onClick = { viewModel.onFilterChange(filter) },
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.loading -> LibraryShimmer()
                    state.visibleItems.isEmpty() -> EmptyState(
                        icon = LumoraIcons.Image,
                        title = "Tu biblioteca está vacía",
                        description = "Importa fotos o videos cortos desde tu galería, o descarga wallpapers desde la tienda.",
                        action = {
                            ExtendedFloatingActionButton(
                                onClick = { showImportSheet = true },
                                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                                text = { Text("Importar") },
                            )
                        },
                    )

                    else -> Box(modifier = Modifier.fillMaxSize()) {
                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 150.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalItemSpacing = 12.dp,
                        ) {
                            items(
                                items = state.visibleItems,
                                key = { wallpaper -> wallpaper.id },
                            ) { wallpaper ->
                                val shared = with(this@LibraryScreen) {
                                    Modifier.sharedElement(
                                        rememberSharedContentState(key = "local-${wallpaper.id}"),
                                        animatedVisibilityScope,
                                    )
                                }
                                LocalCard(
                                    wallpaper = wallpaper,
                                    enhanceProgress = state.enhancements[wallpaper.id]?.progress,
                                    sharedModifier = shared,
                                    onOpen = { onOpenLocal(wallpaper.id) },
                                    onFavorite = { viewModel.toggleFavorite(wallpaper) },
                                    onCancelEnhance = { viewModel.cancelEnhancement(wallpaper.id) },
                                )
                            }
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showImportSheet = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Importar") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp),
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp),
        )
    }

    if (showImportSheet) {
        ModalBottomSheet(
            onDismissRequest = { showImportSheet = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            ImportSheetContent(
                onPicker = {
                    showImportSheet = false
                    pickMedia.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                    )
                },
                onSaf = {
                    showImportSheet = false
                    pickFile.launch(arrayOf("image/*", "video/*", "*/*"))
                },
            )
        }
    }

    val offer = state.offer
    if (offer != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissOffer() },
            title = { Text("¿Mejorar este video?") },
            text = {
                Text(
                    buildString {
                        append("“${offer.title}” — ${offer.reason}.\n\n")
                        append("Lumora puede reescalarlo a ${offer.targetLabel} con Lanczos, ")
                        append("reducir el ruido y recodificarlo en H.265 (más nítido y más ligero). ")
                        append("Se hace una sola vez, en segundo plano, sin sonido.")
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.acceptOffer() }) { Text("Mejorar") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissOffer() }) { Text("Ahora no") }
            },
        )
    }

    if (state.applying) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Aplicando…",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun ImportSheetContent(onPicker: () -> Unit, onSaf: () -> Unit) {
    Column(modifier = Modifier.padding(bottom = 32.dp)) {
        Text(
            text = "Añadir a la biblioteca",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 24.dp, bottom = 4.dp),
        )
        Text(
            text = "Sin permisos de almacenamiento: solo se lee el archivo que elijas.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        SheetOption(
            title = "Galería (Photo Picker)",
            subtitle = "Fotos y videos, JPG · PNG · WEBP · HEIC · AVIF · GIF · MP4 · WEBM…",
            onClick = onPicker,
        )
        SheetOption(
            title = "Archivos (SAF)",
            subtitle = "Cualquier formato, ideal para MKV, MOV o 3GP",
            onClick = onSaf,
        )
    }
}

@Composable
private fun SheetOption(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = LumoraIcons.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LibraryShimmer() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(2) {
            ShimmerBox(
                modifier = Modifier
                    .weight(1f)
                    .height(220.dp),
                shape = RoundedCornerShape(24.dp),
            )
        }
    }
}
