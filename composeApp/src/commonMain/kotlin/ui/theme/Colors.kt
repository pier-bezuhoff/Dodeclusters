package ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

object DodeclustersColors {
    // use figma plugin: material theme builder to generate
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
    val outlineVariantLight = Color(0xFF7E747D)
//    val outlineVariantLight = Color(0xFFD0C3CC) // too low contrast for dividers on white/light-gray
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
    val surfaceContainerHighestLight = // Color(0xFFEADFE6)
        Color(0xFFCCCCCC) // haxz
    val accentLight = Color(0xFF_d09a3f)
    val highAccentLight = Color(0xFF805610)

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
    val accentDark = Color(0xFF_D4BE51)
    val highAccentDark = Color(0xFF_F5BD6F)

    val pureSecondary = Color(0xFF_05E0CF)

    // M3
    val lightScheme = lightColorScheme(
        primary = primaryLight,
        onPrimary = onPrimaryLight,
        primaryContainer = primaryContainerLight,
        onPrimaryContainer = onPrimaryContainerLight,
        secondary = secondaryLight,
        onSecondary = onSecondaryLight,
        secondaryContainer = secondaryContainerLight,
        onSecondaryContainer = onSecondaryContainerLight,
        tertiary = tertiaryLight,
        onTertiary = onTertiaryLight,
        tertiaryContainer = tertiaryContainerLight,
        onTertiaryContainer = onTertiaryContainerLight,
        error = errorLight,
        onError = onErrorLight,
        errorContainer = errorContainerLight,
        onErrorContainer = onErrorContainerLight,
        background = backgroundLight,
        onBackground = onBackgroundLight,
        surface = surfaceLight,
        onSurface = onSurfaceLight,
        surfaceVariant = surfaceVariantLight,
        onSurfaceVariant = onSurfaceVariantLight,
        outline = outlineLight,
        outlineVariant = outlineVariantLight,
        scrim = scrimLight,
        inverseSurface = inverseSurfaceLight,
        inverseOnSurface = inverseOnSurfaceLight,
        inversePrimary = inversePrimaryLight,
        surfaceDim = surfaceDimLight,
        surfaceBright = surfaceBrightLight,
        surfaceContainerLowest = surfaceContainerLowestLight,
        surfaceContainerLow = surfaceContainerLowLight,
        surfaceContainer = surfaceContainerLight,
        surfaceContainerHigh = surfaceContainerHighLight,
        surfaceContainerHighest = surfaceContainerHighestLight,
    )

    val extendedLightScheme = ExtendedColorScheme(
        accentColor = accentLight,
        highAccentColor = highAccentLight,
    )

    val darkScheme = darkColorScheme(
        primary = primaryDark,
        onPrimary = onPrimaryDark,
        primaryContainer = primaryContainerDark,
        onPrimaryContainer = onPrimaryContainerDark,
        secondary = secondaryDark,
        onSecondary = onSecondaryDark,
        secondaryContainer = secondaryContainerDark,
        onSecondaryContainer = onSecondaryContainerDark,
        tertiary = tertiaryDark,
        onTertiary = onTertiaryDark,
        tertiaryContainer = tertiaryContainerDark,
        onTertiaryContainer = onTertiaryContainerDark,
        error = errorDark,
        onError = onErrorDark,
        errorContainer = errorContainerDark,
        onErrorContainer = onErrorContainerDark,
        background = backgroundDark,
        onBackground = onBackgroundDark,
        surface = surfaceDark,
        onSurface = onSurfaceDark,
        surfaceVariant = surfaceVariantDark,
        onSurfaceVariant = onSurfaceVariantDark,
        outline = outlineDark,
        outlineVariant = outlineVariantDark,
        scrim = scrimDark,
        inverseSurface = inverseSurfaceDark,
        inverseOnSurface = inverseOnSurfaceDark,
        inversePrimary = inversePrimaryDark,
        surfaceDim = surfaceDimDark,
        surfaceBright = surfaceBrightDark,
        surfaceContainerLowest = surfaceContainerLowestDark,
        surfaceContainerLow = surfaceContainerLowDark,
        surfaceContainer = surfaceContainerDark,
        surfaceContainerHigh = surfaceContainerHighDark,
        surfaceContainerHighest = surfaceContainerHighestDark,
    )

    val extendedDarkScheme = ExtendedColorScheme(
        accentColor = accentDark,
        highAccentColor = highAccentDark,
    )

    // custom colors
    val purple = Color(0xff_A020F0) // used in the default cluster
    val dimPurple = Color(0xff_9141BD) // similar to purple but slightly less intensive
    val deepAmethyst = Color(0xff_6e3083) // strong & dark pinkish violet
    val puce = Color(0xff_cc8899) // cute dark pink
    val golden = tertiaryDark
    val veryStrongSalad = Color(0xff_8aff00)
    val strongSalad = Color(0xff_a0db5a)
    val darkestGray = Color(0xff_121212)
    val lightestWhite = Color(0xff_FAF8FF)

    val gray = Color.Gray
    val red = Color.Red
    val lightRed = Color(0xff_ff7777)
    val pinkish = Color(1f, 0.5f, 0.5f)
    val green = Color(0f, 1f, 0f)
    val darkGreen = Color(0f, 0.7f, 0f)
    val skyBlue = Color(0xff_2ca3ff)
}
