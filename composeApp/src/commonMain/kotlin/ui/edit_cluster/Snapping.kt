package ui.edit_cluster

import androidx.compose.ui.geometry.Offset
import data.geometry.Circle
import data.geometry.CircleOrLine
import data.geometry.Line
import data.geometry.Point
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

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

fun snapToCircles(
    point: Offset,
    circles: List<CircleOrLine>,
    snapDistance: Double
): Point {
    val p = Point.fromOffset(point)
    val closestCircles = circles.asSequence()
        .map { it to it.distanceFrom(point) }
        .filter { (_, d) -> d <= snapDistance }
        .sortedBy { (_, d) -> d }
        .take(2)
        .map { (c, _) -> c }
        .toList() // get 2 closest circles
    return if (closestCircles.isEmpty())
        p
    else if (closestCircles.size == 1)
        closestCircles.first().project(p)
    else {
        val (c1, c2) = closestCircles
        val intersections = Circle.calculateIntersectionPoints(c1, c2)
        if (intersections.isEmpty())
            c1.project(p)
        else {
            val intersectionTolerance = 1.5
            val (closestIntersection, distance) = intersections
                .map { it to p.distanceFrom(it) }
                .minByOrNull { (_, d) -> d }!!
            if (distance <= intersectionTolerance * snapDistance)
                closestIntersection
            else
                c1.project(p)
        }
    }
}

// from pencil thru p1^p2 choose the one that touches/snaps to the closest of [circles]
/** Construct a circle thru [p1] and [p2] that snaps to [circles] from
 * the initial circle `p1^p2^freePoint` */
fun snapToCircles(p1: Point, p2: Point, freePoint: Point, circles: List<CircleOrLine>): CircleOrLine {
    // circle -> circle
    // line -> circle
    // circle -> line
    // line to line: no snapping
    // 2 -> snap to intersection
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
            val d = c1.distanceFrom(c2.center)
            if (d > c2.radius)
                d - c2.radius
            else
                0.0
        }
        else -> distance(c2, c1) // circle & line
    }