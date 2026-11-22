package app.lumora.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lumora.data.store.DownloadProgress
import app.lumora.data.store.DownloadStatus
import app.lumora.designsystem.icons.LumoraIcons
import app.lumora.model.CatalogItem
import app.lumora.model.LocalWallpaper
import app.lumora.util.Formatters
import java.io.File

@Composable
fun StoreCard(
    item: CatalogItem,
    downloaded: Boolean,
    download: DownloadProgress?,
    favorite: Boolean,
    sharedModifier: Modifier,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
) {
    val aspect = aspectRatioOf(item.width, item.height)
    val shape = RoundedCornerShape(24.dp)
    val busy = download != null && (download.status == DownloadStatus.RUNNING || download.status == DownloadStatus.QUEUED)

    Column(
        modifier = modifier
            .pressableCard(shape = shape, onClick = onOpen),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            MediaThumbnail(
                model = item.thumbUrl.ifBlank { item.previewUrl },
                contentDescription = item.title,
                aspectRatio = aspect,
                shape = shape,
                modifier = sharedModifier,
            )
            // Bottom scrim for text legibility
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(86.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                        ),
                    ),
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (item.kind == app.lumora.model.MediaKind.VIDEO) {
                    VideoBadge(durationMs = item.durationMs)
                }
                if (item.featured) {
                    Badge(text = "Destacado", icon = LumoraIcons.Sparkle)
                }
            }
            FavoriteButton(
                favorite = favorite,
                onClick = onFavorite,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                    .fillMaxWidth(0.72f),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.author,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (downloaded) {
                Badge(
                    text = "En biblioteca",
                    icon = LumoraIcons.Check,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp),
                )
            } else if (busy) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .size(30.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable(onClick = onCancelDownload),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Filled.Close,
                        contentDescription = "Cancelar descarga",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .size(30.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable(onClick = onDownload),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = LumoraIcons.Download,
                        contentDescription = "Descargar",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = busy,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            LinearProgressIndicator(
                progress = { download?.progress ?: 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(50)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
        if (download?.status == DownloadStatus.FAILED) {
            Text(
                text = download.message ?: "Error de descarga",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun LocalCard(
    wallpaper: LocalWallpaper,
    enhanceProgress: Float?,
    sharedModifier: Modifier,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onCancelEnhance: () -> Unit,
) {
    val aspect = aspectRatioOf(wallpaper.width, wallpaper.height)
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .pressableCard(shape = shape, onClick = onOpen),
    ) {
        MediaThumbnail(
            model = (wallpaper.thumbPath ?: wallpaper.filePath).let { File(it) },
            contentDescription = wallpaper.name,
            aspectRatio = aspect,
            shape = shape,
            modifier = sharedModifier,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(74.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.70f)),
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (wallpaper.isVideo) {
                VideoBadge(durationMs = wallpaper.durationMs)
            }
            if (wallpaper.enhanced) {
                Badge(text = "Mejorado", icon = LumoraIcons.Sparkle)
            }
        }
        FavoriteButton(
            favorite = wallpaper.favorite,
            onClick = onFavorite,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
        ) {
            Text(
                text = wallpaper.name,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (wallpaper.isVideo) {
                    "${Formatters.bytes(wallpaper.sizeBytes)} · ${Formatters.duration(wallpaper.durationMs)}"
                } else {
                    Formatters.resolution(wallpaper.width, wallpaper.height)
                },
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1,
            )
        }
        if (enhanceProgress != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(26.dp)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(onClick = onCancelEnhance),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = LumoraIcons.Sparkle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = "  Mejorando ${Formatters.percent(enhanceProgress)} · toca para cancelar",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun ShimmerTile(aspectRatio: Float, modifier: Modifier = Modifier) {
    app.lumora.designsystem.components.ShimmerBox(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio),
        shape = RoundedCornerShape(24.dp),
    )
}

@Composable
fun InfoPill(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

private fun aspectRatioOf(width: Int, height: Int): Float {
    if (width <= 0 || height <= 0) return 0.62f
    val ratio = width.toFloat() / height.toFloat()
    return ratio.coerceIn(0.5f, 1.6f)
}
