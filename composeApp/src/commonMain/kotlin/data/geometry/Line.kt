package data.geometry

import androidx.compose.ui.geometry.Offset
import domain.rotateBy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.hypot

/** [a]*x + [b]*y + [c] = 0
 *
 * Normal vector = ([a], [b]), so it depends on the sign
 *
 * Normal vector points "inside"/to the "left" direction,
 * "inside" = to the "left" side of the line*/
@SerialName("line")
@Serializable
data class Line(
    val a: Double,
    val b: Double,
    val c: Double
) : GCircle, CircleOrLine {

    // direction-preserving
    fun normalized(): Line =
        hypot(a, b).let { norm ->
            Line(a/norm, b/norm, c/norm)
        }

    override fun distanceFrom(point: Offset): Double =
        abs(a*point.x + b*point.y + c)/hypot(a, b)

    /** <0 = inside, 0 on the line, >0 = outside */
    override fun checkPosition(point: Offset): Int =
        -(a*point.x + b*point.y + c).compareTo(0.0)

    override fun translate(vector: Offset): Line =
       Line(a, b, c + (a*vector.x + b*vector.y)/hypot(a, b))

    override fun scale(focus: Offset, zoom: Float): Line {
        // dist1 -> zoom * dist 1
        val newC = zoom*(a*focus.x + b*focus.y + c) - a*focus.x - b*focus.y
        return Line(a, b, newC)
    }

    override fun rotate(focus: Offset, angleDeg: Float): Line {
        val newNormal = (Offset(a.toFloat(), b.toFloat()) - focus).rotateBy(angleDeg) + focus
        val newA = newNormal.x.toDouble()
        val newB = newNormal.y.toDouble()
        val newC = (hypot(newA, newB)/hypot(a, b)) * (a*focus.x + b*focus.y + c) - newA*focus.x - newB*focus.y
        return Line(newA, newB, newC)
    }

    override fun isInside(circle: CircleOrLine): Boolean =
        when (circle) {
            is Circle -> false
            is Line -> {
                val l1 = this.normalized()
                val l2 = circle.normalized()
                l1.a == l2.a && l1.b == l2.b && l1.c <= l2.c // MAYBE: use epsilon eq here
                // || l1.a == -l2.a && l1.b == -l2.b && l1.c <= -l2.c
            }
        }

    override fun isOutside(circle: CircleOrLine): Boolean =
        when (circle) {
            is Circle -> circle.isOutside(this) // beware of cyclic dependencies
            is Line -> {
                val l1 = this.normalized()
                val l2 = circle.normalized()
                l1.a == -l2.a && l1.b == -l2.b && l1.c >= -l2.c // MAYBE: use epsilon eq here
            }
        }

    companion object {
        fun lineBy2Points(p1: Offset, p2: Offset): Line {
            val dy = p2.y.toDouble() - p1.y
            val dx = p2.x.toDouble() - p1.x
            val c = p1.y*dx - p1.x*dy
            return Line(dy, -dx, c)
        }
    }
}