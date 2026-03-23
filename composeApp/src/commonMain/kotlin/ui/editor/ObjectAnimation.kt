package ui.editor

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import core.geometry.CircleOrLine
import core.geometry.GCircle
import domain.ColorAsCss
import domain.model.ConcreteArcPath
import domain.model.SaveState
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

/** params for create/copy/delete animations */
@Immutable
@Serializable
sealed class ArcPathAnimation(
    open val concreteArcPaths: List<ConcreteArcPath>,
    val color: ColorAsCss,
) {
    val maxAlpha: Float = 0.2f
    val alpha01Duration: Int = 50
    val alpha10Duration: Int = 1_500
    val fillCircle: Boolean = true

    /** Animation for creating new arc-paths */
    data class Entrance(override val concreteArcPaths: List<ConcreteArcPath>) :
        ArcPathAnimation(concreteArcPaths, Color.Green)
    /** Animation for duplicating arc-paths */
    data class ReEntrance(override val concreteArcPaths: List<ConcreteArcPath>) :
        ArcPathAnimation(concreteArcPaths, Color.Blue)
    /** Animation for deleting arc-paths */
    data class Exit(override val concreteArcPaths: List<ConcreteArcPath>) :
        ArcPathAnimation(concreteArcPaths, Color.Red)
}
