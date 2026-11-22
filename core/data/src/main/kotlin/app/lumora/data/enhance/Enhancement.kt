package app.lumora.data.enhance

import app.lumora.model.EnhanceTarget
import java.io.File
import kotlin.math.roundToInt

data class EnhanceRequest(
    val input: File,
    val output: File,
    val targetWidth: Int,
    val targetHeight: Int,
    val denoise: Boolean = true,
    val clarity: Boolean = true,
)

data class EnhanceResult(
    val outputFile: File,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val backend: String,
    val hevc: Boolean,
)

/**
 * Pluggable video enhancement backend.
 *
 * The shipped implementation is [Media3VideoEnhancer]: Lanczos resampling + light denoise +
 * clarity, encoded to H.265 (HEVC) to keep the file small. An optional FFmpeg backend
 * (ffmpeg-kit: `scale=lanczos,hqdn3d,unsharp`, H.265) and an optional AI upscaler can be plugged
 * in behind this same interface - see `docs/OPTIONAL_FFMPEG.md`.
 */
interface VideoEnhancer {
    val id: String
    val displayName: String

    suspend fun enhance(request: EnhanceRequest, onProgress: (Float) -> Unit): EnhanceResult
}

data class EnhanceRecommendation(
    val reason: String,
    val targetWidth: Int,
    val targetHeight: Int,
    val target: EnhanceTarget,
)

/**
 * Decides whether a video would visibly benefit from the (expensive) one-shot enhancement.
 * Runs at import time only, so the user can accept or skip it.
 */
object EnhanceAdvisor {

    private const val LOW_BITRATE_THRESHOLD = 2_000_000L

    fun recommend(
        width: Int,
        height: Int,
        bitrate: Long,
        target: EnhanceTarget,
    ): EnhanceRecommendation? {
        if (width <= 0 || height <= 0) return null
        val shortSide = minOf(width, height)
        val lowResolution = shortSide < target.height * 3 / 4
        val lowBitrate = bitrate in 1L until LOW_BITRATE_THRESHOLD
        if (!lowResolution && !lowBitrate) return null

        val (targetWidth, targetHeight) = targetDimensions(width, height, target.height)
        val reason = when {
            lowResolution && lowBitrate -> "Resolución (${shortSide}p) y bitrate bajos"
            lowResolution -> "Resolución baja (${shortSide}p)"
            else -> "Bitrate bajo (${bitrate / 1000} kbps)"
        }
        return EnhanceRecommendation(
            reason = reason,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            target = target,
        )
    }

    /** Fits the frame inside a box whose short side is [targetShortSide], keeping the aspect. */
    fun targetDimensions(width: Int, height: Int, targetShortSide: Int): Pair<Int, Int> {
        val aspect = width.toFloat() / height.toFloat().coerceAtLeast(1f)
        return if (width <= height) {
            even(targetShortSide) to even((targetShortSide / aspect).roundToInt())
        } else {
            even((targetShortSide * aspect).roundToInt()) to even(targetShortSide)
        }
    }

    private fun even(value: Int): Int {
        val clamped = value.coerceIn(2, 4096)
        if (clamped % 2 == 0) return clamped
        return if (clamped + 1 <= 4096) clamped + 1 else clamped - 1
    }
}
