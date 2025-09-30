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
sealed class Category(
    override val name: StringResource,
    override val tools: List<Tool>,
    final override val defaultables: List<Int> = listOf(0),
    final override val default: Tool? = defaultables.firstOrNull()?.let { tools[it] },
    final override val icon: DrawableResource? = null
) : ICategory {

    data object Drag : Category(
        Res.string.drag_category_name,
        listOf(Tool.Drag)
    ) // ~mode-like
    data object Multiselect : Category(
        Res.string.multiselect_category_name,
        listOf(
            Tool.Multiselect,
            Tool.RectangularSelect,
            Tool.FlowSelect,
            Tool.ToggleSelectAll,
        ),
        defaultables = listOf(0, 1, 2)
    ) // ~mode-like
    data object Region : Category(
        Res.string.region_category_name,
        listOf(
            Tool.Region,
            Tool.FlowFill,
            Tool.FillChessboardPattern,
            Tool.RestrictRegionToSelection,
            Tool.DeleteAllParts,
            Tool.BlendSettings,
            // Tool.AppliedColor's are auto-added
        ),
        defaultables = listOf(0, 1)
    ) // ~mode-like
    data object Visibility : Category(
        Res.string.visibility_category_name,
        listOf(
            Tool.StereographicRotation,
            Tool.ToggleObjects,
            Tool.TogglePhantoms,
//            Tool.ToggleFilledOrOutline,
            Tool.ToggleDirectionArrows,
            Tool.HideUI,
            Tool.AddBackgroundImage,
        ),
        defaultables = emptyList(),
        icon = Res.drawable.visible
    )
    data object Colors : Category(
        Res.string.colors_category_name,
        listOf(
            Tool.Palette
        )
    ) // ~button-like
    data object Transform : Category(
        Res.string.transform_category_name,
        listOf(
            Tool.CircleInversion,
            Tool.LoxodromicMotion,
            Tool.CircleOrPointInterpolation,
            Tool.Rotation,
            Tool.BiInversion,
        ),
        defaultables = listOf(0, 1, 2, 3, 4),
    ) { // ~mode-like
        // button: kaleidoscopic reflection
    }
    data object Create : Category(
        Res.string.create_category_name,
        listOf(
            Tool.ConstructCircleByCenterAndRadius,
            Tool.ConstructCircleBy3Points,
            Tool.ConstructLineBy2Points,
            Tool.AddPoint,
            Tool.ConstructCircleByPencilAndPoint,
            Tool.ConstructPolarityByCircleAndLineOrPoint,
            Tool.InsertCenteredCross,
            Tool.ConstructArcPath,
        ),
        defaultables = listOf(0, 1, 2, 3, 4, 5)
    ) { // ~mode-like
        // mode: rectangle by top-left & bottom-right
        // mode: polygon
    }
}
