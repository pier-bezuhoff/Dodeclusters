package ui.edit_cluster

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import data.geometry.CircleOrLine
import data.geometry.GCircle
import domain.ColorAsCss
import kotlinx.serialization.Serializable
import ui.theme.DodeclustersColors

@Immutable
@Serializable
sealed interface ObjectAnimation {
    val objects: List<GCircle>
}

/** Animation: alpha=0 .. alpha=[maxAlpha] .. alpha=0
 *
 * for colored object contour */
@Immutable
@Serializable
sealed interface ColoredContourAnimation : ObjectAnimation {
    val color: ColorAsCss
    val maxAlpha: Float
    val alpha01Duration: Int // in milliseconds
    val alpha10Duration: Int
    val fillCircle: Boolean
}

@Immutable
@Serializable
data class HighlightAnimation(
    override val objects: List<GCircle>,
    override val color: ColorAsCss = DodeclustersColors.skyBlue,
) : ColoredContourAnimation {
    override val maxAlpha: Float = 0.6f
    override val alpha01Duration: Int = 20
    override val alpha10Duration: Int = 500
    override val fillCircle: Boolean = false
}

/** params for create/copy/delete animations */
@Immutable
@Serializable
sealed class CircleAnimation(
    override val objects: List<GCircle>,
    override val color: ColorAsCss,
) : ColoredContourAnimation {
    override val maxAlpha: Float = 0.2f
    override val alpha01Duration: Int = 50
    override val alpha10Duration: Int = 1_500
    override val fillCircle: Boolean = true

    /** Animation for creating new circles */
    data class Entrance(override val objects: List<CircleOrLine>) :
        CircleAnimation(objects, Color.Green)
    /** Animation for duplicating circles */
    data class ReEntrance(override val objects: List<CircleOrLine>) :
        CircleAnimation(objects, Color.Blue)
    /** Animation for deleting circles */
    data class Exit(override val objects: List<CircleOrLine>) :
        CircleAnimation(objects, Color.Red)
}
