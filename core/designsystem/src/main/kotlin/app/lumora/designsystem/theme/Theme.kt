package app.lumora.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// Brand palette
// ---------------------------------------------------------------------------

val AuroraViolet = Color(0xFF7B5CFF)
val AuroraTeal = Color(0xFF25D0C0)
val AuroraPink = Color(0xFFFF5C9D)
val AuroraGold = Color(0xFFFFD166)

private val Night = Color(0xFF08080D)
private val NightSurface = Color(0xFF101017)
private val NightSurfaceHigh = Color(0xFF17171F)

private val DarkScheme = darkColorScheme(
    primary = AuroraViolet,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF2A1F66),
    onPrimaryContainer = Color(0xFFE6DEFF),
    secondary = AuroraTeal,
    onSecondary = Color(0xFF00201C),
    secondaryContainer = Color(0xFF00383A),
    onSecondaryContainer = Color(0xFFB8FFF6),
    tertiary = AuroraPink,
    onTertiary = Color(0xFF3A0018),
    background = Color(0xFF0B0B10),
    onBackground = Color(0xFFE8E6F0),
    surface = NightSurface,
    onSurface = Color(0xFFE8E6F0),
    surfaceVariant = NightSurfaceHigh,
    onSurfaceVariant = Color(0xFFB9B6C8),
    outline = Color(0xFF4A4757),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF3A0000),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF5B3DF5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE6DEFF),
    onPrimaryContainer = Color(0xFF190064),
    secondary = Color(0xFF00796B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB8FFF6),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFFC4005C),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFAF8FF),
    onBackground = Color(0xFF17151F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17151F),
    surfaceVariant = Color(0xFFEDE9F7),
    onSurfaceVariant = Color(0xFF48454F),
    outline = Color(0xFF7B7788),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
)

/** AMOLED variant: true black surfaces to save power on OLED panels. */
private val AmoledScheme = DarkScheme.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceVariant = Color(0xFF0C0C11),
    onBackground = Color(0xFFECEBF2),
    onSurface = Color(0xFFECEBF2),
)

val LumoraShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(34.dp),
)

private val LumoraTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.25).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp,
    ),
)

/** Extra brand colors that are not part of the Material color roles. */
@Immutable
data class LumoraAccents(
    val gradient: List<Color> = listOf(AuroraViolet, AuroraTeal),
    val gradientWarm: List<Color> = listOf(AuroraPink, AuroraGold),
    val elevatedSurface: Color = NightSurfaceHigh,
    val isAmoled: Boolean = false,
)

val LocalLumoraAccents = staticCompositionLocalOf { LumoraAccents() }

@Composable
fun LumoraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    amoled: Boolean = false,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        dynamicColor && supportsDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) {
                val dynamic = dynamicDarkColorScheme(context)
                if (amoled) dynamic.copy(background = Color.Black, surface = Color.Black) else dynamic
            } else {
                dynamicLightColorScheme(context)
            }
        }

        darkTheme && amoled -> AmoledScheme
        darkTheme -> DarkScheme
        else -> LightScheme
    }

    val accents = LumoraAccents(
        gradient = if (darkTheme) {
            listOf(AuroraViolet, AuroraTeal)
        } else {
            listOf(Color(0xFF5B3DF5), Color(0xFF12A99B))
        },
        gradientWarm = listOf(AuroraPink, AuroraGold),
        elevatedSurface = if (darkTheme) NightSurfaceHigh else Color(0xFFFFFFFF),
        isAmoled = amoled && darkTheme,
    )

    CompositionLocalProvider(LocalLumoraAccents provides accents) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LumoraTypography,
            shapes = LumoraShapes,
            content = content,
        )
    }
}

/** Convenience accessor used across the UI. */
val MaterialTheme.extendedAccents: LumoraAccents
    @Composable
    get() = LocalLumoraAccents.current
