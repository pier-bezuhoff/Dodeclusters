package ui.tools

import androidx.compose.runtime.Immutable
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.colors_category_name
import dodeclusters.composeapp.generated.resources.create_category_name
import dodeclusters.composeapp.generated.resources.drag_category_name
import dodeclusters.composeapp.generated.resources.multiselect_category_name
import dodeclusters.composeapp.generated.resources.region_category_name
import dodeclusters.composeapp.generated.resources.transform_category_name
import dodeclusters.composeapp.generated.resources.visibility_category_name
import dodeclusters.composeapp.generated.resources.visible
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
    ) // ~mode-like
    data object Multiselect : EditClusterCategory(
        Res.string.multiselect_category_name,
        listOf(
            EditClusterTool.Multiselect,
            EditClusterTool.RectangularSelect,
            EditClusterTool.FlowSelect,
            EditClusterTool.ToggleSelectAll,
        ),
        defaultables = listOf(0)
    ) // ~mode-like
    data object Region : EditClusterCategory(
        Res.string.region_category_name,
        listOf(
            EditClusterTool.Region,
            EditClusterTool.FlowFill,
            EditClusterTool.FillChessboardPattern,
            EditClusterTool.RestrictRegionToSelection,
            EditClusterTool.DeleteAllParts,
            EditClusterTool.BlendSettings,
            // EditClusterTool.AppliedColor's are auto-added
        ),
        defaultables = listOf(0, 1)
    ) // ~mode-like
    data object Visibility : EditClusterCategory(
        Res.string.visibility_category_name,
        listOf(
            EditClusterTool.SphereRotation,
            EditClusterTool.ToggleObjects,
            EditClusterTool.TogglePhantoms,
//            EditClusterTool.ToggleFilledOrOutline,
            EditClusterTool.ToggleDirectionArrows,
            EditClusterTool.HideUI,
            EditClusterTool.AddBackgroundImage,
        ),
        defaultables = emptyList(),
        icon = Res.drawable.visible
    )
    data object Colors : EditClusterCategory(
        Res.string.colors_category_name,
        listOf(
            EditClusterTool.Palette
        )
    ) // ~button-like
    data object Transform : EditClusterCategory(
        Res.string.transform_category_name,
        listOf(
            EditClusterTool.CircleInversion,
            EditClusterTool.LoxodromicMotion,
            EditClusterTool.CircleOrPointInterpolation,
            EditClusterTool.Rotation,
            EditClusterTool.BiInversion,
        ),
        defaultables = listOf(0, 1, 2, 3, 4),
    ) { // ~mode-like
        // button: kaleidoscopic reflection
    }
    data object Create : EditClusterCategory(
        Res.string.create_category_name,
        listOf(
            EditClusterTool.ConstructCircleByCenterAndRadius,
            EditClusterTool.ConstructCircleBy3Points,
            EditClusterTool.ConstructLineBy2Points,
            EditClusterTool.AddPoint,
            EditClusterTool.ConstructCircleByPencilAndPoint,
            EditClusterTool.ConstructPolarityByCircleAndLineOrPoint,
            EditClusterTool.InsertCenteredCross,
            EditClusterTool.ConstructArcPath,
        ),
        defaultables = listOf(0, 1, 2, 3, 4, 5)
    ) { // ~mode-like
        // mode: rectangle by top-left & bottom-right
        // mode: polygon
    }
}
