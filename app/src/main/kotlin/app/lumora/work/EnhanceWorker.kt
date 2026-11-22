package app.lumora.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.lumora.data.enhance.EnhanceAdvisor
import app.lumora.data.enhance.EnhanceRequest
import app.lumora.data.enhance.VideoEnhancer
import app.lumora.data.library.LibraryRepository
import app.lumora.data.storage.FileStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File

/**
 * One-shot, offline video enhancement executed by WorkManager so the UI can be closed,
 * progress can be observed and the job can be cancelled cleanly.
 */
class EnhanceWorker(
    appContext: Context,
    params: WorkerParameters,
    private val library: LibraryRepository,
    private val enhancer: VideoEnhancer,
    private val fileStore: FileStore,
    private val progressStore: EnhancementProgressStore,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val wallpaperId = inputData.getLong(KEY_WALLPAPER_ID, INVALID_ID)
        if (wallpaperId == INVALID_ID) return Result.failure()

        val wallpaper = library.find(wallpaperId) ?: return Result.failure()
        val input = File(wallpaper.filePath)
        if (!input.exists()) return Result.failure()

        val targetHeight = inputData.getInt(KEY_TARGET_HEIGHT, 1080)
        val (width, height) = EnhanceAdvisor.targetDimensions(
            width = wallpaper.width,
            height = wallpaper.height,
            targetShortSide = targetHeight,
        )
        val output = fileStore.enhanceOutputFor(input)
        val request = EnhanceRequest(
            input = input,
            output = output,
            targetWidth = width,
            targetHeight = height,
        )

        progressStore.start(wallpaperId)
        val channel = Channel<Float>(Channel.CONFLATED)
        val reporterScope = CoroutineScope(coroutineContext + SupervisorJob())
        val reporter = reporterScope.launch {
            for (fraction in channel) {
                runCatching {
                    setProgress(
                        workDataOf(KEY_WALLPAPER_ID to wallpaperId, KEY_PROGRESS to fraction),
                    )
                }
                progressStore.progress(wallpaperId, fraction)
            }
        }

        return try {
            val result = enhancer.enhance(request) { fraction -> channel.trySend(fraction) }
            val updated = library.replaceWithEnhanced(
                id = wallpaperId,
                enhancedFile = result.outputFile,
                width = result.width,
                height = result.height,
            )
            if (updated == null) {
                progressStore.fail(wallpaperId, "No se pudo guardar el video mejorado")
                Result.failure(workDataOf(KEY_ERROR to "No se pudo guardar el video mejorado"))
            } else {
                progressStore.finish(wallpaperId)
                Result.success(workDataOf(KEY_WALLPAPER_ID to wallpaperId))
            }
        } catch (cancellation: CancellationException) {
            fileStore.deleteQuietly(output)
            progressStore.finish(wallpaperId)
            throw cancellation
        } catch (throwable: Throwable) {
            fileStore.deleteQuietly(output)
            progressStore.fail(wallpaperId, throwable.message)
            Result.failure(
                workDataOf(
                    KEY_WALLPAPER_ID to wallpaperId,
                    KEY_ERROR to (throwable.message ?: "Error desconocido"),
                ),
            )
        } finally {
            channel.close()
            reporter.cancel()
            reporterScope.cancel()
        }
    }

    companion object {
        const val KEY_WALLPAPER_ID = "wallpaper_id"
        const val KEY_TARGET_HEIGHT = "target_height"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"
        const val INVALID_ID = -1L
        const val TAG = "lumora-enhance"

        fun uniqueWorkName(wallpaperId: Long): String = "enhance-$wallpaperId"
    }
}
