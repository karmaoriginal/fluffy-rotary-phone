package app.lumora.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.lumora.AppContainer
import app.lumora.BuildConfig
import app.lumora.data.wallpaper.LiveWallpaperDiagnostics
import app.lumora.designsystem.icons.LumoraIcons
import app.lumora.model.EnhanceTarget
import app.lumora.model.FrameRateLimit
import app.lumora.model.ThemeMode
import app.lumora.ui.ViewModelFactories
import app.lumora.ui.components.Chip
import app.lumora.ui.components.DividerSoft
import app.lumora.ui.components.KeyValueRow
import app.lumora.ui.components.ScreenHeader
import app.lumora.util.Formatters

@Composable
fun SettingsScreen(container: AppContainer) {
    val viewModel: SettingsViewModel = viewModel(factory = remember { ViewModelFactories.settings(container) })
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val cacheBytes by viewModel.cacheBytes.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var liveDiagnostics by remember { mutableStateOf<LiveWallpaperDiagnostics?>(null) }

    LaunchedEffect(Unit) { liveDiagnostics = container.wallpaperApplier.liveDiagnostics() }

    LaunchedEffect(message) {
        val text = message
        if (text != null) {
            snackbarHostState.showSnackbar(text)
            viewModel.consumeMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            ScreenHeader(title = "Ajustes", subtitle = "Apariencia, rendimiento y almacenamiento")

            SettingsSection(title = "Apariencia") {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Chip(
                        label = "Automático",
                        selected = settings.themeMode == ThemeMode.SYSTEM,
                        onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) },
                    )
                    Chip(
                        label = "Claro",
                        selected = settings.themeMode == ThemeMode.LIGHT,
                        onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) },
                    )
                    Chip(
                        label = "Oscuro",
                        selected = settings.themeMode == ThemeMode.DARK,
                        onClick = { viewModel.setThemeMode(ThemeMode.DARK) },
                    )
                }
                SettingsSwitch(
                    title = "Color dinámico (Material You)",
                    subtitle = "Toma los colores de tu fondo de pantalla",
                    checked = settings.dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor,
                )
                SettingsSwitch(
                    title = "Modo AMOLED",
                    subtitle = "Negro puro para ahorrar batería en pantallas OLED",
                    checked = settings.amoled,
                    onCheckedChange = viewModel::setAmoled,
                )
            }

            SettingsSection(title = "Fondos animados") {
                SettingsSwitch(
                    title = "Silenciar videos",
                    subtitle = "Recomendado: los fondos animados casi nunca necesitan audio",
                    checked = settings.muteVideo,
                    onCheckedChange = viewModel::setMuteVideo,
                )
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Límite de FPS",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Chip(
                        label = "30",
                        selected = settings.frameRateLimit == FrameRateLimit.FPS_30,
                        onClick = { viewModel.setFrameRate(FrameRateLimit.FPS_30) },
                    )
                    Chip(
                        label = "60",
                        selected = settings.frameRateLimit == FrameRateLimit.FPS_60,
                        onClick = { viewModel.setFrameRate(FrameRateLimit.FPS_60) },
                    )
                }
                Text(
                    text = "El fondo animado se pausa al apagar la pantalla, al abrir otra app " +
                        "y al activar el ahorro de batería.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                liveDiagnostics?.let { info ->
                    KeyValueRow(
                        label = "Fondo del inicio",
                        value = info.activeWallpaper ?: "Estático (ningún fondo animado)",
                    )
                    KeyValueRow(
                        label = "Video asignado",
                        value = info.videoName ?: "Sin asignar",
                    )
                    KeyValueRow(
                        label = "Archivo del video",
                        value = if (info.videoExists) {
                            "Disponible · ${Formatters.bytes(info.videoBytes)}"
                        } else {
                            "No encontrado"
                        },
                    )
                    KeyValueRow(
                        label = "Primer fotograma",
                        value = if (info.posterAvailable) "Listo" else "No disponible",
                    )
                    val error = info.lastError
                    if (error != null) {
                        Text(
                            text = "Último error de reproducción: $error",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            SettingsSection(title = "Mejora de video") {
                SettingsSwitch(
                    title = "Ofrecer mejora al importar",
                    subtitle = "Escalado Lanczos + reducción de ruido + H.265, una sola vez y en segundo plano",
                    checked = settings.autoEnhance,
                    onCheckedChange = viewModel::setAutoEnhance,
                )
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Resolución objetivo",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    EnhanceTarget.entries.forEach { target ->
                        Chip(
                            label = target.label,
                            selected = settings.enhanceTarget == target,
                            onClick = { viewModel.setEnhanceTarget(target) },
                        )
                    }
                }
                SettingsSwitch(
                    title = "Upscaler con IA (experimental)",
                    subtitle = "Opcional, desactivado por defecto. Requiere el backend FFmpeg/AI descrito en la documentación",
                    checked = settings.aiUpscaler,
                    onCheckedChange = viewModel::setAiUpscaler,
                )
            }

            SettingsSection(title = "Red y almacenamiento") {
                SettingsSwitch(
                    title = "Descargar solo por Wi-Fi",
                    subtitle = "Evita consumir datos móviles en la tienda",
                    checked = settings.wifiOnly,
                    onCheckedChange = viewModel::setWifiOnly,
                )
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Límite de caché",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${settings.cacheLimitMb} MB",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Slider(
                        value = settings.cacheLimitMb.toFloat(),
                        onValueChange = { viewModel.setCacheLimit(it.toInt()) },
                        valueRange = 64f..1024f,
                        steps = 14,
                    )
                }
                KeyValueRow(label = "Caché usada", value = Formatters.bytes(cacheBytes))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = viewModel::clearCaches) {
                        Icon(
                            imageVector = LumoraIcons.Download,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Limpiar caché")
                    }
                }
            }

            SettingsSection(title = "Acerca de") {
                KeyValueRow(label = "Versión", value = BuildConfig.VERSION_NAME)
                KeyValueRow(label = "Backend de mejora", value = container.enhancer.displayName)
                KeyValueRow(
                    label = "FFmpeg opcional",
                    value = if (BuildConfig.ENABLE_FFMPEG) "Activado" else "Desactivado",
                )
                KeyValueRow(label = "Licencia", value = "MIT · contenido CC0")
                Text(
                    text = "Lumora nunca sube tus archivos: todo el procesado ocurre en tu dispositivo.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }

            Spacer(modifier = Modifier.height(120.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp),
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 22.dp, top = 10.dp, bottom = 4.dp),
        )
        Box(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 8.dp),
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun SettingsSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
    DividerSoft(modifier = Modifier.padding(horizontal = 20.dp))
}
