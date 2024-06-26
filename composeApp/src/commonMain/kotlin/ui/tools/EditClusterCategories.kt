package ui.tools

import androidx.compose.runtime.Immutable
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.attributes_category_name
import dodeclusters.composeapp.generated.resources.circled_tool
import dodeclusters.composeapp.generated.resources.colors_category_name
import dodeclusters.composeapp.generated.resources.create_category_name
import dodeclusters.composeapp.generated.resources.drag_category_name
import dodeclusters.composeapp.generated.resources.half_diffused_circle
import dodeclusters.composeapp.generated.resources.multiselect_category_name
import dodeclusters.composeapp.generated.resources.region_category_name
import dodeclusters.composeapp.generated.resources.transform_category_name
import dodeclusters.composeapp.generated.resources.visibility_category_name
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

@Immutable
sealed class EditClusterCategory(
    override val name: StringResource,
    override val tools: List<EditClusterTool>,
    final override val defaultables: List<Int> = listOf(0),
    final override val default: EditClusterTool? = defaultables.firstOrNull()?.let { tools[it] },
    final override val icon: DrawableResource? = null
) : Category {

    data object Drag : EditClusterCategory(
        Res.string.drag_category_name,
        listOf(EditClusterTool.Drag)
    )
    data object Multiselect : EditClusterCategory(
        Res.string.multiselect_category_name,
        listOf(
            EditClusterTool.Multiselect,
            EditClusterTool.ToggleSelectAll,
        )
    ) { // ~mode-like

        // submode1: select-by-click
        // submode1: flow-select

        // submode2: xor selection logic
        // submode2: add selection logic
        // submode2: subtract selection logic
    }
    data object Region : EditClusterCategory(
        Res.string.region_category_name,
        listOf(
            EditClusterTool.Region,
            EditClusterTool.RestrictRegionToSelection,
            EditClusterTool.DeleteAllParts,
            // EditClusterTool.AppliedColor are auto-added
        )
    ) { // ~mode-like
        // button/switch: chessboard pattern
    }
    data object Visibility : EditClusterCategory(
        Res.string.visibility_category_name,
        listOf(
            EditClusterTool.ShowCircles,
            EditClusterTool.ToggleFilledOrOutline,
        )
    ) { // ~button/switch-like
    }
    data object Colors : EditClusterCategory(
        Res.string.colors_category_name,
        listOf(
            EditClusterTool.Palette
        )
    ) { // ~button-like
    }
    // MAYBE: this should be a separate context menu, not a general category
    data object Attributes : EditClusterCategory(
        Res.string.attributes_category_name,
        listOf(
            EditClusterTool.Delete,
            EditClusterTool.Duplicate,
        ),
        defaultables = emptyList(),
        icon = Res.drawable.half_diffused_circle
    ) // just open panel, mb diff icon also (like setting wheel)
    data object Transform : EditClusterCategory(
        Res.string.transform_category_name,
        listOf(EditClusterTool.CircleInversion),
        defaultables = listOf(0),
        icon = Res.drawable.circled_tool
    ) { // ~mode-like
        // button: scale -> slider or some other interface
        // button: rotate -> slider, manual angle, etc
        // + invert, kaleidoscopic reflection, etc
    }
    data object Create : EditClusterCategory(
        Res.string.create_category_name,
        listOf(
            EditClusterTool.ConstructCircleByCenterAndRadius,
            EditClusterTool.ConstructCircleBy3Points,
            EditClusterTool.InsertCenteredCross,
        ),
        defaultables = listOf(0, 1)
    ) { // ~mode-like
        // mode: bezier-like arc path
        // mode: line by 2 points
        // mode: rectangle by top-left & bottom-right
        // mode: polygon
        // button: insert centered cross
        // toggle: enable point-to-circle snapping
    }
}
