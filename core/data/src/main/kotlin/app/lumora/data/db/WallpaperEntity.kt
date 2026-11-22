package app.lumora.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.lumora.model.LocalWallpaper
import app.lumora.model.MediaKind
import app.lumora.model.WallpaperOrigin

@Entity(
    tableName = "wallpapers",
    indices = [
        Index("kind"),
        Index("addedAt"),
        Index("sourceId"),
    ],
)
data class WallpaperEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val displayName: String,
    val filePath: String,
    val kind: String,
    val origin: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val durationMs: Long = 0L,
    val category: String = "",
    val author: String = "",
    val license: String = "",
    val addedAt: Long = System.currentTimeMillis(),
    val lastAppliedAt: Long = 0L,
    val applyCount: Int = 0,
    val favorite: Boolean = false,
    val thumbPath: String = "",
    val enhanced: Boolean = false,
    val sourceId: String = "",
)

fun WallpaperEntity.toModel(): LocalWallpaper = LocalWallpaper(
    id = id,
    name = displayName,
    filePath = filePath,
    kind = if (kind.equals(MediaKind.VIDEO.name, ignoreCase = true)) MediaKind.VIDEO else MediaKind.IMAGE,
    origin = if (origin.equals(WallpaperOrigin.STORE.name, ignoreCase = true)) {
        WallpaperOrigin.STORE
    } else {
        WallpaperOrigin.IMPORT
    },
    mimeType = mimeType,
    width = width,
    height = height,
    sizeBytes = sizeBytes,
    durationMs = durationMs,
    category = category,
    author = author,
    license = license,
    addedAt = addedAt,
    lastAppliedAt = lastAppliedAt.takeIf { it > 0L },
    applyCount = applyCount,
    favorite = favorite,
    thumbPath = thumbPath.takeIf { it.isNotBlank() },
    enhanced = enhanced,
    sourceId = sourceId,
)
