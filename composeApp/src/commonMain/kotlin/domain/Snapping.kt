package domain

import androidx.compose.runtime.Immutable
import data.geometry.Circle
import data.geometry.CircleOrLine
import data.geometry.EPSILON
import data.geometry.Line
import data.geometry.Point
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

@Immutable
sealed interface PointSnapResult {
    val result: Point
    sealed interface PointToPoint : PointSnapResult
    sealed interface PointToCircle : PointSnapResult

    data class Free(override val result: Point) : PointToPoint, PointToCircle
    data class Eq(override val result: Point, val pointIndex: Ix) : PointToPoint
    data class Incidence(
        override val result: Point,
        val circleIndex: Ix
    ) : PointToCircle
    data class Intersection(
        override val result: Point,
        val circle1Index: Ix,
        val circle2index: Ix
    ) : PointToCircle {
        init {
            require(circle1Index != circle2index)
        }
    }
}

/** Snaps when to 45 degree marks [snapMarkDeg] when closer than 5% [angleSnapPercent] */
fun snapAngle(
    angleDeg: Double,
    angleSnapPercent: Double = 5.0,
    snapMarkDeg: Double = 45.0
): Double {
    if (abs(angleDeg) <= snapMarkDeg/2)
        return angleDeg // no snapping to 0
    val mod = angleDeg.mod(snapMarkDeg) // mod is always >= 0
    val div = (angleDeg - mod)/snapMarkDeg
    val threshold = snapMarkDeg*angleSnapPercent/100
    return if (mod < threshold) {
        div * snapMarkDeg
    } else if ((snapMarkDeg - mod) < threshold) {
        (div + 1) * snapMarkDeg
    } else {
        angleDeg
    }
}

fun snapPointToPoints(
    point: Point,
    points: List<Point?>,
    snapDistance: Double
): PointSnapResult.PointToPoint {
    val withinSnapDistance = points.mapIndexed { ix, p ->
        ix to (p?.distanceFrom(point) ?: Double.POSITIVE_INFINITY)
    }.filter { (_, d) -> d <= snapDistance }
    if (withinSnapDistance.isEmpty())
        return PointSnapResult.Free(point)
    // because of triple-intersects, we want to resolve them uniformly
    // regardless of position, so natural points order is an easy choice.
    val minDistance = withinSnapDistance.minOf { it.second }
    val oldestButCloseEnough =
        withinSnapDistance.first { it.second < minDistance + EPSILON }
    val ix = oldestButCloseEnough.first
    return PointSnapResult.Eq(points[ix]!!, pointIndex = ix)
//    val ix = points
//        .mapIndexed { ix, p ->
//            ix to (p?.distanceFrom(point) ?: Double.POSITIVE_INFINITY)
//        }
//        .filter { (_, d) -> d <= snapDistance }
//        .minByOrNull { (_, d) -> d }
//        ?.first
//    return if (ix == null)
//        PointSnapResult.Free(point)
//    else
//        PointSnapResult.Eq(points[ix]!!, pointIndex = ix)
}

/** Project [point] onto the closest circle among [circles] that
 * are closer than [snapDistance] from it.
 *
 * @param[intersectionTolerance] how much easier it is to snap to an intersection than
 * to an individual circle
 * */
fun snapPointToCircles(
    point: Point,
    circles: List<CircleOrLine?>,
    snapDistance: Double,
    intersectionTolerance: Double = 1.5
): PointSnapResult.PointToCircle {
    val closestCircles: List<Ix> = circles.asSequence()
        .mapIndexed { ix, p ->
            ix to (p?.distanceFrom(point) ?: Double.POSITIVE_INFINITY)
        }
        .filter { (_, d) -> d <= snapDistance }
        .sortedBy { (_, d) -> d }
        .take(2)
        .map { (ix, _) -> ix }
        .toList() // get 2 closest circles
    return if (closestCircles.isEmpty()) {
        PointSnapResult.Free(point)
    } else if (closestCircles.size == 1) {
        val ix = closestCircles.first()
        val circle = circles[ix]!!
        PointSnapResult.Incidence(
            circle.project(point),
            circleIndex = ix
        )
    } else {
        // NOTE: triple intersections introduce chaos
        val (ix1, ix2) = closestCircles
        val c1 = circles[ix1]!!
        val c2 = circles[ix2]!!
        val intersections = Circle.calculateIntersectionPoints(c1, c2)
        if (intersections.isEmpty()) {
            PointSnapResult.Incidence(
                c1.project(point),
                circleIndex = ix1
            )
        } else {
            val (closestIntersection, distance) = intersections
                .map { it to point.distanceFrom(it) }
                .minByOrNull { (_, d) -> d }!!
            if (distance <= intersectionTolerance * snapDistance) {
                val sortedIxs = listOf(ix1, ix2).sorted()
                PointSnapResult.Intersection(
                    closestIntersection,
                    circle1Index = sortedIxs[0],
                    circle2index = sortedIxs[1]
                )
            } else {
                PointSnapResult.Incidence(
                    c1.project(point),
                    circleIndex = ix1
                )
            }
        }
    }
}

// from pencil thru p1^p2 choose the one that touches/snaps to the closest of [circles]
/** Construct a circle thru [p1] and [p2] that snaps to [circles] from
 * the initial circle `p1^p2^freePoint` */
fun snapCircleThru3PointsToCircles(p1: Point, p2: Point, freePoint: Point, circles: List<CircleOrLine>): CircleOrLine {
    // circle -> circle
    // line -> circle
    // circle -> line
    // line to line: no snapping
    // 2 -> snap to intersection
    // NOTE: involves Apollonian problem with 2 cycles degenerated to points
    // MAYBE: create separate instrument for Apollonian triad via Lie cycles
    TODO()
}

// MAYBE: use -1 for intersections
fun distance(c1: CircleOrLine, c2: CircleOrLine): Double =
    when {
        c1 is Circle && c2 is Circle -> {
            val d = hypot(c2.x - c1.x, c2.y - c1.y)
            if (d >= c1.radius + c2.radius) { // side-by-side case o-o
                d - c1.radius - c2.radius
            } else { // one inside the other case (o)
                val minR = min(c1.radius, c2.radius)
                val maxR = max(c1.radius, c2.radius)
                val distance = maxR - (d + minR)
                if (distance >= 0.0)
                    distance
                else
                    0.0 // intersections
            }
        }
        c1 is Line && c2 is Line -> {
            val l1 = c1.normalizedNoDirection()
            val l2 = c2.normalizedNoDirection()
            if (l1 isCollinearTo l2)
                abs(l1.c - l2.c)
            else
                0.0
        }
        c1 is Line && c2 is Circle -> {
            val d = c1.distanceFrom(c2.centerPoint)
            if (d > c2.radius)
                d - c2.radius
            else
                0.0
        }
        else -> distance(c2, c1) // circle & line
    }

fun PointSnapResult.PointToPoint.toArgPoint(): Arg.Point =
    when (this) {
        is PointSnapResult.Free -> Arg.Point.XY(this.result)
        is PointSnapResult.Eq -> Arg.Point.Index(this.pointIndex)
    }
