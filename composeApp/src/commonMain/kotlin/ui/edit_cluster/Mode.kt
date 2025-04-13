package ui.edit_cluster

import androidx.compose.runtime.Immutable
import domain.Signature
import kotlinx.serialization.Serializable
import ui.tools.EditClusterTool
import kotlin.jvm.Transient

@Immutable
sealed interface Mode {
    val tool: EditClusterTool

    fun isSelectingCircles(): Boolean =
        this == SelectionMode.Drag || this == SelectionMode.Multiselect
}

@Immutable
@Serializable
enum class SelectionMode(
    @Transient
    override val tool: EditClusterTool
) : Mode {
    /** Select & drag singular circles */
    Drag(EditClusterTool.Drag),
    /** Select multiple circles */
    Multiselect(EditClusterTool.Multiselect),
    /** Select regions to create new [LogicalRegion]s */
    Region(EditClusterTool.Region),
}

@Immutable
@Serializable
enum class ViewMode(
    @Transient
    override val tool: EditClusterTool
) : Mode {
    SphereRotation(EditClusterTool.SphereRotation),
}

@Immutable
@Serializable
enum class ToolMode(
    @Transient
    override val tool: EditClusterTool.MultiArg
) : Mode {
    CIRCLE_INVERSION(EditClusterTool.CircleInversion),
    CIRCLE_OR_POINT_INTERPOLATION(EditClusterTool.CircleOrPointInterpolation),
    ROTATION(EditClusterTool.Rotation),
    BI_INVERSION(EditClusterTool.BiInversion),
    LOXODROMIC_MOTION(EditClusterTool.LoxodromicMotion),
    CIRCLE_EXTRAPOLATION(EditClusterTool.CircleExtrapolation),

    CIRCLE_BY_CENTER_AND_RADIUS(EditClusterTool.ConstructCircleByCenterAndRadius),
    CIRCLE_BY_3_POINTS(EditClusterTool.ConstructCircleBy3Points),
    LINE_BY_2_POINTS(EditClusterTool.ConstructLineBy2Points),
    POINT(EditClusterTool.AddPoint),
    CIRCLE_BY_PENCIL_AND_POINT(EditClusterTool.ConstructCircleByPencilAndPoint),
    POLARITY_BY_CIRCLE_AND_LINE_OR_POINT(EditClusterTool.ConstructPolarityByCircleAndLineOrPoint),
    ARC_PATH(EditClusterTool.ConstructArcPath),
    ;

    val signature: Signature = tool.signature

    companion object {
        fun correspondingTo(tool: EditClusterTool.MultiArg): ToolMode =
            ToolMode.entries.first { it.tool == tool }
    }
}

