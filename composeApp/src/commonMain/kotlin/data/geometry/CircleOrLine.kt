package data.geometry

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sqrt

@Immutable
@Serializable
sealed interface CircleOrLine : GCircle, LocusWithOrder {
    fun project(point: Point): Point
    fun distanceFrom(point: Point): Double
    fun distanceFrom(point: Offset): Double
    fun calculateLocation(point: Offset): RegionPointLocation
    /** @return [RegionPointLocation.BORDERING] when the distance is in (-[EPSILON]; +[EPSILON]) */
    fun calculateLocationEpsilon(point: Point): RegionPointLocation
    fun hasInside(point: Offset): Boolean =
        calculateLocation(point) == RegionPointLocation.IN
    fun hasOutside(point: Offset): Boolean =
        calculateLocation(point) == RegionPointLocation.OUT
    fun hasInsideEpsilon(point: Point): Boolean =
        calculateLocationEpsilon(point) == RegionPointLocation.IN
    fun hasOutsideEpsilon(point: Point): Boolean =
        calculateLocationEpsilon(point) == RegionPointLocation.OUT
    /** = [point] is bordering `this` (within [EPSILON] distance) */
    fun hasBorderingEpsilon(point: Point): Boolean =
        calculateLocationEpsilon(point) == RegionPointLocation.BORDERING
    /** partial order ⊆ on circles (treated as either inside or outside regions) */
    infix fun isInside(circle: CircleOrLine): Boolean
    /** partial order ⊇ on circles (treated as either inside or outside regions)
     * `A isOutside B` == A ⊆ Bꟲ*/
    infix fun isOutside(circle: CircleOrLine): Boolean
    fun translated(vector: Offset): CircleOrLine
    fun scaled(focus: Offset, zoom: Float): CircleOrLine
    override fun scaled(focusX: Double, focusY: Double, zoom: Double): CircleOrLine
    fun rotated(focus: Offset, angleInDegrees: Float): CircleOrLine
    override fun reversed(): CircleOrLine
    /** @return tangent line to `this` object at [point], if the
     * [point] is not incident to `this` object, [project] it onto `this` object */
    fun tangentAt(point: Point): Line
}

/** Result of intersecting 2 [CircleOrLine]s */
@Immutable
sealed interface CircleLineIntersection {
    data object None : CircleLineIntersection
    /** The objects are equal, so they coincide */
    data object Eq : CircleLineIntersection
    data class Tangent(val tangentPoint: Point) : CircleLineIntersection
    data class Double(val point1: Point, val point2: Point) : CircleLineIntersection
}

// from Circle.calculateIntersectionPoints
/** @return Ordered intersection between [o1] and [o2]. When there are 2 intersection points,
 * they are ordered as follows:
 *
 * [o1] "needle" (internal orientation, imagine arrows)
 * goes thru
 * [o2] "fabric" (external orientation, in/out region specified).
 *
 * The entrance is the 1st, the
 * exit is the 2nd of the resulting points */
