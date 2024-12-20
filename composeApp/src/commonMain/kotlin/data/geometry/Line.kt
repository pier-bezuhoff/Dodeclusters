package data.geometry

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import domain.rotateBy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sign

/** [a]*x + [b]*y + [c] = 0
 *
 * Normal vector = ([a], [b]), so it depends on the sign
 *
 * Normal vector points "inside"/to the "left" direction,
 * "inside" = to the "left" side of the line*/
@SerialName("line")
@Serializable
@Immutable
data class Line(
    val a: Double,
    val b: Double,
    val c: Double
) : GCircle, CircleOrLine {

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
        val (a,b) = this.normalized()
        val (a1,b1) = line.normalized()
        return abs(a*b1 - a1*b) < EPSILON
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

    /** Project Point([x], [y]) down onto this line */
    override fun project(point: Point): Point {
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
        abs(a*point.x + b*point.y + c)/norm

    /** <0 = inside, 0 on the line, >0 = outside */
    override fun checkPosition(point: Offset): Int =
        -(a*point.x + b*point.y + c).compareTo(0.0)

    override fun checkPositionEpsilon(point: Point): Int {
        if (point == Point.CONFORMAL_INFINITY)
            return 0
        val t = (a*point.x + b*point.y + c)/norm
        return if (abs(t) < EPSILON)
            0
        else if (t > 0) // inside
            -1
        else // outside
            +1
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

    override fun translate(vector: Offset): Line =
       Line(a, b, c - (a*vector.x + b*vector.y))

    override fun scale(focus: Offset, zoom: Float): Line {
        // dist1 -> zoom * dist 1
        val newC = zoom*(a*focus.x + b*focus.y + c) - a*focus.x - b*focus.y
        return Line(a, b, newC)
    }

    override fun scale(focusX: Double, focusY: Double, zoom: Double): Line {
        // dist1 -> zoom * dist 1
        val newC = zoom*(a*focusX + b*focusY + c) - a*focusX - b*focusY
        return Line(a, b, newC)
    }

    override fun rotate(focus: Offset, angleDeg: Float): Line {
        val newNormal = normalVector.rotateBy(angleDeg)
        val newA = newNormal.x.toDouble()
        val newB = newNormal.y.toDouble()
        val newC = (hypot(newA, newB)/hypot(a, b)) * (a*focus.x + b*focus.y + c) - newA*focus.x - newB*focus.y
        return Line(newA, newB, newC)
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