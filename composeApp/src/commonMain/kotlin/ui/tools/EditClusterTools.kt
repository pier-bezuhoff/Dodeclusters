package ui.tools

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import data.PartialArgList
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.applied_color_description
import dodeclusters.composeapp.generated.resources.applied_color_name
import dodeclusters.composeapp.generated.resources.circle
import dodeclusters.composeapp.generated.resources.circle_3_points
import dodeclusters.composeapp.generated.resources.circle_by_3_points_arg_descriptions
import dodeclusters.composeapp.generated.resources.circle_by_3_points_description
import dodeclusters.composeapp.generated.resources.circle_by_3_points_name
import dodeclusters.composeapp.generated.resources.circle_by_center_and_radius_arg_descriptions
import dodeclusters.composeapp.generated.resources.circle_by_center_and_radius_description
import dodeclusters.composeapp.generated.resources.circle_by_center_and_radius_name
import dodeclusters.composeapp.generated.resources.circle_center_and_radius_point
import dodeclusters.composeapp.generated.resources.circle_inversion_arg_descriptions
import dodeclusters.composeapp.generated.resources.circle_inversion_description
import dodeclusters.composeapp.generated.resources.circle_inversion_name
import dodeclusters.composeapp.generated.resources.circle_inversion_v2
import dodeclusters.composeapp.generated.resources.circled_region
import dodeclusters.composeapp.generated.resources.copy
import dodeclusters.composeapp.generated.resources.delete_all_parts_description
import dodeclusters.composeapp.generated.resources.delete_all_parts_name
import dodeclusters.composeapp.generated.resources.delete_description
import dodeclusters.composeapp.generated.resources.delete_forever
import dodeclusters.composeapp.generated.resources.delete_name
import dodeclusters.composeapp.generated.resources.deselect
import dodeclusters.composeapp.generated.resources.drag_description
import dodeclusters.composeapp.generated.resources.drag_mode_1_circle
import dodeclusters.composeapp.generated.resources.drag_name
import dodeclusters.composeapp.generated.resources.duplicate_description
import dodeclusters.composeapp.generated.resources.duplicate_name
import dodeclusters.composeapp.generated.resources.filled_circle
import dodeclusters.composeapp.generated.resources.flow_multiselect_description
import dodeclusters.composeapp.generated.resources.flow_multiselect_name
import dodeclusters.composeapp.generated.resources.full_screen_cross
import dodeclusters.composeapp.generated.resources.hide_layers
import dodeclusters.composeapp.generated.resources.insert_centered_cross_description
import dodeclusters.composeapp.generated.resources.insert_centered_cross_name
import dodeclusters.composeapp.generated.resources.invisible
import dodeclusters.composeapp.generated.resources.line_2_points
import dodeclusters.composeapp.generated.resources.line_by_2_points_arg_descriptions
import dodeclusters.composeapp.generated.resources.line_by_2_points_description
import dodeclusters.composeapp.generated.resources.line_by_2_points_name
import dodeclusters.composeapp.generated.resources.multiselect_description
import dodeclusters.composeapp.generated.resources.multiselect_mode_3_scattered_circles
import dodeclusters.composeapp.generated.resources.multiselect_name
import dodeclusters.composeapp.generated.resources.open_region
import dodeclusters.composeapp.generated.resources.paint_splash
import dodeclusters.composeapp.generated.resources.palette
import dodeclusters.composeapp.generated.resources.palette_description
import dodeclusters.composeapp.generated.resources.palette_name
import dodeclusters.composeapp.generated.resources.region_description
import dodeclusters.composeapp.generated.resources.region_name
import dodeclusters.composeapp.generated.resources.restrict_region_to_selection_description
import dodeclusters.composeapp.generated.resources.restrict_region_to_selection_name
import dodeclusters.composeapp.generated.resources.select_all
import dodeclusters.composeapp.generated.resources.select_region_mode_intersection
import dodeclusters.composeapp.generated.resources.show_circles_description
import dodeclusters.composeapp.generated.resources.show_circles_name
import dodeclusters.composeapp.generated.resources.toggle_filled_or_outline_description
import dodeclusters.composeapp.generated.resources.toggle_filled_or_outline_name
import dodeclusters.composeapp.generated.resources.toggle_select_all_description
import dodeclusters.composeapp.generated.resources.toggle_select_all_name
import dodeclusters.composeapp.generated.resources.two_of_three_circles_connected
import dodeclusters.composeapp.generated.resources.visible
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import ui.theme.DodeclustersColors

