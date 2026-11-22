# Licencias del contenido y de las dependencias

## Contenido de la tienda

Todo el contenido inicial de Lumora es **arte original** creado para este proyecto
(ilustraciones generadas y video loops derivados de ellas, postprocesados con ImageMagick y FFmpeg).

Se dedica al **dominio público bajo CC0 1.0 Universal**:
<https://creativecommons.org/publicdomain/zero/1.0/>

Eso significa que puedes copiar, modificar, distribuir y usar el contenido, incluso
comercialmente, **sin pedir permiso ni dar atribución**. La atribución a "Lumora Studio" es
bienvenida pero no obligatoria.

| Archivo | Tipo | Licencia |
|---|---|---|
| `docs/content/full/*.webp` | Imágenes 941×1672 (≈9:16) | CC0 1.0 |
| `docs/content/thumbs/*.webp` | Miniaturas 400 px | CC0 1.0 |
| `docs/content/videos/*.mp4` | Loops 720×1280, 8 s, H.264 | CC0 1.0 |
| `docs/content/previews/*.mp4` | Previews 480 px, 4 s | CC0 1.0 |

Los videos son bucles **ping-pong** (zoom de ida y vuelta) para que el salto del loop sea
imperceptible.

### Reglas para contribuir contenido

1. Solo **CC0, dominio público o arte original propio**. Nada de material con copyright, marcas
   registradas, personajes existentes ni capturas de otras apps.
2. Si usas un banco de imágenes, respeta su licencia y **documenta la fuente** en `sourceUrl`
   del manifiesto. Fuentes recomendadas: [Pexels](https://www.pexels.com/license/),
   [Pixabay](https://pixabay.com/service/license-summary/),
   [Unsplash](https://unsplash.com/license), [Openverse](https://openverse.org/) filtrando CC0.
3. Nada de contenido NSFW, violencia explícita ni material que infrinja derechos de terceros.
4. Ejecuta `python3 tools/build_catalog.py` antes de hacer commit: el catálogo debe generarse,
   no editarse a mano.

## Dependencias de código

| Dependencia | Licencia |
|---|---|
| AndroidX / Jetpack Compose | Apache 2.0 |
| AndroidX Media3 (ExoPlayer, Transformer) | Apache 2.0 |
| AndroidX Room, WorkManager, DataStore, Lifecycle | Apache 2.0 |
| Coil | Apache 2.0 |
| OkHttp / Okio | Apache 2.0 |
| Kotlin / kotlinx.coroutines / kotlinx.serialization | Apache 2.0 |
| Material Icons (subset incluido) | Apache 2.0 |

El código de Lumora se distribuye bajo **MIT** (ver `LICENSE`).
