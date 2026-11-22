package app.lumora.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object Formatters {

    fun bytes(value: Long): String {
        if (value <= 0L) return "0 MB"
        val kb = value / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.getDefault(), "%.2f GB", gb)
            mb >= 1.0 -> String.format(Locale.getDefault(), "%.1f MB", mb)
            else -> String.format(Locale.getDefault(), "%.0f KB", kb)
        }
    }

    fun duration(millis: Long): String {
        if (millis <= 0L) return "0:00"
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }

    fun resolution(width: Int, height: Int): String {
        val short = minOf(width, height)
        if (short <= 0) return "—"
        val label = when {
            short >= 2160 -> "4K"
            short >= 1440 -> "1440p"
            short >= 1080 -> "1080p"
            short >= 720 -> "720p"
            short >= 480 -> "480p"
            else -> "${short}p"
        }
        return "$label · ${width}×${height}"
    }

    fun bitrate(bitsPerSecond: Long): String {
        if (bitsPerSecond <= 0L) return "—"
        val mbps = bitsPerSecond / 1_000_000.0
        return if (mbps >= 1.0) {
            String.format(Locale.getDefault(), "%.1f Mbps", mbps)
        } else {
            String.format(Locale.getDefault(), "%.0f kbps", bitsPerSecond / 1000.0)
        }
    }

    fun date(epochMillis: Long): String {
        if (epochMillis <= 0L) return "—"
        val formatter = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
        return formatter.format(Date(epochMillis))
    }

    fun percent(fraction: Float): String = "${(fraction.coerceIn(0f, 1f) * 100).roundToInt()}%"
}
