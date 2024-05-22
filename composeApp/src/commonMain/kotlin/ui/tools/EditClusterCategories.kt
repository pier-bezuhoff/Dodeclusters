package ui.tools

import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.attributes_category_name
import dodeclusters.composeapp.generated.resources.colors_category_name
import dodeclusters.composeapp.generated.resources.create_category_name
import dodeclusters.composeapp.generated.resources.multiselect_category_name
import dodeclusters.composeapp.generated.resources.region_category_name
import dodeclusters.composeapp.generated.resources.visibility_category_name
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

sealed class EditClusterCategory(
    override val name: StringResource,
    override val tools: List<EditClusterTool>,
    final override var default: EditClusterTool = tools.first()
) : Category {
    override val icon: DrawableResource = default.icon
    // mode ~ toggle as their both their states are determined by a predicate & action is separated
    data object Multiselect : EditClusterCategory(
        Res.string.multiselect_category_name,
        listOf(
            EditClusterTool.Multiselect,
        )
    ) { // ~mode-like
        // toggle: select all/unselect all (active when everything is selected)

        // mode: select-by-click
        // mode: flow-select

        // mode2: xor selection logic
        // mode2: add selection logic
        // mode2: subtract selection logic
    }
    data object Region : EditClusterCategory(
        Res.string.region_category_name,
        listOf(
            EditClusterTool.Region,
            EditClusterTool.RestrictRegionToSelection,
        )
    ) { // ~mode-like
        // toggle: restrict regions to current selection toggle
        // button: chessboard pattern
        // button: erase all parts
        // buttons: [most used colors as a list (sorted by frequency & recency)]
    }
    data object Visibility : EditClusterCategory(
        Res.string.visibility_category_name,
        listOf(
            EditClusterTool.ShowCircles
        )
    ) { // ~button-like
        // toggle: show/hide circle + mb only select ones
        // toggle: fill/unfill the cluster
        // toggle: show points (potentially)
    }
    data object Colors : EditClusterCategory(
        Res.string.colors_category_name,
        listOf(
            EditClusterTool.Palette
        )
    ) { // ~button-like
        // buttons: [most used colors]
    }
    data object Attributes : EditClusterCategory(
        Res.string.attributes_category_name,
        listOf(
            EditClusterTool.Delete,
            EditClusterTool.Duplicate,
        ),
    ) // ~button-like
    data object Create : EditClusterCategory(
        Res.string.create_category_name,
        listOf(
            EditClusterTool.ConstructCircleByCenterAndRadius,
            EditClusterTool.ConstructCircleBy3Points
        ),
    ) { // ~mode-like
        // mode: circle by center&radius
        // mode: circle by 3 points
        // mode: line by 2 points
        // mode: rectangle by top-left & bottom-right
        // mode: polygon
        // button: insert centered cross
        // toggle: enable point-to-circle snapping
    }
}