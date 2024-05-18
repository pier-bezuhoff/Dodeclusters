package ui.theme

import androidx.compose.material.Colors
import androidx.compose.ui.graphics.Color

object DodeclustersColors {
    val white = Color(0xffffffff)
    val black = Color(0xff000000)
    val turquoise = Color(0xff346f68)
    val darkTurquoise = Color(0xff2c524c)
    val purple = Color(0xff68346f) // complementary to turquoise
    val lightPurple = Color(0xff_9141BD) // to contrast with golden accent
    val darkPurple = Color(0xff4e2b5b)
    val golden = Color(0xffffaa00) // accent
    val gray = Color.Gray
    val veryDarkGray = Color(0xff212121)
    val darkGray = Color(0xff2c2c2c)
    val red = Color.Red
    val pinkishRed = Color(0xffcf6679)
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