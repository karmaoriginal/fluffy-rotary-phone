package app.lumora.data.enhance

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.effect.Contrast
import androidx.media3.effect.GaussianBlur
import androidx.media3.effect.LanczosResample
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * One-shot, offline enhancement of an imported video.
 *
 * Pipeline: gentle spatial denoise -> Lanczos resampling to the requested resolution ->
 * light clarity/contrast -> H.265 encode without audio (wallpapers never need sound), with an
 * automatic H.264 fallback when the device has no HEVC encoder.
 */
class Media3VideoEnhancer(private val context: Context) : VideoEnhancer {

    override val id: String = "media3-lanczos-hevc"
    override val displayName: String = "Lanczos + H.265"

    override suspend fun enhance(
        request: EnhanceRequest,
        onProgress: (Float) -> Unit,
    ): EnhanceResult = withContext(Dispatchers.IO) {
        val first = runCatching { transcode(request, hevc = true, onProgress = onProgress) }
        first.getOrNull()?.let { return@withContext it }

        val error = first.exceptionOrNull()
        if (error is CancellationException) throw error
        // Retry without HEVC: some devices expose no H.265 encoder.
        runCatching { transcode(request, hevc = false, onProgress = onProgress) }
            .getOrElse { fallbackError ->
                if (fallbackError is CancellationException) throw fallbackError
                throw IllegalStateException(
                    "No se pudo mejorar el video: ${fallbackError.message ?: error?.message}",
                    fallbackError,
                )
            }
    }

    private suspend fun transcode(
        request: EnhanceRequest,
        hevc: Boolean,
        onProgress: (Float) -> Unit,
    ): EnhanceResult {
        val output = request.output
        output.parentFile?.mkdirs()
        if (output.exists()) output.delete()

        val thread = HandlerThread("lumora-transformer-${System.nanoTime()}")
        thread.start()
        val looper = thread.looper
        val handler = Handler(looper)

        try {
            val transformer = createTransformer(looper, hevc)
            val editedMediaItem = createEditedMediaItem(request, hevc, transformer)

            return suspendCancellableCoroutine { continuation ->
                val finished = AtomicBoolean(false)
                val poller = object : Runnable {
                    override fun run() {
                        if (finished.get()) return
                        val holder = ProgressHolder()
                        val state = runCatching { transformer.getProgress(holder) }.getOrDefault(-1)
                        if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                            onProgress((holder.progress / 100f).coerceIn(0f, 1f))
                        }
                        handler.postDelayed(this, 250L)
                    }
                }

                transformer.addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (!finished.compareAndSet(false, true)) return
                        onProgress(1f)
                        continuation.resume(
                            EnhanceResult(
                                outputFile = output,
                                width = request.targetWidth,
                                height = request.targetHeight,
                                sizeBytes = output.length(),
                                backend = id,
                                hevc = hevc,
                            ),
                        )
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        if (!finished.compareAndSet(false, true)) return
                        output.delete()
                        continuation.resumeWithException(
                            IllegalStateException(exportException.message ?: "Error de exportación", exportException),
                        )
                    }
                })

                handler.postDelayed(poller, 250L)
                handler.post {
                    if (!currentThreadIsActive()) {
                        finished.set(true)
                        if (continuation.isActive) {
                            continuation.resumeWithException(CancellationException("Enhancement cancelled"))
                        }
                        return@post
                    }
                    runCatching { transformer.start(editedMediaItem, output.absolutePath) }
                        .onFailure { error ->
                            if (finished.compareAndSet(false, true)) {
                                continuation.resumeWithException(error)
                            }
                        }
                }

                continuation.invokeOnCancellation {
                    finished.set(true)
                    handler.post { runCatching { transformer.cancel() } }
                    output.delete()
                }
            }
        } finally {
            handler.removeCallbacksAndMessages(null)
            thread.quitSafely()
        }
    }

    private fun currentThreadIsActive(): Boolean = true

    private fun createTransformer(looper: android.os.Looper, hevc: Boolean): Transformer =
        Transformer.Builder(context)
            .setLooper(looper)
            .setVideoMimeType(if (hevc) MimeTypes.VIDEO_H265 else MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .build()

    private fun createEditedMediaItem(
        request: EnhanceRequest,
        hevc: Boolean,
        transformer: Transformer,
    ): EditedMediaItem {
        val mediaItem = MediaItem.Builder().setUri(Uri.fromFile(request.input)).build()
        val effects = buildList<Effect> {
            if (request.denoise) {
                // Mild spatial denoise (hqdn3d-like) that keeps edges usable for the Lanczos pass.
                add(GaussianBlur(0.55f))
            }
            add(LanczosResample.scaleToFit(request.targetWidth, request.targetHeight))
            if (request.clarity) add(Contrast(0.06f))
        }
        return EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(emptyList<AudioProcessor>(), effects))
            .setRemoveAudio(true)
            .build()
    }

    @Suppress("unused")
    private suspend fun ensureActive() {
        if (!currentCoroutineContext().isActive) throw CancellationException("Enhancement cancelled")
    }
}
