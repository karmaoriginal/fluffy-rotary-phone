package app.lumora.work

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import app.lumora.AppContainer

/** Wires WorkManager to the manual DI container. */
class LumoraWorkerFactory(private val container: AppContainer) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = when (workerClassName) {
        EnhanceWorker::class.java.name -> EnhanceWorker(
            appContext = appContext,
            params = workerParameters,
            library = container.library,
            enhancer = container.enhancer,
            fileStore = container.fileStore,
        )

        else -> null
    }
}
