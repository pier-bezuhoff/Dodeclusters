package ui.tools

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.add_image
import dodeclusters.composeapp.generated.resources.add_point_arg_descriptions
import dodeclusters.composeapp.generated.resources.add_point_description
import dodeclusters.composeapp.generated.resources.add_point_name
import dodeclusters.composeapp.generated.resources.adjust_expr_description
import dodeclusters.composeapp.generated.resources.adjust_expr_name
import dodeclusters.composeapp.generated.resources.applied_color_description
import dodeclusters.composeapp.generated.resources.applied_color_name
import dodeclusters.composeapp.generated.resources.arc_path_arg_descriptions
import dodeclusters.composeapp.generated.resources.arc_path_description
import dodeclusters.composeapp.generated.resources.arc_path_name
import dodeclusters.composeapp.generated.resources.bi_inversion_arg_descriptions
import dodeclusters.composeapp.generated.resources.bi_inversion_description
import dodeclusters.composeapp.generated.resources.bi_inversion_name
import dodeclusters.composeapp.generated.resources.bidirectional_spiral
import dodeclusters.composeapp.generated.resources.bidirectional_spiral_description
import dodeclusters.composeapp.generated.resources.bidirectional_spiral_disabled_description
import dodeclusters.composeapp.generated.resources.bidirectional_spiral_name
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
import dodeclusters.composeapp.generated.resources.circle_tangent
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
import dodeclusters.composeapp.generated.resources.detach_description
import dodeclusters.composeapp.generated.resources.detach_name
import dodeclusters.composeapp.generated.resources.detailed_adjustment_description
import dodeclusters.composeapp.generated.resources.detailed_adjustment_name
import dodeclusters.composeapp.generated.resources.dotted_rectangle
import dodeclusters.composeapp.generated.resources.double_reflection
import dodeclusters.composeapp.generated.resources.drag_description
import dodeclusters.composeapp.generated.resources.drag_mode_1_circle
import dodeclusters.composeapp.generated.resources.drag_name
import dodeclusters.composeapp.generated.resources.duplicate_description
import dodeclusters.composeapp.generated.resources.duplicate_name
import dodeclusters.composeapp.generated.resources.expand
import dodeclusters.composeapp.generated.resources.expand_description
import dodeclusters.composeapp.generated.resources.expand_name
import dodeclusters.composeapp.generated.resources.extrapolate_lines
import dodeclusters.composeapp.generated.resources.fill_chessboard_pattern_alternative_description
import dodeclusters.composeapp.generated.resources.fill_chessboard_pattern_description
import dodeclusters.composeapp.generated.resources.fill_chessboard_pattern_disabled_description
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
import dodeclusters.composeapp.generated.resources.in_between_description
import dodeclusters.composeapp.generated.resources.in_between_disabled_description
import dodeclusters.composeapp.generated.resources.in_between_name
import dodeclusters.composeapp.generated.resources.infinite_point_description
import dodeclusters.composeapp.generated.resources.infinite_point_name
import dodeclusters.composeapp.generated.resources.infinity
import dodeclusters.composeapp.generated.resources.insert_centered_cross_description
import dodeclusters.composeapp.generated.resources.insert_centered_cross_name
import dodeclusters.composeapp.generated.resources.inserted_cross
import dodeclusters.composeapp.generated.resources.interpolate_lines
import dodeclusters.composeapp.generated.resources.intersection_settings
import dodeclusters.composeapp.generated.resources.line_2_points
import dodeclusters.composeapp.generated.resources.line_by_2_points_arg_descriptions
import dodeclusters.composeapp.generated.resources.line_by_2_points_description
import dodeclusters.composeapp.generated.resources.line_by_2_points_name
import dodeclusters.composeapp.generated.resources.lock_open
import dodeclusters.composeapp.generated.resources.loxodromic_motion_arg_descriptions
import dodeclusters.composeapp.generated.resources.loxodromic_motion_description
import dodeclusters.composeapp.generated.resources.loxodromic_motion_name
import dodeclusters.composeapp.generated.resources.mark_as_phantoms_description
import dodeclusters.composeapp.generated.resources.mark_as_phantoms_disabled_description
import dodeclusters.composeapp.generated.resources.mark_as_phantoms_name
import dodeclusters.composeapp.generated.resources.menu
import dodeclusters.composeapp.generated.resources.move_point_to_infinity_description
import dodeclusters.composeapp.generated.resources.move_point_to_infinity_name
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
import dodeclusters.composeapp.generated.resources.phantom
import dodeclusters.composeapp.generated.resources.phantom_crossed
import dodeclusters.composeapp.generated.resources.pick_circle_color_description
import dodeclusters.composeapp.generated.resources.pick_circle_color_name
import dodeclusters.composeapp.generated.resources.png_export_name
import dodeclusters.composeapp.generated.resources.polarity_by_circle_and_line_or_point_arg_descriptions
import dodeclusters.composeapp.generated.resources.polarity_by_circle_and_line_or_point_description
import dodeclusters.composeapp.generated.resources.polarity_by_circle_and_line_or_point_name
import dodeclusters.composeapp.generated.resources.propeller
import dodeclusters.composeapp.generated.resources.rectangular_select_description
import dodeclusters.composeapp.generated.resources.rectangular_select_name
import dodeclusters.composeapp.generated.resources.redo
import dodeclusters.composeapp.generated.resources.redo_name
import dodeclusters.composeapp.generated.resources.region_description
import dodeclusters.composeapp.generated.resources.region_name
import dodeclusters.composeapp.generated.resources.regions_blend_settings_description
import dodeclusters.composeapp.generated.resources.regions_blend_settings_name
import dodeclusters.composeapp.generated.resources.restrict_region_to_selection_description
import dodeclusters.composeapp.generated.resources.restrict_region_to_selection_disabled_description
import dodeclusters.composeapp.generated.resources.restrict_region_to_selection_name
import dodeclusters.composeapp.generated.resources.reverse_direction_description
import dodeclusters.composeapp.generated.resources.reverse_direction_name
import dodeclusters.composeapp.generated.resources.right_left
import dodeclusters.composeapp.generated.resources.road
import dodeclusters.composeapp.generated.resources.rotation_arg_descriptions
import dodeclusters.composeapp.generated.resources.rotation_around_point
import dodeclusters.composeapp.generated.resources.rotation_description
import dodeclusters.composeapp.generated.resources.rotation_name
import dodeclusters.composeapp.generated.resources.save
import dodeclusters.composeapp.generated.resources.save_cluster_name
import dodeclusters.composeapp.generated.resources.screenshot_pc
import dodeclusters.composeapp.generated.resources.select_all
import dodeclusters.composeapp.generated.resources.set_label_description
import dodeclusters.composeapp.generated.resources.set_label_name
import dodeclusters.composeapp.generated.resources.shark_fin_striped
import dodeclusters.composeapp.generated.resources.shrink
import dodeclusters.composeapp.generated.resources.shrink_description
import dodeclusters.composeapp.generated.resources.shrink_name
import dodeclusters.composeapp.generated.resources.spinning_sphere
import dodeclusters.composeapp.generated.resources.spiral
import dodeclusters.composeapp.generated.resources.stereographic_rotation_description
import dodeclusters.composeapp.generated.resources.stereographic_rotation_name
import dodeclusters.composeapp.generated.resources.stub
import dodeclusters.composeapp.generated.resources.svg_export_name
import dodeclusters.composeapp.generated.resources.swap_direction_description
import dodeclusters.composeapp.generated.resources.swap_direction_name
import dodeclusters.composeapp.generated.resources.text
import dodeclusters.composeapp.generated.resources.three_sliders
import dodeclusters.composeapp.generated.resources.to_infinity
import dodeclusters.composeapp.generated.resources.toggle_direction_arrows_description
import dodeclusters.composeapp.generated.resources.toggle_direction_arrows_disabled_description
import dodeclusters.composeapp.generated.resources.toggle_direction_arrows_name
import dodeclusters.composeapp.generated.resources.toggle_filled_or_outline_description
import dodeclusters.composeapp.generated.resources.toggle_filled_or_outline_name
import dodeclusters.composeapp.generated.resources.toggle_objects_description
import dodeclusters.composeapp.generated.resources.toggle_objects_disabled_description
import dodeclusters.composeapp.generated.resources.toggle_objects_name
import dodeclusters.composeapp.generated.resources.toggle_phantoms_description
import dodeclusters.composeapp.generated.resources.toggle_phantoms_disabled_description
import dodeclusters.composeapp.generated.resources.toggle_phantoms_name
import dodeclusters.composeapp.generated.resources.toggle_select_all_description
import dodeclusters.composeapp.generated.resources.toggle_select_all_disabled_description
import dodeclusters.composeapp.generated.resources.toggle_select_all_name
import dodeclusters.composeapp.generated.resources.two_of_three_circles_connected
import dodeclusters.composeapp.generated.resources.two_vertical_sliders
import dodeclusters.composeapp.generated.resources.undo
import dodeclusters.composeapp.generated.resources.undo_name
import dodeclusters.composeapp.generated.resources.upload
import dodeclusters.composeapp.generated.resources.visible
import dodeclusters.composeapp.generated.resources.visible_circle
import dodeclusters.composeapp.generated.resources.visible_haired_arrow
import domain.NonEqualityCondition
import domain.SIGNATURE_1_FINITE_POINT
import domain.SIGNATURE_2_CIRCLES
import domain.SIGNATURE_2_GENERALIZED_FINITE_CIRCLES
import domain.SIGNATURE_2_FINITE_POINTS
import domain.SIGNATURE_3_GENERALIZED_CIRCLE
import domain.SIGNATURE_INDICES_AND_2_CIRCLES
import domain.SIGNATURE_INDICES_AND_2_POINTS
import domain.SIGNATURE_INDICES_AND_CIRCLE
import domain.SIGNATURE_INDICES_AND_FINITE_POINT
import domain.SIGNATURE_N_POINTS_PLACEHOLDER
import domain.SIGNATURE_REAL_CIRCLE_AND_LINE_OR_FINITE_POINT
import domain.Signature
import domain.io.DdcV4
import domain.nonEqualityConditionsOf
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringArrayResource
import org.jetbrains.compose.resources.StringResource
import ui.theme.DodeclustersColors

