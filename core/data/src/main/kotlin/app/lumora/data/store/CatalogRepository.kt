package app.lumora.data.store

import android.content.Context
import app.lumora.model.Catalog
import app.lumora.model.CatalogCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

enum class CatalogSource { NETWORK, DISK, BUNDLED, EMPTY }

data class CatalogResult(
    val catalog: Catalog,
    val source: CatalogSource,
    val message: String? = null,
)

/**
 * Loads `catalog.json` (published from `docs/` with GitHub Pages).
 *
 * Resilience order: network (Pages) -> network (raw.githubusercontent mirror) -> disk cache
 * -> catalog bundled in the APK assets. The store therefore always renders something.
 */
class CatalogRepository(
    private val context: Context,
    private val client: OkHttpClient,
    private val primaryUrl: String,
    private val mirrorUrl: String,
    private val cacheFile: File,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        isLenient = true
    }

    @Volatile
    private var memory: CatalogResult? = null

    @Volatile
    private var memoryTimestamp: Long = 0L

    suspend fun load(forceRefresh: Boolean = false): CatalogResult = withContext(Dispatchers.IO) {
        val cached = memory
        if (!forceRefresh && cached != null && System.currentTimeMillis() - memoryTimestamp < CACHE_TTL_MS) {
            return@withContext cached
        }

        val remote = fetchRemotely()
        if (remote != null) {
            return@withContext remember(CatalogResult(remote, CatalogSource.NETWORK))
        }

        val fromDisk = readCacheFile()
        if (fromDisk != null) {
            return@withContext remember(
                CatalogResult(fromDisk, CatalogSource.DISK, "Sin conexión: catálogo en caché"),
            )
        }

        val bundled = readBundledCatalog()
        if (bundled != null) {
            return@withContext remember(
                CatalogResult(bundled, CatalogSource.BUNDLED, "Sin conexión: catálogo incluido"),
            )
        }

        remember(
            CatalogResult(
                catalog = Catalog(categories = DEFAULT_CATEGORIES),
                source = CatalogSource.EMPTY,
                message = "No se pudo cargar el catálogo",
            ),
        )
    }

    private fun remember(result: CatalogResult): CatalogResult {
        memory = result
        memoryTimestamp = System.currentTimeMillis()
        return result
    }

    private fun fetchRemotely(): Catalog? {
        for (url in listOf(primaryUrl, mirrorUrl)) {
            val parsed = runCatching {
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .header("Cache-Control", "no-cache")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code} for $url")
                    val body = response.body?.string() ?: error("Empty body for $url")
                    json.decodeFromString<Catalog>(body)
                }
            }.getOrNull()
            if (parsed != null) {
                runCatching {
                    cacheFile.parentFile?.mkdirs()
                    cacheFile.writeText(json.encodeToString(Catalog.serializer(), parsed))
                }
                return parsed
            }
        }
        return null
    }

    private fun readCacheFile(): Catalog? = runCatching {
        if (!cacheFile.exists() || cacheFile.length() == 0L) null
        else json.decodeFromString<Catalog>(cacheFile.readText())
    }.getOrNull()

    private fun readBundledCatalog(): Catalog? = runCatching {
        context.assets.open("catalog_fallback.json").bufferedReader().use { it.readText() }
            .let { json.decodeFromString<Catalog>(it) }
    }.getOrNull()

    companion object {
        private const val CACHE_TTL_MS = 10 * 60 * 1000L

        val DEFAULT_CATEGORIES = listOf(
            CatalogCategory("anime", "Anime", "#FF6FA8"),
            CatalogCategory("paisajes", "Paisajes", "#4ECCA3"),
            CatalogCategory("agua", "Agua", "#3FA9F5"),
            CatalogCategory("colores", "Colores", "#FFB347"),
            CatalogCategory("minimalista", "Minimalista", "#B8B8C8"),
            CatalogCategory("espacio", "Espacio", "#7B5CFF"),
            CatalogCategory("naturaleza", "Naturaleza", "#63D471"),
            CatalogCategory("abstracto", "Abstracto", "#FF5C9D"),
            CatalogCategory("oscuro", "Oscuro / AMOLED", "#8A8A9A"),
            CatalogCategory("ciudad", "Ciudad", "#F2C14E"),
        )
    }
}
