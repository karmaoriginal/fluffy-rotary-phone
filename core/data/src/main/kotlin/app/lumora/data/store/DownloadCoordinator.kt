package app.lumora.data.store

import app.lumora.data.library.LibraryRepository
import app.lumora.data.net.ConnectivityChecker
import app.lumora.data.settings.SettingsRepository
import app.lumora.data.storage.FileStore
import app.lumora.model.CatalogItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

enum class DownloadStatus { QUEUED, RUNNING, COMPLETED, FAILED, CANCELED }

data class DownloadProgress(
    val itemId: String,
    val title: String,
    val progress: Float,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val status: DownloadStatus,
    val message: String? = null,
)

/**
 * Resumable, cancellable downloads with progress reporting.
 *
 * Interrupted downloads leave a `.part` file in the cache dir; the next attempt continues
 * from where it stopped using an HTTP `Range` request (servers that ignore it are supported).
 */
class DownloadCoordinator(
    private val client: OkHttpClient,
    private val fileStore: FileStore,
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
    private val connectivity: ConnectivityChecker,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()

    private val _downloads = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadProgress>> = _downloads.asStateFlow()

    fun enqueue(item: CatalogItem): Boolean {
        if (jobs[item.id]?.isActive == true) return false
        _downloads.update {
            it + (item.id to DownloadProgress(
                itemId = item.id,
                title = item.title,
                progress = 0f,
                bytesDownloaded = 0L,
                totalBytes = item.sizeBytes,
                status = DownloadStatus.QUEUED,
            ))
        }
        val job = scope.launch {
            try {
                runDownload(item)
            } catch (cancellation: CancellationException) {
                update(item.id, DownloadStatus.CANCELED, message = "Cancelada")
                throw cancellation
            } catch (t: Throwable) {
                update(
                    item.id,
                    DownloadStatus.FAILED,
                    message = t.message ?: "Error de descarga",
                )
            } finally {
                jobs.remove(item.id)
            }
        }
        jobs[item.id] = job
        return true
    }

    fun cancel(itemId: String) {
        jobs[itemId]?.cancel()
        jobs.remove(itemId)
    }

    fun cancelAll() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
    }

    fun clearFinished() {
        _downloads.update { map ->
            map.filterValues {
                it.status == DownloadStatus.COMPLETED ||
                    it.status == DownloadStatus.CANCELED ||
                    it.status == DownloadStatus.FAILED
            }.let { finished -> map - finished.keys }
        }
    }

    private suspend fun runDownload(item: CatalogItem) {
        val preferences = settings.settings.first()
        if (!connectivity.canDownload(preferences.wifiOnly)) {
            throw IOException(
                if (preferences.wifiOnly) {
                    "Sin Wi-Fi: las descargas están limitadas a redes no medidas"
                } else {
                    "Sin conexión a internet"
                },
            )
        }
        if (item.fullUrl.isBlank()) throw IOException("Este elemento no tiene archivo descargable")

        val partFile = fileStore.partFileFor(item.id)
        var existing = if (partFile.exists()) partFile.length() else 0L

        val requestBuilder = Request.Builder().url(item.fullUrl).get()
        if (existing > 0L) requestBuilder.header("Range", "bytes=$existing-")

        update(item.id, DownloadStatus.RUNNING)

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (response.code == 416) {
                // Range not satisfiable: the partial file is bogus, start over.
                partFile.delete()
                existing = 0L
                throw IOException("Reanudación inválida, vuelve a intentarlo")
            }
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} al descargar")
            }
            val body = response.body ?: throw IOException("Respuesta vacía del servidor")
            val append = existing > 0L && response.code == 206
            if (!append && existing > 0L) {
                partFile.delete()
                existing = 0L
            }
            val remaining = body.contentLength().coerceAtLeast(0L)
            val total = if (remaining > 0) existing + remaining else item.sizeBytes

            FileOutputStream(partFile, append).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = existing
                    var lastTick = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (now - lastTick > 120L) {
                            lastTick = now
                            emitProgress(item, downloaded, total, DownloadStatus.RUNNING)
                        }
                    }
                    output.flush()
                    emitProgress(item, downloaded, total, DownloadStatus.RUNNING)
                }
            }
        }

        update(item.id, DownloadStatus.RUNNING, progress = 0.99f)
        val result = library.adoptDownload(item, partFile)
        result.onSuccess { wallpaper ->
            _downloads.update {
                it + (item.id to DownloadProgress(
                    itemId = item.id,
                    title = item.title,
                    progress = 1f,
                    bytesDownloaded = wallpaper.sizeBytes,
                    totalBytes = wallpaper.sizeBytes,
                    status = DownloadStatus.COMPLETED,
                ))
            }
            withContext(Dispatchers.IO) { delay(2500) }
            _downloads.update { it - item.id }
        }.onFailure { error ->
            throw IOException(error.message ?: "No se pudo guardar el archivo")
        }
    }

    private fun emitProgress(
        item: CatalogItem,
        downloaded: Long,
        total: Long,
        status: DownloadStatus,
    ) {
        val fraction = if (total > 0L) (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
        _downloads.update {
            it + (item.id to DownloadProgress(
                itemId = item.id,
                title = item.title,
                progress = fraction,
                bytesDownloaded = downloaded,
                totalBytes = total,
                status = status,
            ))
        }
    }

    private fun update(
        itemId: String,
        status: DownloadStatus,
        progress: Float? = null,
        message: String? = null,
    ) {
        _downloads.update { current ->
            val previous = current[itemId] ?: return@update current
            current + (itemId to previous.copy(
                status = status,
                message = message,
                progress = progress ?: previous.progress,
            ))
        }
    }

}
