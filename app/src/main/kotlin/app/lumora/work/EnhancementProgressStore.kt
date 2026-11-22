package app.lumora.work

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class EnhanceStatus(
    val wallpaperId: Long,
    val progress: Float,
    val running: Boolean,
    val failed: Boolean = false,
    val error: String? = null,
)

/**
 * In-process progress of enhancement jobs.
 *
 * WorkManager owns scheduling, persistence and cancellation; this store only feeds the UI with
 * smooth progress without polling `WorkInfo` (which would allocate on every query).
 */
class EnhancementProgressStore {

    private val _states = MutableStateFlow<Map<Long, EnhanceStatus>>(emptyMap())
    val states: StateFlow<Map<Long, EnhanceStatus>> = _states.asStateFlow()

    fun start(wallpaperId: Long) = put(EnhanceStatus(wallpaperId, 0f, running = true))

    fun progress(wallpaperId: Long, fraction: Float) =
        put(EnhanceStatus(wallpaperId, fraction.coerceIn(0f, 1f), running = true))

    fun fail(wallpaperId: Long, message: String?) =
        put(EnhanceStatus(wallpaperId, 0f, running = false, failed = true, error = message))

    fun finish(wallpaperId: Long) {
        _states.update { it - wallpaperId }
    }

    fun clear() {
        _states.update { emptyMap() }
    }

    private fun put(status: EnhanceStatus) {
        _states.update { it + (status.wallpaperId to status) }
    }
}
