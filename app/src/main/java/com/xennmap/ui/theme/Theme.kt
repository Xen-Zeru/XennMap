package com.xennmap.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colors that live outside the Material scheme but are used across
 * the app: safety states and map-specific surfaces.
 * Safety states are never communicated by color alone (labels accompany them).
 */
@Immutable
data class ExtendedColors(
    val safe: Color,
    val warning: Color,
    val danger: Color,
    val textSecondary: Color,
    val glassSurface: Color,
    val glassBorder: Color,
    val water: Color,
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        safe = Safe,
        warning = Warning,
        danger = Danger,
        textSecondary = TextSecondary,
        glassSurface = OceanSurfaceGlass,
        glassBorder = OutlineDark,
        water = MapWaterDark,
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = Color(0xFF04121F),
    primaryContainer = Color(0xFF14486F),
    onPrimaryContainer = Color(0xFFCFE9FF),
    secondary = Secondary,
    onSecondary = Color(0xFF04211E),
    secondaryContainer = Color(0xFF0E4A44),
    onSecondaryContainer = Color(0xFFC8F3EC),
    tertiary = Safe,
    background = OceanBackground,
    onBackground = TextPrimary,
    surface = OceanSurface,
    onSurface = TextPrimary,
    surfaceVariant = OceanSurfaceHigh,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = OceanSurfaceHigh,
    surfaceContainerHigh = Color(0xFF14334A),
    surfaceContainerHighest = Color(0xFF183D57),
    error = Danger,
    onError = Color(0xFF2B0505),
    outline = OutlineDark,
    outlineVariant = Color(0xFF14324A),
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCFE9FF),
    onPrimaryContainer = Color(0xFF052B47),
    secondary = LightSecondary,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC8F3EC),
    onSecondaryContainer = Color(0xFF053B36),
    tertiary = Color(0xFF1C7A55),
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceHigh,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainer = LightSurfaceHigh,
    surfaceContainerHigh = Color(0xFFD5E3EF),
    surfaceContainerHighest = Color(0xFFC9DAE9),
    error = Color(0xFFC63B3B),
    onError = Color(0xFFFFFFFF),
    outline = LightOutline,
    outlineVariant = Color(0xFFDCE7F0),
)

@Composable
fun XennTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extended = if (darkTheme) {
        ExtendedColors(
            safe = Safe,
            warning = Warning,
            danger = Danger,
            textSecondary = TextSecondary,
            glassSurface = OceanSurfaceGlass,
            glassBorder = OutlineDark,
            water = MapWaterDark,
        )
    } else {
        ExtendedColors(
            safe = Color(0xFF1C7A55),
            warning = Color(0xFFB97710),
            danger = Color(0xFFC63B3B),
            textSecondary = LightTextSecondary,
            glassSurface = Color(0xF2FFFFFF),
            glassBorder = LightOutline,
            water = MapWaterLight,
        )
    }

    CompositionLocalProvider(LocalExtendedColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = XennTypography,
            shapes = XennShapes,
            content = content,
        )
    }
}

object XennThemeExtended {
    val colors: ExtendedColors
        @Composable get() = LocalExtendedColors.current
}
