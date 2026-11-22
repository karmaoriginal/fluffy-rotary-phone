# Backend de mejora opcional: FFmpeg y upscaler con IA

Lumora trae **por defecto** un backend de mejora 100 % Media3 (`Media3VideoEnhancer`):

```
denoise suave (GaussianBlur) → Lanczos (LanczosResample) → claridad (Contrast) → H.265 sin audio
```

Ese backend no añade dependencias nativas (~0 MB al APK) y funciona en todos los dispositivos.
Si necesitas los filtros exactos de FFmpeg (`hqdn3d`, `unsharp`, `scale=lanczos`) o un upscaler
con IA, puedes enchufar otro backend sin tocar la UI: todos implementan la misma interfaz
`app.lumora.data.enhance.VideoEnhancer`.

## 1. Contrato

```kotlin
interface VideoEnhancer {
    val id: String
    val displayName: String
    suspend fun enhance(request: EnhanceRequest, onProgress: (Float) -> Unit): EnhanceResult
}

data class EnhanceRequest(
    val input: File,
    val output: File,
    val targetWidth: Int,
    val targetHeight: Int,
    val denoise: Boolean = true,
    val clarity: Boolean = true,
)
```

`EnhanceWorker` (WorkManager) llama siempre a la instancia que expone `AppContainer.enhancer`, así
que basta con cambiar una línea:

```kotlin
// app/src/main/kotlin/app/lumora/AppContainer.kt
val enhancer: VideoEnhancer by lazy {
    if (BuildConfig.ENABLE_FFMPEG) FFmpegVideoEnhancer(appContext) else Media3VideoEnhancer(appContext)
}
```

## 2. Con ffmpeg-kit (AVPacket / Arthenica)

> ⚠️ El proyecto `ffmpeg-kit` fue retirado (2025) y sus artefactos ya no se publican en Maven
> Central. Si aún lo tienes cacheado o compilas tu propio AAR, este es el código de referencia.
> Alternativas mantenidas: compilar FFmpeg con los códecs necesarios (`scale`, `hqdn3d`,
> `unsharp`, `libx265`) y exponerlo por JNI, o usar `mobile-ffmpeg`/forks comunitarios.

```kotlin
// app/build.gradle.kts  (añadir solo si tienes el artefacto disponible)
dependencies {
    implementation("com.arthenica:ffmpeg-kit-video:6.0-2")
}
```

```kotlin
package app.lumora.data.enhance

import android.content.Context
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.Statistics
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FFmpegVideoEnhancer(private val context: Context) : VideoEnhancer {

    override val id = "ffmpeg-hqdn3d-unsharp"
    override val displayName = "FFmpeg (lanczos + hqdn3d + unsharp, H.265)"

    override suspend fun enhance(
        request: EnhanceRequest,
        onProgress: (Float) -> Unit,
    ): EnhanceResult = suspendCancellableCoroutine { cont ->
        val command = buildString {
            append("-y -i \"${request.input.absolutePath}\" ")
            append("-vf \"scale=${request.targetWidth}:${request.targetHeight}:flags=lanczos,")
            append("hqdn3d=4:3:6:4.5,unsharp=5:5:0.8:3:3:0.4\" ")
            append("-c:v libx265 -preset medium -crf 24 -tag:v hvc1 -an ")
            append("-movflags +faststart \"${request.output.absolutePath}\"")
        }
        val session: FFmpegSession = FFmpegKit.executeAsync(
            command,
            { finished ->
                val code = finished.returnCode
                if (code.isValueSuccess) {
                    cont.resume(
                        EnhanceResult(
                            outputFile = request.output,
                            width = request.targetWidth,
                            height = request.targetHeight,
                            sizeBytes = request.output.length(),
                            backend = id,
                            hevc = true,
                        ),
                    )
                } else {
                    cont.resumeWithException(IllegalStateException("FFmpeg falló: ${finished.failStackTrace}"))
                }
            },
            null,
            { statistics: Statistics ->
                val time = statistics.time.toFloat()
                if (time > 0) onProgress((time / 100f).coerceIn(0f, 1f))
            },
        )
        cont.invokeOnCancellation { session.cancel() }
    }
}
```

Guarda el APK pequeño: usa un build de ffmpeg-kit **solo con los códecs necesarios** (sin
`-full-gpl`), activa `abiFilters`/splits y recuerda que el binario nativo suma ~10–25 MB.

## 3. Upscaler con IA (opcional, desactivado)

El ajuste **"Upscaler con IA (experimental)"** (`AppSettings.aiUpscaler`, off por defecto) está
pensado para un modelo ligero tipo ESRGAN/Real-ESRGAN en `.tflite` o `.onnx`:

```kotlin
interface AiUpscaler {
    /** Escala un frame; se aplica frame a frame antes de recodificar. */
    suspend fun upscaleFrame(bitmap: Bitmap): Bitmap
}
```

Pasos sugeridos:

1. Empaqueta el modelo en `app/src/main/assets/models/` (usa un `.tflite` cuantizado de ~1–3 MB).
2. Decodifica con `MediaCodec` (o Media3 Transformer + `GlEffect`) y aplica el modelo a cada frame.
3. Recodifica con `MediaMuxer`/Transformer en H.265.
4. Expón la implementación como otro `VideoEnhancer` (`id = "ai-esrgan"`) y selecciónala solo si
   `settings.aiUpscaler` está activo y el dispositivo tiene NNAPI/GPU.

Mientras el modelo no exista, el ajuste se mantiene visible pero el pipeline usa el backend
Media3, de modo que la app nunca depende de un binario o modelo ausente.
