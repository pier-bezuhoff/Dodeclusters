package ui.theme

import androidx.compose.material.Colors
import androidx.compose.ui.graphics.Color

object DodeclustersColors {
    // dark theme
//    val background = Color(0x202020)
//    val textColor = Color(0xFFFFFF)
//    val primary = Color(0x4FD2C1)
//    val secondary = Color(0x5E4A95)
//    val accent = Color(0xFFAA00)

    // version 2
//    val primaryMain = Color(0x51D2C1)
//    val primaryContrastText = Color(0x212121)
//    val secondaryMain = Color(0x5E4A96)
//    val secondaryContrastText = Color(0xFFFFFF)
//    val divider = Color(0xFFAA00)
//    val textPrimary = Color(0xFFFFFF)
//    val textSecondary_ = Color(0x999999) // tp + 60%
//    val textSecondary = Color(0x99FFFFFF) // tp + 60%
//    val textDisabled_ = Color(0x616161) // tp + 38%
//    val textDisabled = Color(0x61FFFFFF) // tp + 38%
//    val textHint = Color(0xFFAA00)
//    val backgroundDefault = Color(0x212121)

    val white = Color(0xffffffff)
    val black = Color(0xff000000)
//    val turquoise = Color(0xff51d2c1)
    val turquoise = Color(0xff346f68)
    val darkTurquoise = Color(0xff2c524c)
    val purple = Color(0xff68346f) // complementary to turquoise
    val lightPurple = Color(0xff_9141BD)
    val darkPurple = Color(0xff4e2b5b)
    val golden = Color(0xffffaa00) // accent
    val gray = Color.Gray
    val veryDarkGray = Color(0xff212121)
    val darkGray = Color(0xff2c2c2c)
    val red = Color.Red
    val pinkishRed = Color(0xffcf6679)
    val green = Color(0f, 1f, 0f)
    val darkGreen = Color(0f, 0.5f, 0f)
    val grassGreen = Color(0xff018d4a)
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