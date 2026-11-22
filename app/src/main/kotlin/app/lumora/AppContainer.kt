package app.lumora

import android.content.Context
import androidx.work.WorkManager
import app.lumora.data.db.LumoraDatabase
import app.lumora.data.enhance.Media3VideoEnhancer
import app.lumora.data.enhance.VideoEnhancer
import app.lumora.data.library.LibraryRepository
import app.lumora.data.net.ConnectivityChecker
import app.lumora.data.settings.SettingsRepository
import app.lumora.data.storage.FileStore
import app.lumora.data.store.CatalogRepository
import app.lumora.data.store.DownloadCoordinator
import app.lumora.data.store.StoreFavoritesRepository
import app.lumora.data.wallpaper.LiveWallpaperConfig
import app.lumora.data.wallpaper.WallpaperApplier
import app.lumora.work.EnhancementProgressStore
import app.lumora.work.EnhancementTracker
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Manual dependency container: tiny, explicit and free of annotation processors.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .cache(Cache(File(appContext.cacheDir, "http"), 64L * 1024L * 1024L))
            .build()
    }

    val fileStore: FileStore by lazy { FileStore(appContext) }

    val database: LumoraDatabase by lazy { LumoraDatabase.build(appContext) }

    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }

    val liveConfig: LiveWallpaperConfig by lazy { LiveWallpaperConfig(appContext) }

    val library: LibraryRepository by lazy {
        LibraryRepository(appContext, database.wallpaperDao(), fileStore)
    }

    val favorites: StoreFavoritesRepository by lazy { StoreFavoritesRepository(appContext) }

    val catalog: CatalogRepository by lazy {
        CatalogRepository(
            context = appContext,
            client = httpClient,
            primaryUrl = BuildConfig.CATALOG_URL,
            mirrorUrl = BuildConfig.CATALOG_FALLBACK_URL,
            cacheFile = File(appContext.cacheDir, "catalog.json"),
        )
    }

    val connectivity: ConnectivityChecker by lazy { ConnectivityChecker(appContext) }

    val downloads: DownloadCoordinator by lazy {
        DownloadCoordinator(
            client = httpClient,
            fileStore = fileStore,
            library = library,
            settings = settings,
            connectivity = connectivity,
        )
    }

    val enhancer: VideoEnhancer by lazy { Media3VideoEnhancer(appContext) }

    val wallpaperApplier: WallpaperApplier by lazy {
        WallpaperApplier(appContext, liveConfig)
    }

    val enhancementProgress: EnhancementProgressStore by lazy { EnhancementProgressStore() }

    val enhancementTracker: EnhancementTracker by lazy {
        EnhancementTracker(WorkManager.getInstance(appContext), enhancementProgress)
    }

    /** Frees resumable-download leftovers, enhancement scratch files and Coil's caches. */
    suspend fun clearAllCaches(): Long = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val freed = library.clearCaches()
        fileStore.clearThumbs()
        runCatching {
            val loader = coil.Coil.imageLoader(appContext)
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
        }
        freed
    }

    fun cacheSizeBytes(): Long = fileStore.cacheBytes() + runCatching {
        coil.Coil.imageLoader(appContext).diskCache?.size ?: 0L
    }.getOrDefault(0L)
}
