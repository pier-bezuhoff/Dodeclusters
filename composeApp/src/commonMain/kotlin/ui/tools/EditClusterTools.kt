package ui.tools

import androidx.compose.runtime.Immutable
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.center
import dodeclusters.composeapp.generated.resources.circle_3_points
import dodeclusters.composeapp.generated.resources.circle_by_3_points_description
import dodeclusters.composeapp.generated.resources.circle_by_3_points_name
import dodeclusters.composeapp.generated.resources.circle_by_center_and_radius_description
import dodeclusters.composeapp.generated.resources.circle_by_center_and_radius_name
import dodeclusters.composeapp.generated.resources.circled_region
import dodeclusters.composeapp.generated.resources.copy
import dodeclusters.composeapp.generated.resources.delete_description
import dodeclusters.composeapp.generated.resources.delete_forever
import dodeclusters.composeapp.generated.resources.delete_name
import dodeclusters.composeapp.generated.resources.drag_description
import dodeclusters.composeapp.generated.resources.drag_mode_1_circle
import dodeclusters.composeapp.generated.resources.drag_name
import dodeclusters.composeapp.generated.resources.duplicate_description
import dodeclusters.composeapp.generated.resources.duplicate_name
import dodeclusters.composeapp.generated.resources.invisible
import dodeclusters.composeapp.generated.resources.multiselect_description
import dodeclusters.composeapp.generated.resources.multiselect_mode_3_scattered_circles
import dodeclusters.composeapp.generated.resources.multiselect_name
import dodeclusters.composeapp.generated.resources.open_region
import dodeclusters.composeapp.generated.resources.palette
import dodeclusters.composeapp.generated.resources.palette_description
import dodeclusters.composeapp.generated.resources.palette_name
import dodeclusters.composeapp.generated.resources.region_description
import dodeclusters.composeapp.generated.resources.region_name
import dodeclusters.composeapp.generated.resources.restrict_region_to_selection_description
import dodeclusters.composeapp.generated.resources.restrict_region_to_selection_name
import dodeclusters.composeapp.generated.resources.rounded_square
import dodeclusters.composeapp.generated.resources.select_region_mode_intersection
import dodeclusters.composeapp.generated.resources.show_circles_description
import dodeclusters.composeapp.generated.resources.show_circles_name
import dodeclusters.composeapp.generated.resources.visible
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

@Immutable
sealed class EditClusterTool(
    override val name: StringResource,
    override val description: StringResource,
    override val icon: DrawableResource,
) : Tool, EditClusterToolbarItem {
    // mode ~ toggle as their both their states are determined by a predicate & action is separated
    sealed class Switch(
        name: StringResource,
        description: StringResource,
        icon: DrawableResource,
        override val disabledIcon: DrawableResource? = null
    ) : EditClusterTool(name, description, icon), Tool.BinaryToggle


    data object Drag: Switch(
        Res.string.drag_name,
        Res.string.drag_description,
        Res.drawable.drag_mode_1_circle
    )
    data object Multiselect: Switch(
        Res.string.multiselect_name,
        Res.string.multiselect_description,
        Res.drawable.multiselect_mode_3_scattered_circles
    )

    data object Region: Switch(
        Res.string.region_name,
        Res.string.region_description,
        Res.drawable.select_region_mode_intersection
    )
    data object RestrictRegionToSelection: Switch(
        Res.string.restrict_region_to_selection_name,
        Res.string.restrict_region_to_selection_description,
        Res.drawable.circled_region,
        Res.drawable.open_region
    )

    data object ShowCircles: Switch(
        Res.string.show_circles_name,
        Res.string.show_circles_description,
        Res.drawable.visible,
        Res.drawable.invisible
    ), Tool.BinaryToggle
    data object Palette: EditClusterTool(
        Res.string.palette_name,
        Res.string.palette_description,
        Res.drawable.palette
    ), Tool.InstantAction {
        val colorOutlineIcon = Res.drawable.rounded_square
    }

    data object Duplicate: EditClusterTool(
        Res.string.duplicate_name,
        Res.string.duplicate_description,
        Res.drawable.copy
    ), Tool.ActionOnSelection
    data object Delete: EditClusterTool(
        Res.string.delete_name,
        Res.string.delete_description,
        Res.drawable.delete_forever
    ), Tool.ActionOnSelection

    // MAYBE: add partial argument icon(s)
    data object ConstructCircleByCenterAndRadius: Switch(
        Res.string.circle_by_center_and_radius_name,
        Res.string.circle_by_center_and_radius_description,
        Res.drawable.center
    ), Tool.MultiArg2<InputType.AnyPoint, InputType.AnyPoint> {
        override val argDescriptions = listOf("Circle's center", "Any point on the circle")
    }
    data object ConstructCircleBy3Points: Switch(
        Res.string.circle_by_3_points_name,
        Res.string.circle_by_3_points_description,
        Res.drawable.circle_3_points
    ), Tool.MultiArg3<InputType.AnyPoint, InputType.AnyPoint, InputType.AnyPoint> {
        override val argDescriptions = listOf("1st point on the circle", "2nd point on the circle", "3rd point on the circle")
    }
    // line by 2 pts
    // insert cross/rect/square
}