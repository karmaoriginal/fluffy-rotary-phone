package app.lumora.work

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.flow.StateFlow

/** Schedules, cancels and observes enhancement jobs. */
class EnhancementTracker(
    private val workManager: WorkManager,
    private val progressStore: EnhancementProgressStore,
) {

    fun observe(): StateFlow<Map<Long, EnhanceStatus>> = progressStore.states

    fun schedule(wallpaperId: Long, targetHeight: Int) {
        val request = OneTimeWorkRequestBuilder<EnhanceWorker>()
            .setInputData(
                workDataOf(
                    EnhanceWorker.KEY_WALLPAPER_ID to wallpaperId,
                    EnhanceWorker.KEY_TARGET_HEIGHT to targetHeight,
                ),
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .addTag(EnhanceWorker.TAG)
            .build()

        progressStore.start(wallpaperId)
        workManager.enqueueUniqueWork(
            EnhanceWorker.uniqueWorkName(wallpaperId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancel(wallpaperId: Long) {
        workManager.cancelUniqueWork(EnhanceWorker.uniqueWorkName(wallpaperId))
        progressStore.finish(wallpaperId)
    }
}
