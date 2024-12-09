package domain.cluster

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect
import data.geometry.Circle
import data.geometry.CircleOrLine
import data.geometry.Point
import data.geometry.RegionPointLocation
import domain.ColorAsCss
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * @param[arcs] [List] of indices (_starting from 1!_) of circles from which the
 * arcs are chosen, __prefixed__ by +/- depending on whether the direction of
 * the arc coincides with the direction of the circle
 * @param[isClosed] when `false` the last arc is considered invisible and only used
 * to denote start & end points
 * */
@Immutable
@Serializable
@SerialName("arcPath")
data class AbstractArcPath(
    // NOTE: cyclic order doesn't matter, but reversing it alternates between one of
    //  2 possible regions that arise when not considering circle order
    @SerialName("arcIndicesStartingFrom1WithMinusIndicatingReversedDirection")
    val arcs: List<Int>,
    val isClosed: Boolean = true,
    val fillColor: ColorAsCss? = null,
    val borderColor: ColorAsCss? = null,
) {
    // previous arc: external orientation
    // next arc: internal orientation
    fun toConcrete(allCircles: List<CircleOrLine>): ConcreteArcPath {
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
        return ConcreteArcPath(
            circles, intersectionPoints, isClosed, fillColor, borderColor
        )
    }
}

// to distinguish in/out, connect any point to-the-left to the infinity and
// count how many arcs the line intersects
/**
 * @param[intersectionPoints] `null`s correspond to non-intersecting circles
 * */
@Immutable
@Serializable
data class ConcreteArcPath(
    val circles: List<CircleOrLine>,
    val intersectionPoints: List<Point?>,
    val isClosed: Boolean,
    val fillColor: ColorAsCss?,
    val borderColor: ColorAsCss?,
) {
    @Transient
    val size: Int = circles.size
    @Transient
    val indices: IntRange = circles.indices
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

    fun calculatePointLocation(point: Point): RegionPointLocation {
        // test if the point lies on any of the arcs
        // construct straight line through the point (prob horizontal, eastward)
        // check how it intersects the arcs, and order those intersections along the line
        // if unresolvable, choose another straight line
        TODO()
    }
}