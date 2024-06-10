package ui.theme

import androidx.compose.material.Colors
import androidx.compose.ui.graphics.Color

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
    val veryDarkGray = Color(0xff_212121)
    val darkGray = Color(0xff_2c2c2c)
    val red = Color.Red
    val pinkishRed = Color(0xff_cf6679)
    val green = Color(0f, 1f, 0f)
    val darkGreen = Color(0f, 0.7f, 0f)
}

val dodeclustersDarkColorPalette = with (DodeclustersColors) {
    Colors(
        primary = turquoise,
        primaryVariant = darkTurquoise,
        secondary = purple,
        secondaryVariant = darkPurple,
        background = veryDarkGray,
        surface = darkGray,
        error = pinkishRed,
        onPrimary = white,
        onSecondary = white,
        onBackground = white,
        onSurface = golden,
        onError = black,
        isLight = false
    )
}

// TODO: test
val dodeclustersLightColorPalette = with (DodeclustersColors) {
    Colors(
        primary = lightTurquoise,
        primaryVariant = turquoise,
        secondary = lightPurple,
        secondaryVariant = purple,
        background = veryLightGray,
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
