package ui.tools

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.add_image
import dodeclusters.composeapp.generated.resources.add_point_arg_descriptions
import dodeclusters.composeapp.generated.resources.add_point_description
import dodeclusters.composeapp.generated.resources.add_point_name
import dodeclusters.composeapp.generated.resources.applied_color_description
import dodeclusters.composeapp.generated.resources.applied_color_name
import dodeclusters.composeapp.generated.resources.arc_path_arg_descriptions
import dodeclusters.composeapp.generated.resources.arc_path_description
import dodeclusters.composeapp.generated.resources.arc_path_name
import dodeclusters.composeapp.generated.resources.change_background_description
import dodeclusters.composeapp.generated.resources.change_background_name
import dodeclusters.composeapp.generated.resources.chessboard
import dodeclusters.composeapp.generated.resources.chessboard_crossed
import dodeclusters.composeapp.generated.resources.chessboard_reflected
import dodeclusters.composeapp.generated.resources.circle
import dodeclusters.composeapp.generated.resources.circle_3_points
import dodeclusters.composeapp.generated.resources.circle_by_3_points_arg_descriptions
import dodeclusters.composeapp.generated.resources.circle_by_3_points_description
import dodeclusters.composeapp.generated.resources.circle_by_3_points_name
import dodeclusters.composeapp.generated.resources.circle_by_center_and_radius_arg_descriptions
import dodeclusters.composeapp.generated.resources.circle_by_center_and_radius_description
import dodeclusters.composeapp.generated.resources.circle_by_center_and_radius_name
import dodeclusters.composeapp.generated.resources.circle_by_pencil_and_point_arg_descriptions
import dodeclusters.composeapp.generated.resources.circle_by_pencil_and_point_description
import dodeclusters.composeapp.generated.resources.circle_by_pencil_and_point_name
import dodeclusters.composeapp.generated.resources.circle_center_and_radius_point
import dodeclusters.composeapp.generated.resources.circle_extrapolation_arg_descriptions
import dodeclusters.composeapp.generated.resources.circle_extrapolation_description
import dodeclusters.composeapp.generated.resources.circle_extrapolation_name
import dodeclusters.composeapp.generated.resources.circle_interpolation_arg_descriptions
import dodeclusters.composeapp.generated.resources.circle_interpolation_description
import dodeclusters.composeapp.generated.resources.circle_interpolation_name
import dodeclusters.composeapp.generated.resources.circle_inversion
import dodeclusters.composeapp.generated.resources.circle_inversion_arg_descriptions
import dodeclusters.composeapp.generated.resources.circle_inversion_description
import dodeclusters.composeapp.generated.resources.circle_inversion_name
import dodeclusters.composeapp.generated.resources.circled_region
import dodeclusters.composeapp.generated.resources.complete_arc_path
import dodeclusters.composeapp.generated.resources.confirm
import dodeclusters.composeapp.generated.resources.copy
import dodeclusters.composeapp.generated.resources.crossed_circle
import dodeclusters.composeapp.generated.resources.delete_all_parts_description
import dodeclusters.composeapp.generated.resources.delete_all_parts_name
import dodeclusters.composeapp.generated.resources.delete_description
import dodeclusters.composeapp.generated.resources.delete_forever
import dodeclusters.composeapp.generated.resources.delete_name
import dodeclusters.composeapp.generated.resources.deselect
import dodeclusters.composeapp.generated.resources.detach_name
import dodeclusters.composeapp.generated.resources.dotted_rectangle
import dodeclusters.composeapp.generated.resources.drag_description
import dodeclusters.composeapp.generated.resources.drag_mode_1_circle
import dodeclusters.composeapp.generated.resources.drag_name
import dodeclusters.composeapp.generated.resources.duplicate_description
import dodeclusters.composeapp.generated.resources.duplicate_name
import dodeclusters.composeapp.generated.resources.expand
import dodeclusters.composeapp.generated.resources.expand_name
import dodeclusters.composeapp.generated.resources.extrapolate_lines
import dodeclusters.composeapp.generated.resources.fill_chessboard_pattern_description
import dodeclusters.composeapp.generated.resources.fill_chessboard_pattern_name
import dodeclusters.composeapp.generated.resources.fill_region
import dodeclusters.composeapp.generated.resources.fill_swiped_circles
import dodeclusters.composeapp.generated.resources.filled_circle
import dodeclusters.composeapp.generated.resources.flagged_point
import dodeclusters.composeapp.generated.resources.flow_fill_description
import dodeclusters.composeapp.generated.resources.flow_fill_name
import dodeclusters.composeapp.generated.resources.flow_multiselect_description
import dodeclusters.composeapp.generated.resources.flow_multiselect_name
import dodeclusters.composeapp.generated.resources.fullscreen
import dodeclusters.composeapp.generated.resources.hide_haired_arrow
import dodeclusters.composeapp.generated.resources.hide_layers
import dodeclusters.composeapp.generated.resources.hide_ui_description
import dodeclusters.composeapp.generated.resources.hide_ui_name
import dodeclusters.composeapp.generated.resources.insert_centered_cross_description
import dodeclusters.composeapp.generated.resources.insert_centered_cross_name
import dodeclusters.composeapp.generated.resources.inserted_cross
import dodeclusters.composeapp.generated.resources.interpolate_lines
import dodeclusters.composeapp.generated.resources.line_2_points
import dodeclusters.composeapp.generated.resources.line_by_2_points_arg_descriptions
import dodeclusters.composeapp.generated.resources.line_by_2_points_description
import dodeclusters.composeapp.generated.resources.line_by_2_points_name
import dodeclusters.composeapp.generated.resources.lock_open
import dodeclusters.composeapp.generated.resources.loxodromic_motion_arg_descriptions
import dodeclusters.composeapp.generated.resources.loxodromic_motion_description
import dodeclusters.composeapp.generated.resources.loxodromic_motion_name
import dodeclusters.composeapp.generated.resources.multiselect
import dodeclusters.composeapp.generated.resources.multiselect_description
import dodeclusters.composeapp.generated.resources.multiselect_name
import dodeclusters.composeapp.generated.resources.open_file
import dodeclusters.composeapp.generated.resources.open_file_name
import dodeclusters.composeapp.generated.resources.open_region
import dodeclusters.composeapp.generated.resources.paint_splash
import dodeclusters.composeapp.generated.resources.palette
import dodeclusters.composeapp.generated.resources.palette_description
import dodeclusters.composeapp.generated.resources.palette_name
import dodeclusters.composeapp.generated.resources.pick_circle_color_description
import dodeclusters.composeapp.generated.resources.pick_circle_color_name
import dodeclusters.composeapp.generated.resources.png_export_name
import dodeclusters.composeapp.generated.resources.propeller
import dodeclusters.composeapp.generated.resources.rectangular_select_description
import dodeclusters.composeapp.generated.resources.rectangular_select_name
import dodeclusters.composeapp.generated.resources.redo
import dodeclusters.composeapp.generated.resources.redo_name
import dodeclusters.composeapp.generated.resources.region_description
import dodeclusters.composeapp.generated.resources.region_name
import dodeclusters.composeapp.generated.resources.restrict_region_to_selection_description
import dodeclusters.composeapp.generated.resources.restrict_region_to_selection_name
import dodeclusters.composeapp.generated.resources.right_left
import dodeclusters.composeapp.generated.resources.road
import dodeclusters.composeapp.generated.resources.save
import dodeclusters.composeapp.generated.resources.save_cluster_name
import dodeclusters.composeapp.generated.resources.select_all
import dodeclusters.composeapp.generated.resources.shark_fin_striped
import dodeclusters.composeapp.generated.resources.show_circles_description
import dodeclusters.composeapp.generated.resources.show_circles_name
import dodeclusters.composeapp.generated.resources.shrink
import dodeclusters.composeapp.generated.resources.shrink_name
import dodeclusters.composeapp.generated.resources.spiral
import dodeclusters.composeapp.generated.resources.stub
import dodeclusters.composeapp.generated.resources.svg_export_name
import dodeclusters.composeapp.generated.resources.swap_direction_name
import dodeclusters.composeapp.generated.resources.three_sliders
import dodeclusters.composeapp.generated.resources.toggle_direction_arrows_description
import dodeclusters.composeapp.generated.resources.toggle_direction_arrows_name
import dodeclusters.composeapp.generated.resources.toggle_filled_or_outline_description
import dodeclusters.composeapp.generated.resources.toggle_filled_or_outline_name
import dodeclusters.composeapp.generated.resources.toggle_select_all_description
import dodeclusters.composeapp.generated.resources.toggle_select_all_name
import dodeclusters.composeapp.generated.resources.two_of_three_circles_connected
import dodeclusters.composeapp.generated.resources.undo
import dodeclusters.composeapp.generated.resources.undo_name
import dodeclusters.composeapp.generated.resources.upload
import dodeclusters.composeapp.generated.resources.visible_circle
import dodeclusters.composeapp.generated.resources.visible_haired_arrow
import domain.SIGNATURE_1_POINT
import domain.SIGNATURE_2_CIRCLES
import domain.SIGNATURE_2_GENERALIZED_CIRCLES
import domain.SIGNATURE_2_POINTS
import domain.SIGNATURE_3_GENERALIZED_CIRCLE
import domain.SIGNATURE_INDEXED_AND_2_POINTS
import domain.SIGNATURE_INDEXED_AND_CIRCLE
import domain.SIGNATURE_N_POINTS_PLACEHOLDER
import domain.Signature
import domain.io.DdcV2
import domain.io.DdcV4
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringArrayResource
import org.jetbrains.compose.resources.StringResource
import ui.theme.DodeclustersColors

