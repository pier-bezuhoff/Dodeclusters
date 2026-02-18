package ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ui.isCompact

@Immutable
enum class ColorTheme {
    LIGHT, DARK,
    /** Black on white, intended for presentations */
    HIGH_CONTRAST,
    /** Automatically detect current system color theme */
    AUTO
    ;

    @Composable
    fun isLight(): Boolean =
        when (this) {
            ColorTheme.LIGHT -> true
            ColorTheme.HIGH_CONTRAST -> true
            ColorTheme.DARK -> false
            ColorTheme.AUTO -> !isSystemInDarkTheme()
        }

    @Composable
    fun isDark(): Boolean =
        when (this) {
            ColorTheme.LIGHT -> false
            ColorTheme.HIGH_CONTRAST -> false
            ColorTheme.DARK -> true
            ColorTheme.AUTO -> isSystemInDarkTheme()
        }

    @Composable
    fun toColorScheme(): ColorScheme {
        val isLight = when (this) {
            ColorTheme.LIGHT -> true
            ColorTheme.HIGH_CONTRAST -> true // TODO: custom colors
            ColorTheme.DARK -> false
            ColorTheme.AUTO -> !isSystemInDarkTheme()
        }
        val scheme =
            if (isLight) DodeclustersColors.lightScheme
            else DodeclustersColors.darkScheme
        return scheme
    }
}

val DEFAULT_COLOR_THEME = ColorTheme.DARK

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun DodeclustersTheme(
    colorTheme: ColorTheme = DEFAULT_COLOR_THEME,
    content: @Composable () -> Unit
) {
    val isLight = when (colorTheme) {
        ColorTheme.LIGHT -> true
        ColorTheme.HIGH_CONTRAST -> true // TODO: custom colors
        ColorTheme.DARK -> false
        ColorTheme.AUTO -> !isSystemInDarkTheme()
    }
    val scheme =
        if (isLight) DodeclustersColors.lightScheme
        else DodeclustersColors.darkScheme
    val extendedScheme =
        if (isLight) DodeclustersColors.extendedLightScheme
        else DodeclustersColors.extendedDarkScheme
    val isCompact = calculateWindowSizeClass().isCompact
    val adaptiveTypography = AdaptiveTypography(
        actionButtonFontSize =
            if (isCompact)
                18.sp
            else
                24.sp
        ,
        title =
            if (isCompact)
                MaterialTheme.typography.titleMedium
            else
                MaterialTheme.typography.titleLarge
        ,
        body =
            if (isCompact)
                MaterialTheme.typography.bodyMedium
            else
                MaterialTheme.typography.bodyLarge
        ,
        label =
            if (isCompact)
                MaterialTheme.typography.labelMedium
            else
                MaterialTheme.typography.labelLarge
        ,
    )
    CompositionLocalProvider(
        LocalExtendedColors provides extendedScheme,
        LocalAdaptiveTypography provides adaptiveTypography,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = MaterialTheme.shapes.copy(
                large = RoundedCornerShape(16.dp),
                extraLarge = RoundedCornerShape(24.dp), // default is 28dp it appears
            ),
            content = content
        )
    }
}


val LocalIsDarkTheme = staticCompositionLocalOf {
    true
}

val MaterialTheme.isDarkTheme: Boolean
    @Composable
    @ReadOnlyComposable
    get() =
        LocalIsDarkTheme.current
