package domain

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect
import data.geometry.Circle
import data.geometry.CircleOrLine
import data.geometry.CircleOrLineOrPoint
import data.geometry.EPSILON
import data.geometry.Line
import data.geometry.Point
import data.geometry.closestPerpendicularPoint
import data.geometry.perpendicularDistance
import data.geometry.translatedUntilTangency
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

// MAYBE: fuse with PointSnapResult
//  into SnapResult<O: GCircle>
@Immutable
sealed interface CircleSnapResult {
    val result: CircleOrLine
    data class Free(override val result: CircleOrLine) : CircleSnapResult
    // Equality case is practically inapplicable to circle-circle snaps
    data class Tangent(override val result: CircleOrLine, val tangentIndex: Ix) : CircleSnapResult
    data class BiTangent(
        override val result: CircleOrLine,
        val tangent1Index: Ix,
        val tangent2Index: Ix,
    ) : CircleSnapResult {
        init {
            tangent1Index != tangent2Index
        }
    }
}

// NOTE: dont forget to exclude [circle], its immediate parents and all children from snappanbles
fun snapCircleToCircles(
    circle: CircleOrLine,
    circlesLinesOrPoints: List<CircleOrLineOrPoint?>,
    snapDistance: Double,
    bitangentTolerance: Double = 1.2,
    visibleRect: Rect? = null,
): CircleSnapResult {
    val closestCircles: List<Ix> = circlesLinesOrPoints.asSequence()
        .mapIndexed { ix, c ->
            ix to abs(c?.perpendicularDistance(circle) ?: Double.POSITIVE_INFINITY)
        }
        .filter { (ix, d) ->
            d <= snapDistance && circlesLinesOrPoints[ix]?.let { o ->
                visibleRect
                    ?.contains(circle.closestPerpendicularPoint(o).toOffset())
                    ?: true
            } ?: false
        }
        .sortedBy { (_, d) -> d }
        .take(2)
        .map { (ix, _) -> ix }
        .toList() // get 2 closest circles
    return when {
        closestCircles.isEmpty() -> {
            CircleSnapResult.Free(circle)
        }
        circle is Line || closestCircles.size == 1  -> {
            // line cannot snap to 2 objects (without rotation)
            val ix1 = closestCircles.first()
            val c1 = circlesLinesOrPoints[ix1]!!
            val newCircle = circle.translatedUntilTangency(c1)
            CircleSnapResult.Tangent(newCircle, ix1)
        }
        else -> { // 2 tangents
            val (ix1, ix2) = closestCircles
            val c = circle as Circle
            val c1 = circlesLinesOrPoints[ix1]!!
            val c2 = circlesLinesOrPoints[ix2]!!
            val newCircle = c.translatedUntilBiTangency(c1, c2)
            if (newCircle == null ||
                newCircle.distanceBetweenCenters(c) >= bitangentTolerance * snapDistance
            ) {
                return CircleSnapResult.Tangent(c.translatedUntilTangency(c1), ix1)
            }
            CircleSnapResult.BiTangent(newCircle, ix1, ix2)
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

fun PointSnapResult.toArgPoint(): Arg.Point =
    when (this) {
        is PointSnapResult.Eq -> Arg.PointIndex(this.pointIndex)
        is PointSnapResult.Free -> Arg.PointXY(this.result)
        else -> Arg.PointXY(this.result)
    }
