package ui.edit_cluster

import androidx.compose.runtime.Immutable
import domain.Signature
import kotlinx.serialization.Serializable
import ui.tools.Tool
import kotlin.jvm.Transient

@Immutable
sealed interface Mode {
    val tool: Tool

    fun isSelectingCircles(): Boolean =
        this == SelectionMode.Drag || this == SelectionMode.Multiselect
}

@Immutable
@Serializable
enum class SelectionMode(
    @Transient
    override val tool: Tool
) : Mode {
    /** Select & drag singular circles */
    Drag(Tool.Drag),
    /** Select multiple circles */
    Multiselect(Tool.Multiselect),
    /** Select regions to create new [LogicalRegion]s */
    Region(Tool.Region),
}

@Immutable
@Serializable
enum class ViewMode(
    @Transient
    override val tool: Tool
) : Mode {
    StereographicRotation(Tool.StereographicRotation),
}

@Immutable
@Serializable
enum class ToolMode(
    @Transient
    override val tool: Tool.MultiArg
) : Mode {
    CIRCLE_INVERSION(Tool.CircleInversion),
    CIRCLE_OR_POINT_INTERPOLATION(Tool.CircleOrPointInterpolation),
    ROTATION(Tool.Rotation),
    BI_INVERSION(Tool.BiInversion),
    LOXODROMIC_MOTION(Tool.LoxodromicMotion),
    CIRCLE_EXTRAPOLATION(Tool.CircleExtrapolation),

    CIRCLE_BY_CENTER_AND_RADIUS(Tool.ConstructCircleByCenterAndRadius),
    CIRCLE_BY_3_POINTS(Tool.ConstructCircleBy3Points),
    LINE_BY_2_POINTS(Tool.ConstructLineBy2Points),
    POINT(Tool.AddPoint),
    CIRCLE_BY_PENCIL_AND_POINT(Tool.ConstructCircleByPencilAndPoint),
    POLARITY_BY_CIRCLE_AND_LINE_OR_POINT(Tool.ConstructPolarityByCircleAndLineOrPoint),
    ARC_PATH(Tool.ConstructArcPath),
    ;

    val signature: Signature = tool.signature

    companion object {
        fun correspondingTo(tool: Tool.MultiArg): ToolMode =
            ToolMode.entries.first { it.tool == tool }
    }
}