@Immutable
sealed class Tool(
    override val name: StringResource,
    override val description: StringResource = name,
    override val icon: DrawableResource,
) : ITool {
    sealed class Switch(
        name: StringResource,
        description: StringResource = name,
        final override val disabledDescription: StringResource = description,
        icon: DrawableResource,
        final override val disabledIcon: DrawableResource? = null,
    ) : Tool(name, description, icon), ITool.BinaryToggle
    sealed class MultiArg(
        name: StringResource,
        description: StringResource = name,
        final override val argDescriptions: StringArrayResource,
        icon: DrawableResource,
        final override val signature: Signature,
        final override val disabledDescription: StringResource = description,
        final override val disabledIcon: DrawableResource? = null,
        final override val nonEqualityConditions: List<NonEqualityCondition> = emptyList(),
    ) : Tool(name, description, icon), ITool.BinaryToggle, ITool.MultiArg {
        init {
            require(nonEqualityConditions.all {
                it.index1 < signature.size && it.index2 < signature.size
            })
        }
    }
    sealed class Action(
        name: StringResource,
        description: StringResource = name,
        icon: DrawableResource,
    ) : Tool(name, description, icon), ITool.InstantAction
    sealed class ContextAction(
        name: StringResource,
        description: StringResource = name,
        icon: DrawableResource,
    ) : Action(name, description, icon), ITool.ContextAction
    sealed class CustomAction(
        name: StringResource,
        description: StringResource = name,
        icon: DrawableResource,
    ) : Action(name, description, icon)


    // top toolbar
    data object ToggleMenu : CustomAction(
        Res.string.stub,
        icon = Res.drawable.menu,
    )
    data object SaveCluster : CustomAction(
        Res.string.save_cluster_name,
        icon = Res.drawable.save
    ) {
        const val DEFAULT_NAME = DdcV4.DEFAULT_NAME
        const val EXTENSION = DdcV4.DEFAULT_EXTENSION // yml
        val otherDisplayedExtensions = setOf("yaml", "ddc", "ddu")
        const val MIME_TYPE = "application/yaml"
    }
    data object SvgExport : CustomAction(
        Res.string.svg_export_name,
        icon = Res.drawable.upload
    ) {
        const val DEFAULT_NAME = DdcV4.DEFAULT_NAME
        const val EXTENSION = "svg"
        const val MIME_TYPE = "image/svg+xml" // apparently this is highly contested (since svg can contain js)
    }
    data object PngExport : CustomAction(
        Res.string.png_export_name,
        icon = Res.drawable.screenshot_pc
    ) {
        const val DEFAULT_NAME = "dodeclusters-screenshot"
        const val EXTENSION = "png"
        const val MIME_TYPE = "image/png"
    }
    data object OpenFile : CustomAction(
        name = Res.string.open_file_name,
        icon = Res.drawable.open_file,
    )
    data object Undo : Action(
        name = Res.string.undo_name,
        icon = Res.drawable.undo,
    )
    data object Redo : Action(
        name = Res.string.redo_name,
        icon = Res.drawable.redo,
    )

    // bottom/left toolbar
    data object Drag : Switch(
        name = Res.string.drag_name,
        description = Res.string.drag_description,
        icon = Res.drawable.drag_mode_1_circle,
    )
    data object Multiselect : Switch(
        name = Res.string.multiselect_name,
        description = Res.string.multiselect_description,
        icon = Res.drawable.multiselect,
    )
    data object RectangularSelect : Switch(
        name = Res.string.rectangular_select_name,
        description = Res.string.rectangular_select_description,
        icon = Res.drawable.dotted_rectangle,
    )
    data object FlowSelect : Switch(
        name = Res.string.flow_multiselect_name,
        description = Res.string.flow_multiselect_description,
        icon = Res.drawable.two_of_three_circles_connected,
    )
    data object ToggleSelectAll : Switch(
        name = Res.string.toggle_select_all_name,
        description = Res.string.toggle_select_all_description,
        disabledDescription = Res.string.toggle_select_all_disabled_description,
        icon = Res.drawable.select_all,
        disabledIcon = Res.drawable.deselect,
    )

    data object Region : Switch(
        name = Res.string.region_name,
        description = Res.string.region_description,
        icon = Res.drawable.fill_region,
    )
    data object FlowFill : Switch(
        name = Res.string.flow_fill_name,
        description = Res.string.flow_fill_description,
        icon = Res.drawable.fill_swiped_circles,
    )
    data object FillChessboardPattern : Tool(
        name = Res.string.fill_chessboard_pattern_name,
        description = Res.string.fill_chessboard_pattern_description,
        icon = Res.drawable.chessboard,
    ), ITool.TernaryToggle {
        override val alternativeIcon: DrawableResource = Res.drawable.chessboard_reflected
        override val alternativeDescription: StringResource = Res.string.fill_chessboard_pattern_alternative_description
        override val disabledIcon: DrawableResource = Res.drawable.chessboard_crossed
        override val disabledDescription: StringResource = Res.string.fill_chessboard_pattern_disabled_description
    }
    data object RestrictRegionToSelection : Switch(
        name = Res.string.restrict_region_to_selection_name,
        description = Res.string.restrict_region_to_selection_description,
        disabledDescription = Res.string.restrict_region_to_selection_disabled_description,
        icon = Res.drawable.circled_region,
        disabledIcon = Res.drawable.open_region,
    )
    data object DeleteAllParts : Action(
        name = Res.string.delete_all_parts_name,
        description = Res.string.delete_all_parts_description,
        icon = Res.drawable.hide_layers,
    )
    data object BlendSettings : Action(
        name = Res.string.regions_blend_settings_name,
        description = Res.string.regions_blend_settings_description,
        icon = Res.drawable.intersection_settings,
    )

    data object StereographicRotation : Switch(
        name = Res.string.stereographic_rotation_name,
        description = Res.string.stereographic_rotation_description,
        icon = Res.drawable.spinning_sphere,
    )
    data object ToggleObjects : Switch(
        name = Res.string.toggle_objects_name,
        description = Res.string.toggle_objects_description,
        disabledDescription = Res.string.toggle_objects_disabled_description,
        icon = Res.drawable.visible_circle,
        disabledIcon = Res.drawable.crossed_circle,
    )
    data object TogglePhantoms : Switch(
        name = Res.string.toggle_phantoms_name,
        description = Res.string.toggle_phantoms_description,
        disabledDescription = Res.string.toggle_phantoms_disabled_description,
        icon = Res.drawable.phantom,
        disabledIcon = Res.drawable.phantom_crossed,
    )
    data object ToggleFilledOrOutline : Switch( // presently unused
        name = Res.string.toggle_filled_or_outline_name,
        description = Res.string.toggle_filled_or_outline_description,
        icon = Res.drawable.filled_circle,
        disabledIcon = Res.drawable.circle,
    ) // TODO: instead adjust it for every part
    data object HideUI : Action(
        name = Res.string.hide_ui_name,
        description = Res.string.hide_ui_description,
        icon = Res.drawable.fullscreen
    )
    data object ToggleDirectionArrows : Switch(
        name = Res.string.toggle_direction_arrows_name,
        description = Res.string.toggle_direction_arrows_description,
        disabledDescription = Res.string.toggle_direction_arrows_disabled_description,
        icon = Res.drawable.visible_haired_arrow,
        disabledIcon = Res.drawable.hide_haired_arrow,
    )
    data object AddBackgroundImage : Action(
        name = Res.string.change_background_name,
        description = Res.string.change_background_description,
        icon = Res.drawable.add_image,
    ) // NOTE: critical multiplatform image decoding is available now

    data object Palette : Action(
        name = Res.string.palette_name,
        description = Res.string.palette_description,
        icon = Res.drawable.palette
    )
    data class AppliedColor(val color: Color) : Action(
        name = Res.string.applied_color_name,
        description = Res.string.applied_color_description,
        icon = Res.drawable.paint_splash, // tint=color should be applied
    )

    data object CircleInversion : MultiArg(
        name = Res.string.circle_inversion_name,
        description = Res.string.circle_inversion_description,
        argDescriptions = Res.array.circle_inversion_arg_descriptions,
        icon = Res.drawable.circle_inversion,
        signature = SIGNATURE_INDICES_AND_CIRCLE,
        nonEqualityConditions = nonEqualityConditionsOf(0 to 1),
    )
    data object CircleOrPointInterpolation : MultiArg(
        name = Res.string.circle_interpolation_name,
        description = Res.string.circle_interpolation_description,
        argDescriptions = Res.array.circle_interpolation_arg_descriptions,
        icon = Res.drawable.interpolate_lines,
        signature = SIGNATURE_2_GENERALIZED_FINITE_CIRCLES,
        nonEqualityConditions = nonEqualityConditionsOf(0 to 1),
    )
    data object Rotation : MultiArg(
        name = Res.string.rotation_name,
        description = Res.string.rotation_description,
        argDescriptions = Res.array.rotation_arg_descriptions,
        icon = Res.drawable.rotation_around_point,
        signature = SIGNATURE_INDICES_AND_FINITE_POINT,
        nonEqualityConditions = nonEqualityConditionsOf(0 to 1),
    )
    data object BiInversion : MultiArg(
        name = Res.string.bi_inversion_name,
        description = Res.string.bi_inversion_description,
        argDescriptions = Res.array.bi_inversion_arg_descriptions,
        icon = Res.drawable.double_reflection,
        signature = SIGNATURE_INDICES_AND_2_CIRCLES,
        nonEqualityConditions = nonEqualityConditionsOf(1 to 2),
    )
    data object LoxodromicMotion : MultiArg(
        name = Res.string.loxodromic_motion_name,
        description = Res.string.loxodromic_motion_description,
        argDescriptions = Res.array.loxodromic_motion_arg_descriptions,
        icon = Res.drawable.spiral,
        signature = SIGNATURE_INDICES_AND_2_POINTS,
        nonEqualityConditions = nonEqualityConditionsOf(0 to 1, 0 to 2, 1 to 2),
    )
    data object CircleExtrapolation : MultiArg(
        name = Res.string.circle_extrapolation_name,
        description = Res.string.circle_extrapolation_description,
        argDescriptions = Res.array.circle_extrapolation_arg_descriptions,
        icon = Res.drawable.extrapolate_lines,
        signature = SIGNATURE_2_CIRCLES,
        nonEqualityConditions = nonEqualityConditionsOf(0 to 1),
    )

    // MAYBE: add partial argument icon(s)
    data object ConstructCircleByCenterAndRadius : MultiArg(
        name = Res.string.circle_by_center_and_radius_name,
        description = Res.string.circle_by_center_and_radius_description,
        argDescriptions = Res.array.circle_by_center_and_radius_arg_descriptions,
        icon = Res.drawable.circle_center_and_radius_point,
        signature = SIGNATURE_2_FINITE_POINTS,
        nonEqualityConditions = nonEqualityConditionsOf(0 to 1),
    )
    data object ConstructCircleBy3Points : MultiArg(
        name = Res.string.circle_by_3_points_name,
        description = Res.string.circle_by_3_points_description,
        argDescriptions = Res.array.circle_by_3_points_arg_descriptions,
        icon = Res.drawable.circle_3_points,
        signature = SIGNATURE_3_GENERALIZED_CIRCLE,
        nonEqualityConditions = nonEqualityConditionsOf(0 to 1, 0 to 2, 1 to 2),
    )
    data object ConstructLineBy2Points : MultiArg(
        name = Res.string.line_by_2_points_name,
        description = Res.string.line_by_2_points_description,
        argDescriptions = Res.array.line_by_2_points_arg_descriptions,
        icon = Res.drawable.line_2_points,
        signature = SIGNATURE_2_GENERALIZED_FINITE_CIRCLES,
        nonEqualityConditions = nonEqualityConditionsOf(0 to 1),
    )
    data object AddPoint : MultiArg(
        name = Res.string.add_point_name,
        description = Res.string.add_point_description,
        argDescriptions = Res.array.add_point_arg_descriptions,
        icon = Res.drawable.flagged_point,
        signature = SIGNATURE_1_FINITE_POINT,
    )
    data object ConstructCircleByPencilAndPoint : MultiArg(
        name = Res.string.circle_by_pencil_and_point_name,
        description = Res.string.circle_by_pencil_and_point_description,
        argDescriptions = Res.array.circle_by_pencil_and_point_arg_descriptions,
        icon = Res.drawable.propeller,
        signature = SIGNATURE_3_GENERALIZED_CIRCLE,
        nonEqualityConditions = nonEqualityConditionsOf(0 to 1),
    )
    data object ConstructPolarityByCircleAndLineOrPoint : MultiArg(
        name = Res.string.polarity_by_circle_and_line_or_point_name,
        description = Res.string.polarity_by_circle_and_line_or_point_description,
        argDescriptions = Res.array.polarity_by_circle_and_line_or_point_arg_descriptions,
        icon = Res.drawable.circle_tangent,
        signature = SIGNATURE_REAL_CIRCLE_AND_LINE_OR_FINITE_POINT,
    )
    data object InsertCenteredCross : Action(
        name = Res.string.insert_centered_cross_name,
        description = Res.string.insert_centered_cross_description,
        icon = Res.drawable.inserted_cross
    )
    data object ConstructArcPath : MultiArg(
        name = Res.string.arc_path_name,
        description = Res.string.arc_path_description,
        argDescriptions = Res.array.arc_path_arg_descriptions,
        icon = Res.drawable.shark_fin_striped,
        signature = SIGNATURE_N_POINTS_PLACEHOLDER,
        // none 2 should be equal
    )
    data object CompleteArcPath : ContextAction(
        name = Res.string.complete_arc_path,
        icon = Res.drawable.confirm
    )
    // insert rect/square

    // these are inlined into canvas HUD
    data object Expand : ContextAction(
        name = Res.string.expand_name,
        description = Res.string.expand_description,
        icon = Res.drawable.expand
    )
    data object Shrink : ContextAction(
        name = Res.string.shrink_name,
        description = Res.string.shrink_description,
        icon = Res.drawable.shrink
    )
    data object AdjustExpr : ContextAction(
        name = Res.string.adjust_expr_name,
        description = Res.string.adjust_expr_description,
        icon = Res.drawable.two_vertical_sliders
    )
    data object PickCircleColor : ContextAction(
        name = Res.string.pick_circle_color_name,
        description = Res.string.pick_circle_color_description,
        icon = Res.drawable.paint_splash
    )
    data object SetLabel : ContextAction(
        name = Res.string.set_label_name,
        description = Res.string.set_label_description,
        icon = Res.drawable.text
    )
    data object MarkAsPhantoms : ContextAction(
        name = Res.string.mark_as_phantoms_name,
        description = Res.string.mark_as_phantoms_description,
        icon = Res.drawable.visible,
    ), ITool.BinaryToggle {
        override val disabledIcon = Res.drawable.phantom
        override val disabledDescription = Res.string.mark_as_phantoms_disabled_description
    }
    data object SwapDirection : ContextAction(
        name = Res.string.swap_direction_name,
        description = Res.string.swap_direction_description,
        icon = Res.drawable.right_left
    )
    data object Detach : ContextAction(
        name = Res.string.detach_name,
        description = Res.string.detach_description,
        icon = Res.drawable.lock_open
    )
    data object Duplicate : ContextAction(
        name = Res.string.duplicate_name,
        description = Res.string.duplicate_description,
        icon = Res.drawable.copy
    ), ITool.Tinted {
        override val tint = DodeclustersColors.skyBlue.copy(alpha = 0.9f)
    }
    // MAYBE: eraser-like mode
    data object Delete : ContextAction(
        name = Res.string.delete_name,
        description = Res.string.delete_description,
        icon = Res.drawable.delete_forever
    ), ITool.Tinted {
        override val tint = DodeclustersColors.lightRed.copy(alpha = 0.9f)
    }
    data object DetailedAdjustment : ContextAction(
        name = Res.string.detailed_adjustment_name,
        description = Res.string.detailed_adjustment_description,
        icon = Res.drawable.three_sliders
    )
    data object InBetween : ContextAction(
        name = Res.string.in_between_name,
        description = Res.string.in_between_description,
        icon = Res.drawable.road
    ), ITool.BinaryToggle {
        override val disabledIcon = Res.drawable.road
        override val disabledDescription = Res.string.in_between_disabled_description
    }
    data object ReverseDirection : ContextAction(
        name = Res.string.reverse_direction_name,
        description = Res.string.reverse_direction_description,
        icon = Res.drawable.right_left
    )
    data object BidirectionalSpiral : ContextAction(
        name = Res.string.bidirectional_spiral_name,
        description = Res.string.bidirectional_spiral_description,
        icon = Res.drawable.bidirectional_spiral
    ) {
        val disabledDescription = Res.string.bidirectional_spiral_disabled_description
    }
    data object InfinitePoint : ContextAction(
        name = Res.string.infinite_point_name,
        description = Res.string.infinite_point_description,
        icon = Res.drawable.infinity,
    )
    data object MovePointToInfinity : ContextAction(
        name = Res.string.move_point_to_infinity_name,
        description = Res.string.move_point_to_infinity_description,
        icon = Res.drawable.to_infinity,
    )
}