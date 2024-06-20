package ui.theme

import androidx.compose.material.Colors
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.ui.graphics.Color

val primaryLight = Color(0xFF7B4E7F)
val onPrimaryLight = Color(0xFFFFFFFF)
val primaryContainerLight = Color(0xFFFFD6FE)
val onPrimaryContainerLight = Color(0xFF310938)
val secondaryLight = Color(0xFF006A62)
val onSecondaryLight = Color(0xFFFFFFFF)
val secondaryContainerLight = Color(0xFF9DF2E6)
val onSecondaryContainerLight = Color(0xFF00201D)
val tertiaryLight = Color(0xFF805610)
val onTertiaryLight = Color(0xFFFFFFFF)
val tertiaryContainerLight = Color(0xFFFFDDB4)
val onTertiaryContainerLight = Color(0xFF291800)
val errorLight = Color(0xFFBA1A1A)
val onErrorLight = Color(0xFFFFFFFF)
val errorContainerLight = Color(0xFFFFDAD6)
val onErrorContainerLight = Color(0xFF410002)
val backgroundLight = Color(0xFFFFF7FA)
val onBackgroundLight = Color(0xFF1F1A1F)
val surfaceLight = Color(0xFFFFF7FA)
val onSurfaceLight = Color(0xFF1F1A1F)
val surfaceVariantLight = Color(0xFFECDFE8)
val onSurfaceVariantLight = Color(0xFF4D444C)
val outlineLight = Color(0xFF7E747D)
val outlineVariantLight = Color(0xFFD0C3CC)
val scrimLight = Color(0xFF000000)
val inverseSurfaceLight = Color(0xFF352F34)
val inverseOnSurfaceLight = Color(0xFFF9EEF5)
val inversePrimaryLight = Color(0xFFEBB5ED)
val surfaceDimLight = Color(0xFFE2D7DE)
val surfaceBrightLight = Color(0xFFFFF7FA)
val surfaceContainerLowestLight = Color(0xFFFFFFFF)
val surfaceContainerLowLight = Color(0xFFFCF0F7)
val surfaceContainerLight = Color(0xFFF6EBF2)
val surfaceContainerHighLight = Color(0xFFF0E5EC)
val surfaceContainerHighestLight = Color(0xFFEADFE6)

val primaryDark = Color(0xFFEBB5ED)
val onPrimaryDark = Color(0xFF48204E)
val primaryContainerDark = Color(0xFF613766)
val onPrimaryContainerDark = Color(0xFFFFD6FE)
val secondaryDark = Color(0xFF81D5CA)
val onSecondaryDark = Color(0xFF003732)
val secondaryContainerDark = Color(0xFF005049)
val onSecondaryContainerDark = Color(0xFF9DF2E6)
val tertiaryDark = Color(0xFFF5BD6F)
val onTertiaryDark = Color(0xFF452B00)
val tertiaryContainerDark = Color(0xFF633F00)
val onTertiaryContainerDark = Color(0xFFFFDDB4)
val errorDark = Color(0xFFFFB4AB)
val onErrorDark = Color(0xFF690005)
val errorContainerDark = Color(0xFF93000A)
val onErrorContainerDark = Color(0xFFFFDAD6)
val backgroundDark = Color(0xFF121212)
val onBackgroundDark = Color(0xFFEADFE6)
val surfaceDark = Color(0xFF121212)
val onSurfaceDark = Color(0xFFEADFE6)
val surfaceVariantDark = Color(0xFF4D444C)
val onSurfaceVariantDark = Color(0xFFD0C3CC)
val outlineDark = Color(0xFF998D96)
val outlineVariantDark = Color(0xFF4D444C)
val scrimDark = Color(0xFF000000)
val inverseSurfaceDark = Color(0xFFEADFE6)
val inverseOnSurfaceDark = Color(0xFF352F34)
val inversePrimaryDark = Color(0xFF7B4E7F)
val surfaceDimDark = Color(0xFF121212)
val surfaceBrightDark = Color(0xFF3E373D)
val surfaceContainerLowestDark = Color(0xFF110D11)
val surfaceContainerLowDark = Color(0xFF1F1A1F)
val surfaceContainerDark = Color(0xFF231E23)
val surfaceContainerHighDark = Color(0xFF2E282D)
val surfaceContainerHighestDark = Color(0xFF393338)

val dodeclustersLightScheme = lightColors(
    primary = primaryLight,
    primaryVariant = primaryContainerLight,
    secondary = secondaryLight,
    secondaryVariant = secondaryContainerLight,
    background = backgroundLight,
    surface = surfaceLight,
    error = errorLight,
    onPrimary = onPrimaryLight,
    onSecondary = onSecondaryLight,
    onBackground = onBackgroundLight,
    onSurface = onTertiaryContainerLight, // circle color
    onError = onErrorLight,
)

val dodeclustersDarkScheme = darkColors(
    primary = primaryDark,
    primaryVariant = primaryContainerDark,
    secondary = secondaryDark,
    secondaryVariant = secondaryContainerDark,
    background = backgroundDark,
    surface = surfaceDark,
    error = errorDark,
    onPrimary = onPrimaryDark,
    onSecondary = onSecondaryDark,
    onBackground = onBackgroundDark,
    onSurface = tertiaryDark, // golden circle color
    onError = onErrorDark,
)


// old theme
object DodeclustersColors {
    val white = Color(0xff_ffffff)
    val black = Color(0xff_000000)
    val turquoise = Color(0xff_346f68)
    val lightTurquoise = Color(0xff_90CBC4)
    val darkTurquoise = Color(0xff_2c524c)
    val purple = Color(0xff_68346f) // complementary to turquoise
    val lightPurple = Color(0xff_9141BD) // to contrast with golden accent
    val darkPurple = Color(0xff_4e2b5b)
    val golden = Color(0xff_ffaa00) // accent
    val gray = Color.Gray
    val veryLightGray = Color(0xff_DEDEDE)
    val veryDarkGray = Color(0xff_121212) // <- google rec // Color(0xff_212121)
    val darkGray = Color(0xff_2c2c2c)
    val red = Color.Red
    val pinkishRed = Color(0xff_cf6679)
    val pinkish = Color(1f, 0.5f, 0.5f)
    val green = Color(0f, 1f, 0f)
    val darkGreen = Color(0f, 0.7f, 0f)
}

//val x = darkColors()
val dodeclustersDarkColorPalette = with (DodeclustersColors) {
    Colors(
        primary = turquoise, // 81D5CA
        primaryVariant = darkTurquoise,
        secondary = purple, // EBB5ED
        secondaryVariant = darkPurple,
        background = veryDarkGray,
        surface = darkGray,
        error = pinkishRed,
        onPrimary = white, // 003732
        onSecondary = white, // 48204E
        onBackground = white,
        onSurface = golden,
        onError = black,
        isLight = false
    )
}

val dodeclustersLightColorPalette = with (DodeclustersColors) {
    Colors(
        primary = lightTurquoise,
        primaryVariant = turquoise,
        secondary = lightPurple,
        secondaryVariant = purple,
        background = white,//veryLightGray,
        surface = white,
        error = pinkishRed,
        onPrimary = black,
        onSecondary = white,
        onBackground = black,
        onSurface = black,
        onError = black,
        isLight = true
    )
}
