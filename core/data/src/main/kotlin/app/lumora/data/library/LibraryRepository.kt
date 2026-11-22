package app.lumora.data.library

import android.content.Context
import android.net.Uri
import app.lumora.data.db.WallpaperDao
import app.lumora.data.db.WallpaperEntity
import app.lumora.data.db.toModel
import app.lumora.data.media.MediaFormats
import app.lumora.data.media.MediaInfo
import app.lumora.data.media.MediaProbe
import app.lumora.data.media.ThumbnailFactory
import app.lumora.data.storage.FileStore
import app.lumora.model.CatalogItem
import app.lumora.model.LibraryStats
import app.lumora.model.LocalWallpaper
import app.lumora.model.MediaKind
import app.lumora.model.WallpaperOrigin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Owns the local library: imports from the Photo Picker / SAF, downloads from the store,
 * thumbnails, renames, deletions and space accounting.
 */
class LibraryRepository(
    private val context: Context,
    private val dao: WallpaperDao,
    private val fileStore: FileStore,
) {

    val wallpapers: Flow<List<LocalWallpaper>> =
        dao.observeAll().map { entities -> entities.map { it.toModel() } }

    val favorites: Flow<List<LocalWallpaper>> =
        dao.observeFavorites().map { entities -> entities.map { it.toModel() } }

    val stats: Flow<LibraryStats> = wallpapers.map { items ->
        LibraryStats(
            images = items.count { !it.isVideo },
            videos = items.count { it.isVideo },
            totalBytes = items.sumOf { it.sizeBytes },
            cacheBytes = fileStore.cacheBytes(),
        )
    }

    suspend fun find(id: Long): LocalWallpaper? = withContext(Dispatchers.IO) {
        dao.findById(id)?.toModel()
    }

    suspend fun findBySourceId(sourceId: String): LocalWallpaper? = withContext(Dispatchers.IO) {
        dao.findBySourceId(sourceId)?.toModel()
    }

    /**
     * Copies a picked media file into private storage (Photo Picker URIs are only readable while
     * the grant lasts, and the live wallpaper runs in a different process context).
     */
    suspend fun importFromUri(
        uri: Uri,
        category: String = "",
        authorOverride: String? = null,
        license: String = "Contenido propio",
    ): Result<LocalWallpaper> = withContext(Dispatchers.IO) {
        var destination: File? = null
        try {
            val resolver = context.contentResolver
            val resolverName = MediaProbe.displayName(context, uri)
            val extension = resolverName?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.isNotBlank() }
            val mime = resolver.getType(uri)
            val kind = MediaFormats.isSupported(mime, extension)
                ?: return@withContext Result.failure(
                    UnsupportedFormatException(
                        "Formato no compatible (${mime ?: extension ?: "desconocido"}). " +
                            "Usa JPG, PNG, WEBP, HEIC, AVIF, GIF, BMP, MP4, MKV, WEBM, MOV o 3GP.",
                    ),
                )

            val resolvedMime = mime ?: MediaFormats.fallbackMime(kind, extension)
            val baseName = resolverName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "Importado"
            val destinationFile = fileStore.newWallpaperFile(
                baseName = baseName,
                extension = extension ?: FileStore.extensionFor(kind, resolvedMime),
            )
            destination = destinationFile

            val input = resolver.openInputStream(uri)
                ?: return@withContext Result.failure(IllegalStateException("No se pudo abrir el archivo"))
            input.use { stream ->
                destinationFile.outputStream().use { out -> stream.copyTo(out, DEFAULT_BUFFER) }
            }

            val info = MediaProbe.probeFile(destinationFile, kind, resolvedMime)
            Result.success(persist(destinationFile, kind, info, baseName, category, authorOverride ?: "Importado", license, sourceId = ""))
        } catch (t: Throwable) {
            destination?.let { runCatching { it.delete() } }
            Result.failure(t)
        }
    }

    /** Registers an already-downloaded store file as a library item. */
    suspend fun adoptDownload(
        item: CatalogItem,
        downloadedFile: File,
    ): Result<LocalWallpaper> = withContext(Dispatchers.IO) {
        try {
            val target = fileStore.finalFileFor(
                id = item.id,
                extension = FileStore.extensionFor(item.kind, item.kind.defaultMime()),
            )
            if (downloadedFile.absolutePath != target.absolutePath) {
                downloadedFile.copyTo(target, overwrite = true)
                downloadedFile.delete()
            }
            val info = MediaProbe.probeFile(target, item.kind, item.kind.defaultMime())
            val merged = if (info.width == 0 || info.height == 0) {
                info.copy(width = item.width, height = item.height, durationMs = item.durationMs)
            } else {
                info
            }
            Result.success(
                persist(
                    file = target,
                    kind = item.kind,
                    info = merged,
                    displayName = item.title,
                    category = item.categories.firstOrNull() ?: "",
                    author = item.author,
                    license = item.license,
                    sourceId = item.id,
                ),
            )
        } catch (t: Throwable) {
            runCatching { downloadedFile.delete() }
            Result.failure(t)
        }
    }

    private suspend fun persist(
        file: File,
        kind: MediaKind,
        info: MediaInfo,
        displayName: String,
        category: String,
        author: String,
        license: String,
        sourceId: String,
    ): LocalWallpaper {
        val thumb = fileStore.thumbFileFor(file)
        ThumbnailFactory.createThumbnail(file, kind, thumb)
        val entity = WallpaperEntity(
            displayName = displayName,
            filePath = file.absolutePath,
            kind = kind.name,
            origin = if (sourceId.isNotBlank()) WallpaperOrigin.STORE.name else WallpaperOrigin.IMPORT.name,
            mimeType = info.mimeType,
            width = info.width,
            height = info.height,
            sizeBytes = info.sizeBytes,
            durationMs = info.durationMs,
            category = category,
            author = author,
            license = license,
            addedAt = System.currentTimeMillis(),
            thumbPath = if (thumb.exists()) thumb.absolutePath else "",
            sourceId = sourceId,
        )
        val id = dao.insert(entity)
        return dao.findById(id)?.toModel() ?: entity.copy(id = id).toModel()
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        val entity = dao.findById(id) ?: return@withContext
        fileStore.deleteWithThumbnail(File(entity.filePath))
        dao.deleteById(id)
    }

    suspend fun rename(id: Long, name: String) = withContext(Dispatchers.IO) {
        val trimmed = name.trim().take(60)
        if (trimmed.isNotEmpty()) dao.rename(id, trimmed)
    }

    suspend fun toggleFavorite(id: Long, favorite: Boolean) = withContext(Dispatchers.IO) {
        dao.setFavorite(id, favorite)
    }

    suspend fun markApplied(id: Long) = withContext(Dispatchers.IO) {
        dao.markApplied(id, System.currentTimeMillis())
    }

    /** Swaps the original file with the enhanced transcode, freeing the old one. */
    suspend fun replaceWithEnhanced(
        id: Long,
        enhancedFile: File,
        width: Int,
        height: Int,
    ): LocalWallpaper? = withContext(Dispatchers.IO) {
        val entity = dao.findById(id) ?: return@withContext null
        val previous = File(entity.filePath)
        val target = fileStore.newWallpaperFile(
            baseName = previous.nameWithoutExtension,
            extension = "mp4",
        )
        if (!enhancedFile.renameTo(target)) {
            enhancedFile.copyTo(target, overwrite = true)
            enhancedFile.delete()
        }
        val thumb = fileStore.thumbFileFor(target)
        ThumbnailFactory.createThumbnail(target, MediaKind.VIDEO, thumb)
        val updated = entity.copy(
            filePath = target.absolutePath,
            thumbPath = if (thumb.exists()) thumb.absolutePath else entity.thumbPath,
            width = width,
            height = height,
            sizeBytes = target.length(),
            mimeType = "video/mp4",
            enhanced = true,
        )
        dao.update(updated)
        if (previous.absolutePath != target.absolutePath) {
            fileStore.deleteWithThumbnail(previous)
        }
        dao.findById(id)?.toModel()
    }

    suspend fun totalBytes(): Long = withContext(Dispatchers.IO) { fileStore.libraryBytes() }

    suspend fun cacheBytes(): Long = withContext(Dispatchers.IO) { fileStore.cacheBytes() }

    suspend fun trimCache(limitMb: Int): Long = withContext(Dispatchers.IO) {
        fileStore.trimCache(limitMb.toLong() * 1024L * 1024L)
    }

    suspend fun clearCaches(): Long = withContext(Dispatchers.IO) {
        fileStore.clearDownloads() + fileStore.clearEnhanceCache()
    }

    suspend fun regenerateMissingThumbs() = withContext(Dispatchers.IO) {
        dao.observeAll()
        for (path in dao.allFilePaths()) {
            val file = File(path)
            if (!file.exists()) continue
            val thumb = fileStore.thumbFileFor(file)
            if (!thumb.exists()) {
                ThumbnailFactory.createThumbnail(file, MediaFormats.kindOfFile(file), thumb)
            }
        }
    }

    private fun MediaKind.defaultMime(): String = MediaFormats.fallbackMime(this, null)

    class UnsupportedFormatException(message: String) : IllegalArgumentException(message)

    private companion object {
        const val DEFAULT_BUFFER = 64 * 1024
    }
}
