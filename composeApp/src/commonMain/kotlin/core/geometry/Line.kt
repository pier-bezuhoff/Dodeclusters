@file:Suppress("NOTHING_TO_INLINE")

package core.geometry

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import domain.radians
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sign
import kotlin.math.sin

// MAYBE: 2-point constructor instead of a,b,c for smoother incident point transformation
/** [a]*x + [b]*y + [c] = 0
 *
 * NOTE: must be [normalized] `a^2 + b^2 == 1` before most operations
 *
 * Normal vector = ([a], [b]), so it depends on the sign
 *
 * Normal vector points "inside"/to the "left" direction,
 * "inside" = to the "left" side of the line*/
@Immutable
@Serializable
@SerialName("line")
data class Line(
    val a: Double,
    val b: Double,
    val c: Double,
    // MAYBE: fixedPoint1: Point = default1(a,b,c)
    //  fixedPoint2: Point = default2(a,b,c) for consistent locus ordering
) : CircleOrLine, LineOrPoint {

    init {
        require(
            a.isFinite() && b.isFinite() && c.isFinite() &&
            (a != 0.0 || b != 0.0)
        ) { "Invalid Line($a, $b, $c)" }
    }

    inline val normalX: Double get() =
        a

    inline val normalY: Double get() =
        b

    /** length=1 normal vector, to the left of the direction vector */
    val normalVector: Offset get() =
        Offset(normalX.toFloat(), normalY.toFloat())

    inline val directionX: Double get() =
        -b

    inline val directionY: Double get() =
        a

    /** length=1 direction vector */
    val directionVector: Offset get() =
        Offset(-b.toFloat(), a.toFloat())

    /** Direction-preserving, ensures that `a^2 + b^2 == 1` */
    fun normalized(): Line {
        val norm = hypot(a, b)
        return Line(a / norm, b / norm, c / norm)
    }

    /** First non-zero coordinate is guaranteed to be positive and
     * `a^2 + b^2 == 1` */
    fun normalizedNoDirection(): Line {
        val norm = hypot(a, b)
        val sign =
            if (a == 0.0) sign(b) // b != 0
            else sign(a)
        return Line(sign*a / norm, sign*b / norm, sign*c / norm)
    }

    infix fun isCollinearTo(line: Line): Boolean {
        val crossProduct = this.a*line.b - this.b*line.a
        return abs(crossProduct) < EPSILON
    }

    /** [point] to line vector, normalized */
    fun towardsVectorFrom(point: Offset) =
        if (point liesInside this)
            -normalVector
        else
            normalVector

    // reference: https://en.wikipedia.org/wiki/Distance_from_a_point_to_a_line#Line_defined_by_an_equation
    /** Project [point] down onto this line */
    fun project(point: Offset): Offset {
        val t = b*point.x - a*point.y
        return Offset(
            (b*t - a*c).toFloat(),
            (-a*t - b*c).toFloat()
        )
    }

    /** Project Point(x, y) down onto this line */
    override fun project(point: Point): Point {
        if (point.isInfinite)
            return point
        val (x, y) = point
        val t = b*x - a*y
        return Point(
            b*t - a*c,
            -a*t - b*c
        )
    }

    inline fun signedDistanceFrom(x: Double, y: Double): Double =
        a*x + b*y + c

    inline fun signedDistanceFrom(x: Float, y: Float): Double =
        a*x + b*y + c

    inline fun signedDistanceFrom(point: Point): Double =
        a*point.x + b*point.y + c

    inline fun signedDistanceFrom(point: Offset): Double =
        a*point.x + b*point.y + c

    override fun distanceFrom(point: Offset): Double =
        abs(a*point.x + b*point.y + c)

    override fun distanceFrom(x: Double, y: Double): Double =
        abs(a*x + b*y + c)

    override fun distanceFrom(point: Point): Double =
        if (point.isInfinite) 0.0
        else abs(a*point.x + b*point.y + c)

    override fun getPointLocation(point: Offset): Region.PointLocation {
        val sd = signedDistanceFrom(point)
        return when {
            sd > 0 -> Region.PointLocation.INSIDE
            sd < 0 -> Region.PointLocation.OUTSIDE
            else -> Region.PointLocation.BORDERING
        }
    }

    override fun getPointLocation(point: Point): Region.PointLocation {
        if (point.isInfinite)
            return Region.PointLocation.BORDERING
        val sd = signedDistanceFrom(point)
        return when {
            abs(sd) < EPSILON -> Region.PointLocation.BORDERING
            sd > 0 -> Region.PointLocation.INSIDE
            else -> Region.PointLocation.OUTSIDE
        }
    }

    // conf_inf < projection along line direction; order 0 = project(0,0)
    override fun point2order(point: Point): Double {
        return if (point.isInfinite)
            ORDER_OF_CONFORMAL_INFINITY
        else
            point.x*directionX + point.y*directionY
    }

    override fun order2point(order: Double): Point {
        if (order.isInfinite())
            return Point.CONFORMAL_INFINITY
//        val (p0x, p0y) = project(Point(0.0, 0.0))
        val k = -c
        val p0x = k*a
        val p0y = k*b
        return Point(
            p0x + directionX*order,
            p0y + directionY*order
        )
    }

    override fun orderInBetween(order1: Double, order2: Double): Double =
        Line.orderInBetween(order1, order2)

    override fun agreesWithOrientation(startOrder: Double, middleOrder: Double, endOrder: Double): Boolean =
        orderIsInBetween(startOrder, middleOrder, endOrder)

    override fun translated(vector: Offset): Line =
       copy(c = c - (a*vector.x + b*vector.y))

    override fun translated(dx: Double, dy: Double): Line =
        copy(c = c - (a*dx + b*dy))

    fun translatedTo(point: Point): Line =
        copy(c = -a*point.x - b*point.y)

    override fun scaled(focus: Offset, zoom: Float): Line {
        // dist1 -> zoom * dist 1
        val newC = zoom * signedDistanceFrom(focus) - a*focus.x - b*focus.y
        return copy(c = newC)
    }

    // scaling doesn't scale incident points
    // MAYBE: scale a&b too and for incidents use vector (a, b) instead of normal with length=1?
    override fun scaled(focusX: Double, focusY: Double, zoom: Double): Line {
        // dist1 -> zoom * dist 1
        val newC = zoom * signedDistanceFrom(focusX, focusY) - a*focusX - b*focusY
        return copy(c = newC)
    }

    override fun rotated(focusX: Double, focusY: Double, angleInRadians: Double): Line {
        val cosine = cos(angleInRadians)
        val sine = sin(angleInRadians)
        val newA = normalX * cosine - normalY * sine
        val newB = normalX * sine + normalY * cosine
        val newC = signedDistanceFrom(focusX, focusY) - newA*focusX - newB*focusY
        return Line(newA, newB, newC)
    }

    override fun rotated(focus: Offset, angleInDegrees: Float): Line {
        val angle = angleInDegrees.radians
        val cosine = cos(angle)
        val sine = sin(angle)
        val newA = normalX * cosine - normalY * sine
        val newB = normalX * sine + normalY * cosine
        val newC = signedDistanceFrom(focus) - newA*focus.x - newB*focus.y
        return Line(newA, newB, newC)
    }

    override fun transformed(
        translation: Offset,
        focus: Offset,
        zoom: Float,
        rotationAngle: Float,
    ): Line {
        val (focusX, focusY) =
            if (focus == Offset.Unspecified)
                order2point(0.0).toOffset()
            else focus
        var c1: Double = c
        c1 -= a*translation.x + b*translation.y
        c1 = zoom * signedDistanceFrom(focusX, focusY) // - a*focusX - b*focusY // added back when rotating
        val angle: Double = rotationAngle.radians
        val cosine = cos(angle)
        val sine = sin(angle)
        val a1 = normalX * cosine - normalY * sine
        val b1 = normalX * sine + normalY * cosine
        c1 = c1 - a1*focusX - b1*focusY
        return Line(a1, b1, c1)
    }

    override fun reversed(): Line =
        copy(a = -a, b = -b, c = -c)

    override fun getRegionLocation(region: Region): Region.RegionLocation =
        when (region) {
            is Circle -> {
                val far = distanceFrom(region.x, region.y) > region.radius
                if (!far) {
                    Region.RegionLocation.OVERLAPS
                } else if (region.isCCW) { // far CCW
                    if (region.center liesInside this) // "↺ ↑" case
                        Region.RegionLocation.CONTAINS_INSIDE
                    else  // "↑ ↺" case
                        Region.RegionLocation.NO_INTERSECTION
                } else { // far CW
                    if (region.center liesInside this) // "↻ ↑" case
                        Region.RegionLocation.OVERLAPS
                    else // "↑ ↻" case
                        Region.RegionLocation.IS_CONTAINED_INSIDE
                }
            }
            is Line -> {
                val (a1, b1, c1) = this.normalized()
                val (a2, b2, c2) = region.normalized()
                if (abs(a1 - a2) < EPSILON && abs(b1 - b2) < EPSILON) {
                    if (c1 <= c2)
                        Region.RegionLocation.CONTAINS_INSIDE
                    else
                        Region.RegionLocation.IS_CONTAINED_INSIDE
                    // NOTE: anti-parallel line (l' == -l) cannot define a half-plane that is fully inside
                } else if (abs(a1 + a2) < EPSILON && abs(b1 + b2) < EPSILON && c1 <= c2) {
                    Region.RegionLocation.NO_INTERSECTION
                } else {
                    Region.RegionLocation.OVERLAPS
                }
            }
            is ConcreteArcPath -> {
                if (region intersects this)
                    Region.RegionLocation.OVERLAPS
                else if (region.vertices.firstOrNull()?.liesInside(this) == true)
                    Region.RegionLocation.CONTAINS_INSIDE
                else
                    Region.RegionLocation.NO_INTERSECTION
            }
        }

    override fun tangentAt(point: Point): Line =
        this

    override fun calculateIntersectionPoints(other: Intersectable): List<Point> =
        when (other) {
            is CircleOrLine -> Circle.calculateIntersectionPoints(this, other)
            else -> other.calculateIntersectionPoints(this)
        }

    companion object {
        /** `Line.point2order(Point.CONFORMAL_INFINITY)` */
        const val ORDER_OF_CONFORMAL_INFINITY = Double.NEGATIVE_INFINITY

        fun by2Points(p1: Offset, p2: Offset): Line {
            val dx = p2.x.toDouble() - p1.x
            val dy = p2.y.toDouble() - p1.y
            val c = p1.x*dy - p1.y*dx
            return Line(-dy, dx, c).normalized()
        }

        fun by2Points(p1: Point, p2: Point): Line {
            val dx = p2.x - p1.x
            val dy = p2.y - p1.y
            val c = p1.x*dy - p1.y*dx
            return Line(-dy, dx, c).normalized()
        }

        fun orderIsInBetween(startOrder: Double, order: Double, endOrder: Double): Boolean {
            return if (startOrder <= endOrder)
                order in startOrder..endOrder
            else order <= endOrder || startOrder <= order // segment contains infinity
        }

        fun orderInBetween(order1: Double, order2: Double): Double {
            val order1IsFinite = order1.isFinite()
            val order2IsFinite = order2.isFinite()
            return if (order1IsFinite) {
                if (order2IsFinite) {
                    if (order2 > order1)
                        order1 + (order2 - order1) / 2.0
                    else if (order2 == order1)
                        order1 + 50.0 // idk, w/e
                    else // order2 < order1
                        order1 - (order1 - order2) / 2.0
                } else { // finite & infinite
                    order1 + 50.0
                }
            } else if (order2IsFinite) { // infinite & finite
                order2 - 50.0
            } else { // infinite & infinite
                0.0
            }
        }

        fun coerceOrder(order: Double, startOrder: Double, endOrder: Double): Double {
            return if (startOrder <= endOrder)
                order.coerceIn(startOrder, endOrder)
            else // segment contains infinity
                if (order <= endOrder || startOrder <= order)
                    order
                else // endOrder < order < startOrder
                    if (order - endOrder < startOrder - order)
                        endOrder
                    else
                        startOrder
        }

        fun projectPointOntoSegment(point: Point, start: Point, end: Point): Point {
            val l2 = start.distance2From(end)
            val dx = end.x - start.x
            val dy = end.y - start.y
            val dotProduct = (point.x - start.x)*dx + (point.y - start.y)*dy
            val t = (dotProduct/l2).coerceIn(0.0, 1.0)
            return Point(
                x = start.x + t*dx,
                y = start.y + t*dy
            )
        }

        fun projectPointOntoLineBy2Points(point: Point, start: Point, end: Point): Point {
            val l2 = start.distance2From(end)
            val dx = end.x - start.x
            val dy = end.y - start.y
            val scalar = (point.x - start.x)*dx + (point.y - start.y)*dy
            val t = scalar/l2
            return Point(
                x = start.x + t*dx,
                y = start.y + t*dy
            )
        }

        fun pointIsInBetweenUndirected(p1: Point, p2: Point, p3: Point): Boolean {
            val ax = p2.x - p1.x
            val ay = p2.y - p1.y
            val bx = p2.x - p3.x
            val by = p2.y - p3.y
            return ax*bx + ay*by <= 0.0
        }
    }
}