@Immutable
sealed class EditClusterTool(
    override val name: StringResource,
    override val description: StringResource = name,
    override val icon: DrawableResource,
) : Tool {
    sealed class Switch(
        name: StringResource,
        description: StringResource = name,
        icon: DrawableResource,
        final override val disabledIcon: DrawableResource? = null
    ) : EditClusterTool(name, description, icon), Tool.BinaryToggle
    sealed class MultiArg(
        final override val signature: Signature,
        name: StringResource,
        description: StringResource = name,
        final override val argDescriptions: StringArrayResource,
        icon: DrawableResource,
        final override val disabledIcon: DrawableResource? = null,
    ) : EditClusterTool(name, description, icon), Tool.BinaryToggle, Tool.MultiArg
    sealed class Action(
        name: StringResource,
        description: StringResource = name,
        icon: DrawableResource,
    ) : EditClusterTool(name, description, icon), Tool.InstantAction
    sealed class ContextAction(
        name: StringResource,
        description: StringResource = name,
        icon: DrawableResource,
    ) : Action(name, description, icon), Tool.ContextAction
    sealed class CustomAction(
        name: StringResource,
        description: StringResource = name,
        icon: DrawableResource,
    ) : Action(name, description, icon)


    // top toolbar
    data object SaveCluster: CustomAction(
        Res.string.save_cluster_name,
        icon = Res.drawable.save
    ) {
        const val DEFAULT_NAME = DdcV4.DEFAULT_NAME
        const val EXTENSION = DdcV4.DEFAULT_EXTENSION // yml
        val otherDisplayedExtensions = setOf("yaml", "ddc", "ddu")
        const val MIME_TYPE = "application/yaml"
    }
    data object SvgExport: CustomAction(
        Res.string.svg_export_name,
        icon = Res.drawable.upload
    ) {
        const val DEFAULT_NAME = DdcV4.DEFAULT_NAME
        const val EXTENSION = "svg"
        const val MIME_TYPE = "image/svg+xml" // apparently this is highly contested (since svg can contain js)
    }
    data object PngExport: CustomAction(
        Res.string.png_export_name,
        icon = Res.drawable.upload
    ) {
        const val DEFAULT_NAME = "dodeclusters-screenshot"
        const val EXTENSION = "png"
        const val MIME_TYPE = "image/png"
    }
    data object OpenFile: CustomAction(
        Res.string.open_file_name,
        icon = Res.drawable.open_file
    )
    data object Undo: Action(
        Res.string.undo_name,
        icon = Res.drawable.undo
    )
    data object Redo: Action(
        Res.string.redo_name,
        icon = Res.drawable.redo
    )

    // bottom/left toolbar
    data object Drag: Switch(
        Res.string.drag_name,
        Res.string.drag_description,
        Res.drawable.drag_mode_1_circle
    )
    data object Multiselect: Switch(
        Res.string.multiselect_name,
        Res.string.multiselect_description,
        Res.drawable.multiselect
    )
    data object RectangularSelect: Switch(
        Res.string.rectangular_select_name,
        Res.string.rectangular_select_description,
        Res.drawable.dotted_rectangle
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
        Res.drawable.fill_region
    )
    data object FlowFill: Switch(
        Res.string.flow_fill_name,
        Res.string.flow_fill_description,
        Res.drawable.fill_swiped_circles
    )
    data object FillChessboardPattern: Switch(
        Res.string.fill_chessboard_pattern_name,
        Res.string.fill_chessboard_pattern_description,
        Res.drawable.chessboard,
        Res.drawable.chessboard_crossed,
    ) {
        val alternativeEnabledIcon = Res.drawable.chessboard_reflected
    }
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
        Res.drawable.visible_circle,
        Res.drawable.crossed_circle
    )
    data object ToggleFilledOrOutline: Switch(
        Res.string.toggle_filled_or_outline_name,
        Res.string.toggle_filled_or_outline_description,
        Res.drawable.filled_circle,
        Res.drawable.circle
    ) // TODO: instead adjust it for every part
    data object HideUI: Action(
        Res.string.hide_ui_name,
        Res.string.hide_ui_description,
        Res.drawable.fullscreen
    )
    data object ToggleDirectionArrows: Switch(
        Res.string.toggle_direction_arrows_name,
        Res.string.toggle_direction_arrows_description,
        Res.drawable.visible_haired_arrow,
        Res.drawable.hide_haired_arrow,
    )
    data object AddBackgroundImage: Action(
        Res.string.change_background_name,
        Res.string.change_background_description,
        Res.drawable.add_image
    ) // NOTE: critical multiplatform image decoding is available now

    data object Palette: Action(
        Res.string.palette_name,
        Res.string.palette_description,
        Res.drawable.palette
    )
    data class AppliedColor(val color: Color): Action(
        Res.string.applied_color_name,
        Res.string.applied_color_description,
        Res.drawable.paint_splash, // tint=color should be applied
    )

    data object CircleInversion: MultiArg(
        SIGNATURE_INDEXED_AND_CIRCLE,
//        PartialArgList.SIGNATURE_SELECTED_CIRCLES_AND_CIRCLE,
        Res.string.circle_inversion_name,
        Res.string.circle_inversion_description,
        Res.array.circle_inversion_arg_descriptions,
        Res.drawable.circle_inversion
    )
    data object CircleInterpolation: MultiArg(
        SIGNATURE_2_CIRCLES,
        Res.string.circle_interpolation_name,
        Res.string.circle_interpolation_description,
        Res.array.circle_interpolation_arg_descriptions,
        Res.drawable.interpolate_lines
    )
    data object CircleExtrapolation: MultiArg(
        SIGNATURE_2_CIRCLES,
        Res.string.circle_extrapolation_name,
        Res.string.circle_extrapolation_description,
        Res.array.circle_extrapolation_arg_descriptions,
        Res.drawable.extrapolate_lines
    )
    data object LoxodromicMotion: MultiArg(
        SIGNATURE_INDEXED_AND_2_POINTS,
        Res.string.loxodromic_motion_name,
        Res.string.loxodromic_motion_description,
        Res.array.loxodromic_motion_arg_descriptions,
        Res.drawable.spiral
    )

    // MAYBE: add partial argument icon(s)
    data object ConstructCircleByCenterAndRadius: MultiArg(
        SIGNATURE_2_POINTS,
        Res.string.circle_by_center_and_radius_name,
        Res.string.circle_by_center_and_radius_description,
        Res.array.circle_by_center_and_radius_arg_descriptions,
        Res.drawable.circle_center_and_radius_point
    )
    data object ConstructCircleBy3Points: MultiArg(
        SIGNATURE_3_GENERALIZED_CIRCLE,
        Res.string.circle_by_3_points_name,
        Res.string.circle_by_3_points_description,
        Res.array.circle_by_3_points_arg_descriptions,
        Res.drawable.circle_3_points
    )
    data object ConstructCircleByPencilAndPoint: MultiArg(
        SIGNATURE_3_GENERALIZED_CIRCLE,
        Res.string.circle_by_pencil_and_point_name,
        Res.string.circle_by_pencil_and_point_description,
        Res.array.circle_by_pencil_and_point_arg_descriptions,
        Res.drawable.propeller
    )
    data object ConstructLineBy2Points: MultiArg(
        SIGNATURE_2_GENERALIZED_CIRCLES,
        Res.string.line_by_2_points_name,
        Res.string.line_by_2_points_description,
        Res.array.line_by_2_points_arg_descriptions,
        Res.drawable.line_2_points
    )
    data object InsertCenteredCross: Action(
        Res.string.insert_centered_cross_name,
        Res.string.insert_centered_cross_description,
        Res.drawable.inserted_cross
    )
    data object ConstructArcPath: MultiArg(
        SIGNATURE_N_POINTS_PLACEHOLDER,
        Res.string.arc_path_name,
        Res.string.arc_path_description,
        Res.array.arc_path_arg_descriptions,
        Res.drawable.shark_fin_striped
    )
    data object CompleteArcPath: ContextAction(
        Res.string.complete_arc_path,
        icon = Res.drawable.confirm
    )
    data object AddPoint: MultiArg(
        SIGNATURE_1_POINT,
        Res.string.add_point_name,
        Res.string.add_point_description,
        Res.array.add_point_arg_descriptions,
        Res.drawable.flagged_point
    )
    // insert rect/square

    // these are inlined into canvas HUD
    data object Expand: ContextAction(
        Res.string.expand_name,
        icon = Res.drawable.expand
    )
    data object Shrink: ContextAction(
        Res.string.shrink_name,
        icon = Res.drawable.shrink
    )
    data object Duplicate: ContextAction(
        Res.string.duplicate_name,
        Res.string.duplicate_description,
        Res.drawable.copy
    ), Tool.Tinted {
        override val tint = DodeclustersColors.skyBlue.copy(alpha = 0.9f)
    }
    data object PickCircleColor: ContextAction(
        Res.string.pick_circle_color_name,
        Res.string.pick_circle_color_description,
        Res.drawable.paint_splash
    )
    // MAYBE: eraser-like mode
    data object Delete: ContextAction(
        Res.string.delete_name,
        Res.string.delete_description,
        Res.drawable.delete_forever
    ), Tool.Tinted {
        override val tint = DodeclustersColors.lightRed.copy(alpha = 0.9f)
    }
    data object Detach: ContextAction(
        Res.string.detach_name,
        icon = Res.drawable.lock_open
    )
    data object SwapDirection: ContextAction(
        Res.string.swap_direction_name,
        icon = Res.drawable.right_left
    )
    data object DetailedAdjustment: ContextAction(
        Res.string.stub,
        icon = Res.drawable.three_sliders
    )
    data object InBetween: ContextAction(
        Res.string.stub,
        icon = Res.drawable.road
    )
}