package data.geometry

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import domain.rotateBy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sign
import kotlin.math.sin

// NOTE: ggbra lines are defined by 2 points, maybe its better for incidence?
/** [a]*x + [b]*y + [c] = 0
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
    val c: Double
) : CircleOrLine {

    init {
        require(
            a.isFinite() && b.isFinite() && c.isFinite()
        ) { "Invalid Line($a, $b, $c)" }
    }

    @Transient
    val norm: Double =
        hypot(a, b)

    /** length=1 normal vector, to the left of the direction vector */
    val normalVector: Offset get() =
        Offset((a/norm).toFloat(), (b/norm).toFloat())

    /** length=1 direction vector */
    val directionVector: Offset get() =
        Offset((b/norm).toFloat(), (-a/norm).toFloat())

    val directionX: Double get() =
        b/norm

    val directionY: Double get() =
        -a/norm

    /** Direction-preserving, ensures that `hypot(a, b) == 1` */
    fun normalized(): Line =
        Line(a/norm, b/norm, c/norm)

    /** First non-zero coordinate is guaranteed to be positive and
     * `hypot(a, b) == 1` */
    fun normalizedNoDirection(): Line {
        val sign =
            if (a == 0.0) sign(b) // b != 0
            else sign(a)
        return Line(sign*a / norm, sign*b / norm, sign*c / norm)
    }

    infix fun isCollinearTo(line: Line): Boolean {
        val crossProduct = (this.a*line.b - this.b*line.a) / this.norm / line.norm
        return abs(crossProduct) < EPSILON
    }

    /** Project [point] down onto this line */
    // reference: https://en.wikipedia.org/wiki/Distance_from_a_point_to_a_line#Line_defined_by_an_equation
    fun project(point: Offset): Offset {
        val t = b*point.x - a*point.y
        val n2 = a*a + b*b
        return Offset(
            ((b*t - a*c)/n2).toFloat(),
            ((-a*t - b*c)/n2).toFloat()
        )
    }

    /** Project Point(x, y) down onto this line */
    override fun project(point: Point): Point {
        if (point == Point.CONFORMAL_INFINITY)
            return point
        val (x, y) = point
        val t = b*x - a*y
        val n2 = a*a + b*b
        return Point(
            (b*t - a*c)/n2,
            (-a*t - b*c)/n2
        )
    }

    override fun distanceFrom(point: Offset): Double =
        abs(a*point.x + b*point.y + c)/norm

    override fun distanceFrom(point: Point): Double =
        if (point == Point.CONFORMAL_INFINITY) 0.0
        else abs(a*point.x + b*point.y + c)/norm

    override fun calculateLocation(point: Offset): RegionPointLocation {
        val m = -(a * point.x + b * point.y + c)
        return if (m < 0) RegionPointLocation.IN
        else if (m > 0) RegionPointLocation.OUT
        else RegionPointLocation.BORDERING
    }

    override fun calculateLocationEpsilon(point: Point): RegionPointLocation {
        if (point == Point.CONFORMAL_INFINITY)
            return RegionPointLocation.BORDERING
        val t = (a*point.x + b*point.y + c)/norm
        return if (abs(t) < EPSILON)
            RegionPointLocation.BORDERING
        else if (t > 0) // inside
            RegionPointLocation.IN
        else // outside
            RegionPointLocation.OUT
    }

    // conf_inf < projection along line direction; order 0 = project(0,0)
    override fun point2order(point: Point): Double {
        return if (point == Point.CONFORMAL_INFINITY)
            Double.NEGATIVE_INFINITY
        else
            point.x*directionX + point.y*directionY
    }

    override fun order2point(order: Double): Point {
        if (order == Double.NEGATIVE_INFINITY)
            Point.CONFORMAL_INFINITY
        val p0 = project(Point(0.0, 0.0))
        return Point(
            p0.x + directionX*order,
            p0.y + directionY*order
        )
    }

    override fun orderInBetween(order1: Double, order2: Double): Double =
        if (order1 == Double.NEGATIVE_INFINITY && order2 == Double.NEGATIVE_INFINITY)
            0.0
        else if (order1 == Double.NEGATIVE_INFINITY)
            order2 - 50.0
        else if (order2 == Double.NEGATIVE_INFINITY)
            order1 + 50.0
        else if (order2 > order1)
            order1 + (order2 - order1)/2.0
        else if (order2 == order1)
            order1 + 50.0 // idk, w/e
        else // order2 < order1
            order1 - (order1 - order2)/2.0

    override fun orderIsInBetween(startOrder: Double, order: Double, endOrder: Double): Boolean {
        return if (startOrder <= endOrder)
            order in startOrder..endOrder
        else order <= endOrder || startOrder <= order // segment contains infinity
    }

    override fun translated(vector: Offset): Line =
       Line(a, b, c - (a*vector.x + b*vector.y))

    fun translated(dx: Double, dy: Double): Line =
        Line(a, b, c - (a*dx + b*dy))

    fun translatedTo(point: Point): Line =
        Line(a, b, -a*point.x - b*point.y)

    override fun scaled(focus: Offset, zoom: Float): Line {
        // dist1 -> zoom * dist 1
        val newC = zoom*(a*focus.x + b*focus.y + c) - a*focus.x - b*focus.y
        return Line(a, b, newC)
    }

    // scaling doesn't scale incident points
    // MAYBE: scale a&b too and for incidents use vector (a, b) instead of normal with length=1?
    override fun scaled(focusX: Double, focusY: Double, zoom: Double): Line {
        // dist1 -> zoom * dist 1
        val newC = zoom*(a*focusX + b*focusY + c) - a*focusX - b*focusY
        return Line(a, b, newC)
    }

    override fun rotated(focus: Offset, angleInDegrees: Float): Line {
        val newNormal = normalVector.rotateBy(angleInDegrees)
        val newA = newNormal.x.toDouble()
        val newB = newNormal.y.toDouble()
        val newC = (hypot(newA, newB)/hypot(a, b)) * (a*focus.x + b*focus.y + c) - newA*focus.x - newB*focus.y
        return Line(newA, newB, newC)
    }

    override fun transformed(translation: Offset, focus: Offset, zoom: Float, rotationAngle: Float): Line {
        val (focusX, focusY) =
            if (focus == Offset.Unspecified) order2point(0.0).toOffset()
            else focus
        var c1: Double = c
        c1 -= a*translation.x + b*translation.y
        c1 = zoom*(a*focusX + b*focusY + c1) // - a*focusX - b*focusY // added back when rotating
        val phi: Double = rotationAngle * PI/180.0
        val cosPhi = cos(phi)
        val sinPhi = sin(phi)
        val a1 = a * cosPhi - b * sinPhi
        val b1 = a * sinPhi + b * cosPhi
        c1 = (hypot(a1, b1)/hypot(a, b)) * c1 - a1*focusX - b1*focusY
        return Line(a1, b1, c1)
    }

    override fun reversed(): Line =
        copy(a = -a, b = -b, c = -c)

    override fun isInside(circle: CircleOrLine): Boolean =
        when (circle) {
            is Circle ->
                if (circle.isCCW)
                    false
                else
                    circle.copy(isCCW = true).isOutside(this)
            is Line -> {
                val l1 = this.normalized()
                val l2 = circle.normalized()
                l1.a == l2.a && l1.b == l2.b && l1.c <= l2.c // MAYBE: use epsilon eq here
                // NOTE: anti-parallel line (l' == -l) cannot define a half-plane that is fully inside
            }
        }

    override fun isOutside(circle: CircleOrLine): Boolean =
        when (circle) {
            is Circle ->
                if (circle.isCCW)
                    circle.isOutside(this) // beware of cyclic dependencies
                else
                    false
            is Line -> {
                val l1 = this.normalized()
                val l2 = circle.normalized()
                l1.a == -l2.a && l1.b == -l2.b && l1.c <= -l2.c // MAYBE: use epsilon eq here
            }
        }

    override fun tangentAt(point: Point): Line =
        this

    companion object {
        fun by2Points(p1: Offset, p2: Offset): Line {
            val dy = p2.y.toDouble() - p1.y
            val dx = p2.x.toDouble() - p1.x
            val c = p1.y*dx - p1.x*dy
            return Line(dy, -dx, c).normalized()
        }

        fun by2Points(p1: Point, p2: Point): Line {
            val dy = p2.y - p1.y
            val dx = p2.x - p1.x
            val c = p1.y*dx - p1.x*dy
            return Line(dy, -dx, c).normalized()
        }
    }
}