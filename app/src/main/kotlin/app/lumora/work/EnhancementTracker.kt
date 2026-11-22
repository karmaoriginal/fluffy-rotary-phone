package app.lumora.work

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.lumora.data.storage.FileStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class EnhanceStatus(
    val wallpaperId: Long,
    val progress: Float,
    val running: Boolean,
    val failed: Boolean,
    val error: String? = null,
)

/** Observes and controls enhancement jobs, exposing them as a simple Flow for the UI. */
class EnhancementTracker(
    private val workManager: WorkManager,
    private val fileStore: FileStore,
) {

    fun observe(): Flow<Map<Long, EnhanceStatus>> =
        workManager.getWorkInfosByTagFlow(EnhanceWorker.TAG).map { infos ->
            infos.mapNotNull { it.toStatus() }
                .filter { it.running }
                .associateBy { it.wallpaperId }
        }

    fun schedule(wallpaperId: Long, targetHeight: Int, existingOriginal: Boolean = true) {
        val constraints = Constraints.Builder()
            .setRequiresStorageNotLow(true)
            .build()
        val request = OneTimeWorkRequestBuilder<EnhanceWorker>()
            .setInputData(
                workDataOf(
                    EnhanceWorker.KEY_WALLPAPER_ID to wallpaperId,
                    EnhanceWorker.KEY_TARGET_HEIGHT to targetHeight,
                ),
            )
            .setConstraints(constraints)
            .addTag(EnhanceWorker.TAG)
            .build()
        workManager.enqueueUniqueWork(
            EnhanceWorker.uniqueWorkName(wallpaperId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
        if (!existingOriginal) fileStore.trimCache(0L)
    }

    fun cancel(wallpaperId: Long) {
        workManager.cancelUniqueWork(EnhanceWorker.uniqueWorkName(wallpaperId))
    }

    private fun WorkInfo.toStatus(): EnhanceStatus? {
        val id = progress.getLong(EnhanceWorker.KEY_WALLPAPER_ID, EnhanceWorker.INVALID_ID)
            .takeIf { it != EnhanceWorker.INVALID_ID }
            ?: inputData.getLong(EnhanceWorker.KEY_WALLPAPER_ID, EnhanceWorker.INVALID_ID)
                .takeIf { it != EnhanceWorker.INVALID_ID }
            ?: return null
        val running = state == WorkInfo.State.ENQUEUED || state == WorkInfo.State.RUNNING || state == WorkInfo.State.BLOCKED
        return EnhanceStatus(
            wallpaperId = id,
            progress = progress.getFloat(EnhanceWorker.KEY_PROGRESS, 0f),
            running = running,
            failed = state == WorkInfo.State.FAILED,
            error = progress.getString(EnhanceWorker.KEY_ERROR),
        )
    }
}
