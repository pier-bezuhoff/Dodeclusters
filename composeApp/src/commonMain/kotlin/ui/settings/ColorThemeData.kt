package ui.settings

import ui.theme.ColorTheme

data class ColorThemeData(
    val enabled: Boolean,
    val description: String,
    val colorTheme: ColorTheme,
)
