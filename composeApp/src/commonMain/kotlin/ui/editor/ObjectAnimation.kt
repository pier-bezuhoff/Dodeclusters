package ui.editor

import androidx.compose.runtime.Immutable
import core.geometry.GCircleOrConcreteAcPath
import domain.Ix

@Immutable
sealed interface ObjectAnimation {
    val objects: Map<Ix, GCircleOrConcreteAcPath>
}

/** Animation: alpha=0 .. alpha=[maxAlpha] .. alpha=0
 *
 * for colored object contour
 * @property[alpha01Duration] in milliseconds
 * @property[alpha10Duration] in milliseconds
 */
@Immutable
sealed interface ColoredContourAnimation : ObjectAnimation {
    val maxAlpha: Float
    val alpha01Duration: Int
    val alpha10Duration: Int
}

@Immutable
data class HighlightAnimation(
    override val objects: Map<Ix, GCircleOrConcreteAcPath>,
) : ColoredContourAnimation {
    override val maxAlpha: Float = 0.6f
    override val alpha01Duration: Int = 20
    override val alpha10Duration: Int = 500
}

/** params for create/copy/delete animations */
@Immutable
sealed class AppearanceAnimation(
    override val objects: Map<Ix, GCircleOrConcreteAcPath>,
) : ColoredContourAnimation {
    override val maxAlpha: Float = 0.2f
    override val alpha01Duration: Int = 50
    override val alpha10Duration: Int = 1_500

    /** Animation for creating new objects */
    data class Entrance(
        override val objects: Map<Ix, GCircleOrConcreteAcPath>
    ) : AppearanceAnimation(objects)
    /** Animation for duplicating objects */
    data class ReEntrance(
        override val objects: Map<Ix, GCircleOrConcreteAcPath>
    ) : AppearanceAnimation(objects)
    /** Animation for deleting objects */
    data class Exit(
        override val objects: Map<Ix, GCircleOrConcreteAcPath>
    ) : AppearanceAnimation(objects)
}
