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
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
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
            LIGHT -> true
            HIGH_CONTRAST -> true
            DARK -> false
            AUTO -> !isSystemInDarkTheme()
        }

    @Composable
    fun isDark(): Boolean =
        when (this) {
            LIGHT -> false
            HIGH_CONTRAST -> false
            DARK -> true
            AUTO -> isSystemInDarkTheme()
        }

    @Composable
    fun toColorScheme(): ColorScheme {
        val isLight = when (this) {
            LIGHT -> true
            HIGH_CONTRAST -> true // TODO: custom colors
            DARK -> false
            AUTO -> !isSystemInDarkTheme()
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
    val defaultViewConfiguration = LocalViewConfiguration.current
    val viewConfiguration = remember(defaultViewConfiguration) {
        object : ViewConfiguration {
            override val longPressTimeoutMillis: Long = // desktop/web: 500ms, android: 400ms
                defaultViewConfiguration.longPressTimeoutMillis // quite long
            override val doubleTapTimeoutMillis: Long = // 300ms
                defaultViewConfiguration.doubleTapTimeoutMillis
            override val doubleTapMinTimeMillis: Long = // 40ms
                defaultViewConfiguration.doubleTapMinTimeMillis
            override val touchSlop: Float = // min drag length for dragging to register
                // defaults (might depend on screen sizes/densities):
                // 18 on desktop
                // 24.75 on web
                // 16 on my android tablet
                defaultViewConfiguration.touchSlop * 0.5f
        }
    }
    CompositionLocalProvider(
        LocalExtendedColors provides extendedScheme,
        LocalAdaptiveTypography provides adaptiveTypography,
        LocalViewConfiguration provides viewConfiguration,
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
