package app.lumora.ui.preview

import android.net.Uri
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.lumora.AppContainer
import app.lumora.model.WallpaperTarget
import app.lumora.ui.ViewModelFactories
import app.lumora.ui.components.Badge
import app.lumora.ui.components.Chip
import app.lumora.ui.components.DownloadBar
import app.lumora.ui.components.FavoriteButton
import app.lumora.ui.components.InfoPill
import app.lumora.designsystem.icons.LumoraIcons
import app.lumora.util.Formatters
import app.lumora.wallpaper.LiveWallpaperController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.PreviewScreen(
    container: AppContainer,
    source: String,
    id: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: PreviewViewModel = viewModel(
        key = "preview-$source-$id",
        factory = remember { ViewModelFactories.preview(container, source, id) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val liveController = remember { LiveWallpaperController(context, container.wallpaperApplier) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var target by remember { mutableStateOf(WallpaperTarget.BOTH) }
    var showChrome by remember { mutableStateOf(true) }
    var showMenu by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }
    LaunchedEffect(state.message) {
        val message = state.message
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // ---------------- media ----------------
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(state.isDownloaded) {
                    if (!state.isDownloaded) return@pointerInput
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x / size.width.toFloat() * 2f).coerceIn(-1f, 1f)
                        offsetY = (offsetY + dragAmount.y / size.height.toFloat() * 2f).coerceIn(-1f, 1f)
                    }
                },
        ) {
            val animatedScale by animateFloatAsState(targetValue = scale, label = "cropScale")
            val model: Any? = when {
                state.mediaPath != null -> File(state.mediaPath!!)
                state.remote != null -> state.remote!!.thumbUrl.ifBlank { state.remote!!.fullUrl }
                else -> null
            }
            if (state.isVideo && state.mediaPath != null) {
                VideoSurface(
                    path = state.mediaPath!!,
                    muted = state.muted,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (model != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(model).crossfade(220).build(),
                    contentDescription = state.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = animatedScale
                            scaleY = animatedScale
                            translationX = offsetX * size.width * (animatedScale - 1f) / 2f
                            translationY = offsetY * size.height * (animatedScale - 1f) / 2f
                        },
                )
            }
        }

        if (showChrome) {
            HomeScreenChrome()
        }

        // ---------------- top bar ----------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent),
                    ),
                )
                .padding(start = 10.dp, end = 10.dp, top = 44.dp, bottom = 26.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = Color.White,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (state.isDownloaded) "En tu biblioteca" else "En la tienda",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
            Box {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .clickable { showMenu = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Más acciones",
                        tint = Color.White,
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Probar en pantalla de inicio") },
                        onClick = {
                            showChrome = !showChrome
                            showMenu = false
                        },
                    )
                    if (state.isDownloaded) {
                        DropdownMenuItem(
                            text = { Text("Renombrar") },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                showRename = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Eliminar") },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                showDelete = true
                            },
                        )
                    }
                }
            }
            FavoriteButton(
                favorite = state.favorite,
                onClick = viewModel::toggleFavorite,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        // ---------------- bottom panel ----------------
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.title,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = buildString {
                                if (state.author.isNotBlank()) append("${state.author} · ")
                                append(state.license)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.canEnhance && !state.isDownloaded.not()) {
                        Badge(
                            text = "Mejorable",
                            icon = LumoraIcons.Sparkle,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        )
                    }
                }

                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val local = state.local
                    val remote = state.remote
                    InfoPill(
                        text = Formatters.resolution(
                            local?.width ?: remote?.width ?: 0,
                            local?.height ?: remote?.height ?: 0,
                        ),
                    )
                    InfoPill(
                        text = Formatters.bytes(local?.sizeBytes ?: remote?.sizeBytes ?: 0L),
                    )
                    if (state.isVideo) {
                        InfoPill(text = Formatters.duration(local?.durationMs ?: remote?.durationMs ?: 0L))
                    }
                    if (local?.enhanced == true) {
                        InfoPill(text = "H.265")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                when {
                    state.loading -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Cargando…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    state.error != null -> {
                        Text(
                            text = state.error ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    !state.isDownloaded -> {
                        val download = state.download
                        val downloading = download != null &&
                            (download.status == app.lumora.data.store.DownloadStatus.RUNNING ||
                                download.status == app.lumora.data.store.DownloadStatus.QUEUED)
                        if (downloading) {
                            DownloadBar(
                                progress = download?.progress ?: 0f,
                                label = "Descargando ${Formatters.percent(download?.progress ?: 0f)} · toca cancelar para pausar",
                                modifier = Modifier.clickable { viewModel.cancelDownload() },
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        Button(
                            onClick = viewModel::download,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            enabled = !downloading,
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Icon(LumoraIcons.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                if (downloading) "Descargando…" else {
                                    "Descargar · ${Formatters.bytes(state.remote?.sizeBytes ?: 0L)}"
                                },
                            )
                        }
                        if (state.download?.status == app.lumora.data.store.DownloadStatus.FAILED) {
                            Text(
                                text = state.download?.message ?: "Error de descarga",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }

                    state.isVideo -> {
                        VideoActions(
                            muted = state.muted,
                            fpsLimit = state.fpsLimit,
                            enhanceRunning = state.enhanceRunning,
                            enhanceProgress = state.enhanceProgress,
                            canEnhance = state.canEnhance,
                            onMutedChange = viewModel::setMuted,
                            onFpsChange = viewModel::setFpsLimit,
                            onApplyLive = {
                                val path = state.mediaPath
                                if (path != null) {
                                    val intent = liveController.prepareAndBuildIntent(
                                        video = File(path),
                                        muted = state.muted,
                                        fpsLimit = state.fpsLimit,
                                    )
                                    runCatching { context.startActivity(intent) }
                                        .onFailure {
                                            runCatching {
                                                context.startActivity(liveController.chooserIntent())
                                            }
                                        }
                                }
                            },
                            onEnhance = viewModel::startEnhancement,
                            onCancelEnhance = viewModel::cancelEnhancement,
                        )
                    }

                    else -> {
                        ImageActions(
                            scale = scale,
                            onScaleChange = { scale = it },
                            target = target,
                            onTargetChange = { target = it },
                            applying = state.applying,
                            onReset = {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            },
                            onApply = { viewModel.applyImage(target, scale, offsetX, offsetY) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp),
        )
    }

    if (showRename) {
        var name by remember { mutableStateOf(state.title) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Renombrar") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    singleLine = true,
                    label = { Text("Nombre") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.rename(name)
                    showRename = false
                }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("Cancelar") }
            },
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("¿Eliminar wallpaper?") },
            text = { Text("Se borrará el archivo de tu dispositivo. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    viewModel.delete()
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun ImageActions(
    scale: Float,
    onScaleChange: (Float) -> Unit,
    target: WallpaperTarget,
    onTargetChange: (WallpaperTarget) -> Unit,
    applying: Boolean,
    onReset: () -> Unit,
    onApply: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Ajuste",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = scale,
                onValueChange = onScaleChange,
                valueRange = 1f..1.8f,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            )
            Text(
                text = "${(scale * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WallpaperTarget.entries.forEach { option ->
                Chip(
                    label = when (option) {
                        WallpaperTarget.HOME -> "Inicio"
                        WallpaperTarget.LOCK -> "Bloqueo"
                        WallpaperTarget.BOTH -> "Ambos"
                    },
                    selected = target == option,
                    onClick = { onTargetChange(option) },
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onReset) { Text("Centrar") }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Button(
            onClick = onApply,
            enabled = !applying,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Icon(LumoraIcons.Sparkle, contentDescription = null)
            Spacer(modifier = Modifier.width(10.dp))
            Text(if (applying) "Aplicando…" else "Aplicar fondo")
        }
    }
}

@Composable
private fun VideoActions(
    muted: Boolean,
    fpsLimit: Int,
    enhanceRunning: Boolean,
    enhanceProgress: Float,
    canEnhance: Boolean,
    onMutedChange: (Boolean) -> Unit,
    onFpsChange: (Int) -> Unit,
    onApplyLive: () -> Unit,
    onEnhance: () -> Unit,
    onCancelEnhance: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Silenciar",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = muted, onCheckedChange = onMutedChange)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "FPS",
                style = MaterialTheme.typography.bodyLarge,
            )
            Chip(label = "30", selected = fpsLimit <= 30, onClick = { onFpsChange(30) })
            Chip(label = "60", selected = fpsLimit > 30, onClick = { onFpsChange(60) })
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Ahorra batería con 30",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onApplyLive,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Icon(LumoraIcons.LiveWallpaper, contentDescription = null)
            Spacer(modifier = Modifier.width(10.dp))
            Text("Aplicar fondo animado")
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (enhanceRunning) {
            DownloadBar(
                progress = enhanceProgress,
                label = "Mejorando ${Formatters.percent(enhanceProgress)} · toca para cancelar",
                modifier = Modifier.clickable(onClick = onCancelEnhance),
            )
        } else if (canEnhance) {
            TextButton(onClick = onEnhance) {
                Icon(LumoraIcons.Sparkle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Mejorar calidad (Lanczos + H.265)")
            }
        }
    }
}

@Composable
private fun VideoSurface(path: String, muted: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(Unit) {
        onDispose {
            player.release()
        }
    }
    LaunchedEffect(path) {
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
        player.repeatMode = Player.REPEAT_MODE_ONE
        player.prepare()
        player.play()
    }
    LaunchedEffect(muted) {
        player.volume = if (muted) 0f else 1f
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                this.player = player
            }
        },
        update = { view -> view.player = player },
        modifier = modifier,
    )
}

@Composable
private fun HomeScreenChrome() {
    val time = remember { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 96.dp, start = 26.dp, bottom = 360.dp),
    ) {
        Text(
            text = time,
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light),
            color = Color.White.copy(alpha = 0.92f),
        )
        Text(
            text = "Lumora · vista previa",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.7f),
        )
        Spacer(modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(4) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.16f)),
                )
            }
        }
    }
}
