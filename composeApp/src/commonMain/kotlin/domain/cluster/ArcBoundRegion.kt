package domain.cluster

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.util.fastZipWithNext
import data.geometry.Circle
import data.geometry.CircleOrLine
import data.geometry.Line
import data.geometry.Point
import domain.ColorAsCss
import domain.zip3
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
@Immutable
/**
 * @param[arcs] [List] of indices (_starting from 1!_) of circles from which the
 * arcs are chosen, __prefixed__ by +/- depending on whether the direction of
 * the arc corresponds to the direction of the circle
 * */
data class ArcBoundRegion(
    // NOTE: cyclic order doesn't matter, but reversing it alternates between one of
    //  2 possible regions that arise when not considering circle order
    @SerialName("arcIndicesStartingFrom1WithSignIndicatingReversedDirection")
    val arcs: List<Int>,
    val fillColor: ColorAsCss?,
    val borderColor: ColorAsCss?,
) {
    // previous arc: external orientation
    // next arc: internal orientation
    fun toConcrete(allCircles: List<CircleOrLine>): ConcreteArcBoundRegion {
        val circles = arcs.map { i ->
            if (i > 0) allCircles[i-1]
            else allCircles[i-1].reversed()
        }
        val intersectionPoints: MutableList<Point?> = mutableListOf()
        if (arcs.size > 1) {
            var previous: CircleOrLine = circles.last()
            var next: CircleOrLine = circles.first()
            var intersection: List<Point> = Circle.calculateIntersectionPoints(next, previous)
            val startingPoint = intersection.firstOrNull()
            intersectionPoints.add(startingPoint)
            for (ix in arcs.indices.drop(1)) {
                previous = circles[ix - 1]
                next = circles[ix]
                intersection = Circle.calculateIntersectionPoints(next, previous)
                intersectionPoints.add(intersection.firstOrNull())
            }
        }
        return ConcreteArcBoundRegion(
            circles, intersectionPoints, fillColor, borderColor
        )
    }
}

/**
 * @param[intersectionPoints] `null`s correspond to non-intersecting circles
 * */
@Serializable
@Immutable
data class ConcreteArcBoundRegion(
    val circles: List<CircleOrLine>,
    val intersectionPoints: List<Point?>,
    val fillColor: ColorAsCss?,
    val borderColor: ColorAsCss?,
) {
    @Transient
    val isContinuous: Boolean = intersectionPoints.none { it == null }
    @Transient
    val rects: List<Rect> = circles.map { circle ->
        if (circle is Circle)
            Rect(circle.center, circle.radius.toFloat())
        else
            Rect.Zero
    }
    /** clockwise, [0°; 360°)
     *
     * [reference](https://developer.android.com/reference/android/graphics/Path#arcTo(android.graphics.RectF,%20float,%20float))
     * */
    @Transient
    val startAngles: List<Float> = circles.zip(intersectionPoints) { circle, startPoint ->
        if (startPoint != null && circle is Circle)
            (-circle.point2angle(startPoint)).rem(360)
        else
            0f
    }
    /**
     * clockwise, [0°; 360°)
     *
     * [reference](https://developer.android.com/reference/android/graphics/Path#arcTo(android.graphics.RectF,%20float,%20float))
     */
    @Transient
    val sweepAngles: List<Float> = circles.mapIndexed { ix, circle ->
        val previousPoint = intersectionPoints[ix]
        val nextIndex = (ix + 1).mod(circles.size)
        val nextPoint = intersectionPoints[nextIndex]
        if (circle is Circle && previousPoint != null && nextPoint != null) {
            val startAngle = startAngles[ix]
            val endAngle = circle.point2angle(nextPoint)
            if (circle.isCCW)
                (startAngle - endAngle).rem(360f)
            else
                (endAngle - startAngle).rem(360f)
        } else 0f
    }

    init {
        require(circles.size == intersectionPoints.size)
    }
}

class NonContinuousArcContourError(
    vararg circles: CircleOrLine,
) : IllegalArgumentException("Non-continuous arc-contour $circles")