package domain.settings

import androidx.compose.runtime.Immutable
import domain.ColorAsCss
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ui.editor.ToolbarState
import ui.editor.dialogs.DefaultBiInversionParameters
import ui.editor.dialogs.DefaultInterpolationParameters
import ui.editor.dialogs.DefaultLoxodromicMotionParameters
import ui.editor.dialogs.DefaultRotationParameters
import ui.theme.ColorTheme
import ui.theme.DEFAULT_COLOR_THEME

/**
 * @param[savedColors] user-defined & saved in the color picker as part of [ColorPickerParameters]
 * @param[inversionOfControl] Allow moving non-free object IF all of it's lvl 1 parents=dependencies are free by
 * moving all of its parent with it // ggbra-like
 */
@Immutable
@Serializable
data class Settings(
    val showDirectionArrows: Boolean = false,
    // ?upscaling factor
    val regionsOpacity: Float = 1.0f,
    val regionsBlendModeType: BlendModeType = BlendModeType.SRC_OVER,
    // default tools for categories
    val savedColors: List<ColorAsCss> = emptyList(),
    val defaultInterpolationParameters: DefaultInterpolationParameters = DefaultInterpolationParameters(),
    val defaultRotationParameters: DefaultRotationParameters = DefaultRotationParameters(),
    val defaultBiInversionParameters: DefaultBiInversionParameters = DefaultBiInversionParameters(),
    val defaultLoxodromicMotionParameters: DefaultLoxodromicMotionParameters = DefaultLoxodromicMotionParameters(),
    val categoryDefaultIndices: List<Int?> = ToolbarState().categoryDefaultIndices, // NG dep
    val saveDirectory: String? = null,
    // adjustable in settings screen
    val colorTheme: ColorTheme = DEFAULT_COLOR_THEME,
    val inversionOfControl: InversionOfControl = InversionOfControl.LEVEL_1,
    val enableTangentSnapping: Boolean = true,
    val enableAngleSnapping: Boolean = false,
    val enableCreatingAdditionPointsForCircleByCenterAndRadius: Boolean = false,
    val enableCreatingAdditionPointsForCircleBy3Points: Boolean = true,
    val enableCreatingAdditionPointsForLineBy2Points: Boolean = true,
) {
    companion object {
        val JSON_FORMAT = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /** When constructing an object depending on not-yet-existing points,
         * always create them. In contrast to replacing its expression with a static, free circle.
         * (Should have more choices to make it a settings)
         */
        const val FAST_CENTERED_CIRCLE = true
        const val ENABLE_POINT_TO_POINT_SNAPPING = false
        const val RESTORE_LAST_STATE_ON_LOAD = true
        const val SHOW_IMAGINARY_CIRCLES = true
        /** When several objects are close enough to the tap position,
         * show the list of them to choose from */
        const val SHOW_SELECTION_CHOICES = true
        /** try aligning PartialArcPath vertices horizontally or
         * vertically to each other */
        const val ENABLE_ARCPATH_VERTEX_ALIGNMENT_SNAPPING = true
        const val ENABLE_ARCPATH_VERTEX_TO_VERTEX_SNAPPING = true
    }
}