package app.lumora

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import androidx.work.WorkManager
import app.lumora.work.LumoraWorkerFactory
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import java.io.File

class LumoraApplication : Application(), ImageLoaderFactory, Configuration.Provider {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Trim the on-disk caches on a cold start (cheap, keeps storage bounded).
        val limitMb = 256
        runCatching { container.fileStore.trimCache(limitMb.toLong() * 1024L * 1024L) }
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(container.httpClient)
        .components {
            add(GifDecoder.Factory())
            add(VideoFrameDecoder.Factory())
        }
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.20)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(File(cacheDir, "coil_cache"))
                .maxSizeBytes(192L * 1024L * 1024L)
                .build()
        }
        .crossfade(200)
        .respectCacheHeaders(true)
        .build()

    override val workManagerConfiguration: Configuration
        get() {
            if (!::container.isInitialized) {
                Log.d(TAG, "WorkManager configuration requested before container init")
                container = AppContainer(this)
            }
            return Configuration.Builder()
                .setWorkerFactory(LumoraWorkerFactory(container))
                .setMinimumLoggingLevel(Log.INFO)
                .build()
        }

    fun workManager(): WorkManager = WorkManager.getInstance(this)

    private companion object {
        const val TAG = "LumoraApplication"
    }
}
