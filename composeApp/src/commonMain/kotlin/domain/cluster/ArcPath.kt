package domain.cluster

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect
import data.geometry.Circle
import data.geometry.CircleOrLine
import data.geometry.GCircle
import data.geometry.Point
import data.geometry.RegionPointLocation
import data.geometry.calculateAngle
import domain.ColorAsCss
import domain.Ix
import domain.TAU
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.math.abs

/**
 * @property[arcs] [List] of indices (_starting from 1!_) of circles/lines from which the
 * arcs are chosen, __prefixed__ by +/- depending on whether the direction of
 * the arc coincides with the direction of the circle/line. When not [isClosed] the first and
 * the last indices specify start/end points in addition
 * */
@Immutable
@Serializable
@SerialName("ArcPath")
sealed interface ArcPath {
    @SerialName("arcIndicesStartingFrom1WithMinusIndicatingReversedDirection")
    val arcs: List<Int>
    val borderColor: ColorAsCss?
}

/** Looping arc-path */
@Immutable
@Serializable
@SerialName("ClosedArcPath")
data class ClosedArcPath(
    // NOTE: cyclic order doesn't matter, but reversing it alternates between one of
    //  2 possible regions that arise when not considering circle order
    @SerialName("arcIndicesStartingFrom1WithMinusIndicatingReversedDirection")
    override val arcs: List<Int>,
    val fillColor: ColorAsCss? = null,
    override val borderColor: ColorAsCss? = null,
) : ArcPath {

    // previous arc: external orientation
    // next arc: internal orientation
    // TODO: when consecutive arcs dont intersect, fuse them the other way thru conformal infinity
    fun toConcrete(allObjects: List<GCircle?>): ConcreteClosedArcPath? {
        val circles = arcs.map { i ->
            val circleOrLine = allObjects[i-1] as CircleOrLine
            if (i > 0) circleOrLine
            else circleOrLine.reversed()
        }
        val intersectionPoints: MutableList<Point> = mutableListOf()
        if (arcs.size > 1) {
            var previous: CircleOrLine = circles.last()
            var next: CircleOrLine = circles.first()
            var intersection: List<Point> = Circle.calculateIntersectionPoints(next, previous)
            val startingPoint = intersection.firstOrNull() ?: return null
            intersectionPoints.add(startingPoint)
            for (ix in arcs.indices.drop(1)) {
                previous = circles[ix - 1]
                next = circles[ix]
                intersection = Circle.calculateIntersectionPoints(next, previous)
                // order of intersection points is stable
                val point = intersection.firstOrNull() ?: return null
                intersectionPoints.add(point)
            }
        }
        return ConcreteClosedArcPath(
            circles, intersectionPoints, fillColor, borderColor
        )
    }
}

/** Non-looping arc-path
 *
 * Assumption: [startPoint] lies on [arcs]`.first()` and
 * [endPoint] lies on [arcs]`.last()`
 * */
@Immutable
@Serializable
@SerialName("OpenArcPath")
data class OpenArcPath(
    val startPoint: Ix,
    val endPoint: Ix,
    @SerialName("arcIndicesStartingFrom1WithMinusIndicatingReversedDirection")
    override val arcs: List<Int>,
    override val borderColor: ColorAsCss? = null,
) : ArcPath

@Immutable
@Serializable
sealed interface ConcreteArcPath