fun calculateIntersection(
    o1: CircleOrLine,
    o2: CircleOrLine,
): CircleLineIntersection {
    return when {
        o1 == o2 || o1 == o2.reversed() ->
            CircleLineIntersection.Eq
        o1 is Line && o2 is Line -> {
            val (a1, b1, c1) = o1
            val (a2, b2, c2) = o2
            val w = a1*b2 - a2*b1
            if (abs(w / o1.norm / o2.norm) < EPSILON) { // parallel condition
                CircleLineIntersection.Tangent(Point.CONFORMAL_INFINITY)
            } else {
                val wx = b1*c2 - b2*c1 // det in homogenous coordinates
                val wy = a2*c1 - a1*c2
                // we know that w != 0 (non-parallel)
                val p = Point(wx / w, wy / w)
                val q = Point.CONFORMAL_INFINITY
                if (o1.directionX*a2 + o1.directionY*b2 >= 0)
                    CircleLineIntersection.Double(p, q)
                else
                    CircleLineIntersection.Double(q, p)
            }
        }
        o1 is Line && o2 is Circle -> {
            val (cx, cy, r) = o2
            val (px, py) = o1.project(Point(cx, cy))
            val distance = hypot(px - cx, py - cy)
            if (distance > r + EPSILON) {
                CircleLineIntersection.None
            } else if (abs(distance - r) < EPSILON) { // they touch (hold hands >///<)
                CircleLineIntersection.Tangent(Point(px, py))
            } else {
                val pToIntersection = sqrt(r.pow(2) - distance * distance)
                val vx = o1.directionX
                val vy = o1.directionY
                val p = Point(px + vx * pToIntersection, py + vy * pToIntersection)
                val q = Point(px - vx * pToIntersection, py - vy * pToIntersection)
                val s = o1.pointInBetween(p, q) // directed segment p->s->q
                if (o2.hasInsideEpsilon(s))
                    CircleLineIntersection.Double(p, q)
                else
                    CircleLineIntersection.Double(q, p)
            }
        }
        o1 is Circle && o2 is Line -> {
            val (cx, cy, r) = o1
            val (px, py) = o2.project(Point(cx, cy))
            val distance = hypot(px - cx, py - cy)
            if (distance > r + EPSILON) {
                CircleLineIntersection.None
            } else if (abs(distance - r) < EPSILON) { // they touch (hold hands >///<)
                CircleLineIntersection.Tangent(Point(px, py))
            } else {
                val pToIntersection = sqrt(r.pow(2) - distance * distance)
                val vx = o2.directionX
                val vy = o2.directionY
                val p = Point(px + vx * pToIntersection, py + vy * pToIntersection)
                val q = Point(px - vx * pToIntersection, py - vy * pToIntersection)
                val s = o2.pointInBetween(p, q) // directed segment p->s->q
                if (o1.hasInsideEpsilon(s))
                    CircleLineIntersection.Double(q, p)
                else
                    CircleLineIntersection.Double(p, q)
            }
        }
        o1 is Circle && o2 is Circle -> {
            val (x1,y1,r1) = o1
            val (x2,y2,r2) = o2
            val r12 = o1.r2
            val r22 = o2.r2
            val dcx = x2 - x1
            val dcy = y2 - y1
            val d2 = dcx*dcx + dcy*dcy
            val d = sqrt(d2) // distance between centers
            if (abs(r1 - r2) > d + EPSILON || d > r1 + r2 + EPSILON) {
                CircleLineIntersection.None
            } else if (
                abs(abs(r1 - r2) - d) < EPSILON || // inner touch "o)" <_<
                abs(d - r1 - r2) < EPSILON // outer touch oo
            ) {
                CircleLineIntersection.Tangent(Point(x1 + dcx / d * r1, y1 + dcy / d * r1))
            } else {
                val dr2 = r12 - r22
                // reference (0->1, 1->2):
                // https://stackoverflow.com/questions/3349125/circle-circle-intersection-points#answer-3349134
                val a = (d2 + dr2)/(2 * d)
                val h = sqrt(r12 - a * a)
                val pcx = x1 + a * dcx / d
                val pcy = y1 + a * dcy / d
                val vx = h * dcx / d
                val vy = h * dcy / d
                val p = Point(pcx + vy, pcy - vx)
                val q = Point(pcx - vy, pcy + vx)
                val s = o1.pointInBetween(p, q) // directed arc p->s->q
                if (o2.hasInsideEpsilon(s))
                    CircleLineIntersection.Double(p, q)
                else
                    CircleLineIntersection.Double(q, p)
            }
        }
        else -> throw IllegalStateException("Never")
    }
}

sealed interface SegmentPoint {
    val point: Point
    data class Vertex(override val point: Point) : SegmentPoint
    data class Interior(override val point: Point) : SegmentPoint
}

fun CircleOrLine.calculateSegmentTopLeft(start: Point, end: Point): SegmentPoint =
    when (this) {
        is Line ->
            SegmentPoint.Vertex(
                if (abs(start.y - end.y) < EPSILON) {
                    if (start.x < end.x) start else end
                } else if (start.y < end.y) start
                else end
            )
        is Circle -> {
            val angle1 = (point2angle(start) + 360f) % 360f // [0; 360)
            val angle2 = (point2angle(end) + 360f) % 360f
            val startAngle = if (isCCW) angle1 else angle2
            var endAngle = if (isCCW) angle2 else angle1
            if (endAngle < startAngle)
                endAngle += 360f // (360; 720)
            val a = (startAngle - 90.0)/360.0
            val b = (endAngle - 90.0)/360.0
            val segmentContainsNorth = ceil(a) <= b
            if (segmentContainsNorth) {
                SegmentPoint.Interior(
                    Point(x, y - radius) // north
                )
            } else {
                SegmentPoint.Vertex(
                    if (abs(start.y - end.y) < EPSILON) {
                        if (start.x < end.x) start else end
                    } else if (start.y < end.y) start
                    else end
                )
            }
        }
    }