package ui.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.center
import dodeclusters.composeapp.generated.resources.circle_3_points
import dodeclusters.composeapp.generated.resources.copy
import dodeclusters.composeapp.generated.resources.invisible
import dodeclusters.composeapp.generated.resources.visible

sealed interface EditClusterTool : Tool {
    data object ShowCircles : Tool.BinaryToggle, EditClusterTool {
        override val name = "Show circles"
        override val description = "Show/hide all circle outlines"
        override val iconResource = IconResource.AsDrawable(Res.drawable.visible)
        override val disabledIconResource = IconResource.AsDrawable(Res.drawable.invisible)
    }
    data object Duplicate : Tool.ActionOnSelection, EditClusterTool {
        override val name = "Dupicate"
        override val description = "Duplicate selected circles and then select the new copies"
        override val iconResource = IconResource.AsDrawable(Res.drawable.copy)
    }
    data object Delete : Tool.ActionOnSelection, EditClusterTool {
        override val name = "Delete"
        override val description = "Copy selected circles and then select the copies"
        override val iconResource = IconResource.AsImageVector(Icons.Default.Delete)
    }
    // MAYBE: add partial argument icon(s)
    data object ConstructCircleByCenterAndRadius : Tool.MultiArg2<InputType.AnyPoint, InputType.AnyPoint>, EditClusterTool {
        override val name = "Circle by center and radius"
        override val description = "Construct a circle by its center and radius point"
        override val iconResource = IconResource.AsDrawable(Res.drawable.center)
        override val argDescriptions = listOf("Circle's center", "Any point on the circle")
    }
    data object ConstructCircleBy3Points : Tool.MultiArg3<InputType.AnyPoint, InputType.AnyPoint, InputType.AnyPoint>, EditClusterTool {
        override val name = "Circle by 3 points"
        override val description = "Construct a circle by 3 points lying on it"
        override val iconResource = IconResource.AsDrawable(Res.drawable.circle_3_points)
        override val argDescriptions = listOf("1st point on the circle", "2nd point on the circle", "3rd point on the circle")
    }
}