@Immutable
sealed class EditClusterTool(
    override val name: StringResource,
    override val description: StringResource,
    override val icon: DrawableResource,
) : Tool {
    sealed class Switch(
        name: StringResource,
        description: StringResource,
        icon: DrawableResource,
        final override val disabledIcon: DrawableResource? = null
    ) : EditClusterTool(name, description, icon), Tool.BinaryToggle

    sealed class MultiArg(
        final override val signature: PartialArgList.Signature,
        name: StringResource,
        description: StringResource,
        final override val argDescriptions: StringResource,
        icon: DrawableResource,
        final override val disabledIcon: DrawableResource? = null,
    ) : EditClusterTool(name, description, icon), Tool.BinaryToggle, Tool.MultiArg

    sealed class Action(
        name: StringResource,
        description: StringResource,
        icon: DrawableResource,
    ) : EditClusterTool(name, description, icon), Tool.InstantAction


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
    data object FlowSelect: Switch(
        Res.string.flow_multiselect_name,
        Res.string.flow_multiselect_description,
        Res.drawable.two_of_three_circles_connected
    )
    data object ToggleSelectAll: Switch(
        Res.string.toggle_select_all_name,
        Res.string.toggle_select_all_description,
        Res.drawable.select_all,
        Res.drawable.deselect
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
    data object DeleteAllParts: Action(
        Res.string.delete_all_parts_name,
        Res.string.delete_all_parts_description,
        Res.drawable.hide_layers
    )

    data object ShowCircles: Switch(
        Res.string.show_circles_name,
        Res.string.show_circles_description,
        Res.drawable.visible,
        Res.drawable.invisible
    )
    data object ToggleFilledOrOutline: Switch(
        Res.string.toggle_filled_or_outline_name,
        Res.string.toggle_filled_or_outline_description,
        Res.drawable.filled_circle,
        Res.drawable.circle
    )

    data object Palette: Action(
        Res.string.palette_name,
        Res.string.palette_description,
        Res.drawable.palette
    )
    data class AppliedColor(val color: Color) : Action(
        Res.string.applied_color_name,
        Res.string.applied_color_description,
        Res.drawable.paint_splash, // tint=color should be applied
    )

    data object Duplicate: Action(
        Res.string.duplicate_name,
        Res.string.duplicate_description,
        Res.drawable.copy
    ), Tool.ActionOnSelection
    // MAYBE: eraser-like mode
    data object Delete: Action(
        Res.string.delete_name,
        Res.string.delete_description,
        Res.drawable.delete_forever
    ), Tool.ActionOnSelection {
        val tint = DodeclustersColors.pinkish
    }

    data object CircleInversion: MultiArg(
        PartialArgList.SIGNATURE_2_CIRCLES,
        Res.string.circle_inversion_name,
        Res.string.circle_inversion_description,
        Res.string.circle_inversion_arg_descriptions,
        Res.drawable.circle_inversion_v2
    )

    // MAYBE: add partial argument icon(s)
    data object ConstructCircleByCenterAndRadius: MultiArg(
        PartialArgList.SIGNATURE_2_POINTS,
        Res.string.circle_by_center_and_radius_name,
        Res.string.circle_by_center_and_radius_description,
        Res.string.circle_by_center_and_radius_arg_descriptions,
        Res.drawable.circle_center_and_radius_point,
    )
    data object ConstructCircleBy3Points: MultiArg(
        PartialArgList.SIGNATURE_3_POINTS,
        Res.string.circle_by_3_points_name,
        Res.string.circle_by_3_points_description,
        Res.string.circle_by_3_points_arg_descriptions,
        Res.drawable.circle_3_points
    )
    data object ConstructLineBy2Points: MultiArg(
        PartialArgList.SIGNATURE_2_POINTS,
        Res.string.line_by_2_points_name,
        Res.string.line_by_2_points_description,
        Res.string.line_by_2_points_arg_descriptions,
        Res.drawable.line_2_points
    )
    data object InsertCenteredCross: Action(
        Res.string.insert_centered_cross_name,
        Res.string.insert_centered_cross_description,
        Res.drawable.full_screen_cross
    )
    // insert rect/square
}