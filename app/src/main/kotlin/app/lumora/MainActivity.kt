package app.lumora

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lumora.designsystem.theme.LumoraTheme
import app.lumora.model.AppSettings
import app.lumora.model.ThemeMode
import app.lumora.ui.LumoraApp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as LumoraApplication).container

        setContent {
            val settings by container.settings.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            LumoraTheme(
                darkTheme = darkTheme,
                amoled = settings.amoled,
                dynamicColor = settings.dynamicColor,
            ) {
                LumoraApp(container = container)
            }
        }
    }
}
