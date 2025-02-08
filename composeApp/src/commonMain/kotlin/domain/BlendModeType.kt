package domain

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.BlendMode
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.blend_mode_difference
import dodeclusters.composeapp.generated.resources.blend_mode_multiply
import dodeclusters.composeapp.generated.resources.blend_mode_overlay
import dodeclusters.composeapp.generated.resources.blend_mode_plus
import dodeclusters.composeapp.generated.resources.blend_mode_screen
import dodeclusters.composeapp.generated.resources.blend_mode_src_over
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource

// reference: https://developer.android.com/reference/android/graphics/BlendMode
/** Wrapper around [BlendMode]s for easy serialization */
@Immutable
@Serializable
enum class BlendModeType(
    val blendMode: BlendMode,
    val nameStringResource: StringResource,
) {
    /** Default blend mode */
    SRC_OVER(BlendMode.SrcOver, Res.string.blend_mode_src_over),
    MULTIPLY(BlendMode.Multiply, Res.string.blend_mode_multiply),
    SCREEN(BlendMode.Screen, Res.string.blend_mode_screen),
    OVERLAY(BlendMode.Overlay, Res.string.blend_mode_overlay),
    DIFFERENCE(BlendMode.Difference, Res.string.blend_mode_difference),
    PlUS(BlendMode.Plus, Res.string.blend_mode_plus),
}