// to distinguish in/out, connect any point to-the-left to the infinity and
// count how many arcs the line intersects, or the winding algorithm
@Immutable
@Serializable
data class ConcreteClosedArcPath(
    val circles: List<CircleOrLine>,
    val intersectionPoints: List<Point>,
    val fillColor: ColorAsCss?,
    val borderColor: ColorAsCss?,
) : ConcreteArcPath {
    @Transient
    val size: Int = circles.size
    @Transient
    val indices: IntRange = circles.indices
    /** Whether it contains inside the CONFORMAL_INFINITY point */
    @Transient
    val isBounded: Boolean = TODO("i need to think about it")
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
        if (circle is Circle)
            (360 - circle.point2angle(startPoint)) % 360
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
        if (circle is Circle) {
            if (size == 1) { // singular full circle case
                return@mapIndexed 360f
            }
            val startAngle = startAngles[ix]
            val endAngle = circle.point2angle(nextPoint)
            if (circle.isCCW)
                (360 + startAngle - endAngle) % 360f
            else
                (360 + endAngle - startAngle) % 360f
        } else 0f
    }

    init {
        require(circles.size == intersectionPoints.size)
    }

    // good reference algorithms: https://en.wikipedia.org/wiki/Point_in_polygon
    fun calculatePointLocation(point: Point): RegionPointLocation {
        // test if the point lies on any of the arcs
        // construct straight line through the point (prob horizontal, eastward)
        // check how it intersects the arcs, and order those intersections along the line
        // if unresolvable, choose another straight line
        if (size == 1 && circles[0] is Circle) { // single full circle case
            val circle = circles[0] as Circle
            return circle.calculateLocationEpsilon(point)
        }
        // cumulative winding angle == 0 => the point is inside
        var windingAngle: Double = 0.0
        for (i in indices) {
            val arcStart = intersectionPoints[i] // closed arcpath => all intersections are present
            val arcEnd = intersectionPoints[(i + 1) % size]
            val circle = circles[i]
            val location = circle.calculateLocationEpsilon(point)
            if (location == RegionPointLocation.BORDERING) {
                val inBetween = circle.orderIsInBetween(
                    order = circle.point2order(point),
                    startOrder = circle.point2order(arcStart),
                    endOrder = circle.point2order(arcEnd),
                )
                if (inBetween)
                    return RegionPointLocation.BORDERING
            }
            val angle = calculateAngle(point, arcStart, arcEnd) // positive => we are 'in'
            if (circle is Circle) { // circular arcs require a patch
                val angleIsCCW = angle >= 0.0
                val dAngle = if (angleIsCCW) { // positive, CCW angle
                    if (circle.isCCW) { // extruding arc doesn't matter
                        angle
                    } else { // intruding arc
                        if (circle.hasInsideEpsilon(point))
                            angle
                        else
                            angle - TAU
                    }
                } else { // negative, CW angle
                    if (circle.isCCW) { // intruding arc
                        if (circle.hasOutsideEpsilon(point))
                            angle
                        else angle + TAU
                    } else { // extruding arc doesn't matter
                        angle
                    }
                }
                println("winding angle += $dAngle (angle = $angle)")
                windingAngle += dAngle
            } else {
                println("winding angle += $angle")
                windingAngle += angle
            }
        }
        println("calculatePointLocation($point): windingAngle = $windingAngle")
        val soThePointIsOutside = abs(windingAngle) > 0.1 // small threshold just in case
        return if (soThePointIsOutside == isBounded)
            RegionPointLocation.OUT
        else
            RegionPointLocation.IN
    }
}

/**
 * @param[intersectionPoints] include `startPoint` and `endPoint`
 * */
@Immutable
@Serializable
data class ConcreteOpenArcPath(
    val circles: List<CircleOrLine>,
    val intersectionPoints: List<Point>,
    val borderColor: ColorAsCss?,
) : ConcreteArcPath {
    @Transient
    val startPoint: Point = intersectionPoints.first()

    @Transient
    val endPoint: Point = intersectionPoints.last()

    /** number of arcs in the path, aka [circles]`.size` */
    @Transient
    val size: Int = circles.size

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
    val startAngles: List<Float> =
        circles.zip(intersectionPoints.dropLast(1)) { circle, startPoint ->
            if (circle is Circle)
                (360 - circle.point2angle(startPoint)) % 360
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
        val nextIndex = (ix + 1) % size
        val nextPoint = intersectionPoints[nextIndex]
        if (circle is Circle) {
            val startAngle = startAngles[ix]
            val endAngle = circle.point2angle(nextPoint) // [-180; +180]
            if (circle.isCCW)
                (360 + startAngle - endAngle) % 360f
            else
                (360 + endAngle - startAngle) % 360f
        } else 0f
    }

    init {
        require(size >= 1)
        require(intersectionPoints.size == size + 1)
        require(circles.first().hasBorderingEpsilon(startPoint))
        require(circles.last().hasBorderingEpsilon(endPoint))
    }
}