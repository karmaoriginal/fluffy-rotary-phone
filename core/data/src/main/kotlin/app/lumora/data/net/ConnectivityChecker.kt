package app.lumora.data.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

class ConnectivityChecker(context: Context) {

    private val manager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun isOnline(): Boolean {
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork ?: return false)
            ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun isUnmetered(): Boolean {
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork ?: return false)
            ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    /** Respects the "only over Wi-Fi" setting before starting a download. */
    fun canDownload(wifiOnly: Boolean): Boolean = isOnline() && (!wifiOnly || isUnmetered())
}
