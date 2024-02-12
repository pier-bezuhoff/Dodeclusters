package data

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.minus
import kotlinx.serialization.Serializable
import utils.ComplexField
import utils.r
import utils.r2
import utils.toComplex
import kotlin.math.abs

@Serializable
data class Circle(
    val x: Double,
    val y: Double,
    val radius: Double,
) {
    val offset: Offset
        get() = Offset(x.toFloat(), y.toFloat())

    constructor(center: Offset, radius: Double) :
        this(center.x.toDouble(), center.y.toDouble(), radius)

    constructor(center: Offset, radius: Float) :
        this(center.x.toDouble(), center.y.toDouble(), radius.toDouble())

    /** semiorder ⊆ on circles' insides (⭗) */
    infix fun isInside(otherCircle: Circle): Boolean =
        (offset - otherCircle.offset).getDistance() + radius <= otherCircle.radius

    /** semiorder ⊇ on circles, includes side-by-side (oo) but not encapsulating (⭗) case */
    infix fun isOutside(otherCircle: Circle): Boolean =
        (offset - otherCircle.offset).getDistance() >= otherCircle.radius + radius

    /** -1 = inside, 0 on the circle, +1 = outside */
    fun checkPosition(point: Offset): Int =
        (offset - point).getDistance().compareTo(radius)

    fun hasInside(point: Offset): Boolean =
        checkPosition(point) < 0

    fun hasOutside(point: Offset): Boolean =
        checkPosition(point) > 0

    companion object {
        fun by3Points(p1: Offset, p2: Offset, p3: Offset): Circle {
            // reference: https://math.stackexchange.com/a/3503338
            if (p1 == p2)
                throw NumberFormatException("Not a circle")
            val z1 = p1.toComplex()
            val z2 = p2.toComplex()
            val z3 = p3.toComplex()
            with (ComplexField) {
                val w = (z3 - z1)/(z2 - z1)
                if (abs(w.im) <= 1e-6)
                    throw NumberFormatException("Not a circle")
                val c = (z2 - z1)*(w - w.r2)/(2.0*w.im*i) + z1
                val r = (z1 - c).r
                return Circle(c.re, c.im, r)
            }
        }
    }
}

@Serializable
data class DirectedCircle(
    val x: Double,
    val y: Double,
    val radius: Double,
    /** Circle direction, inside/outside ~ counterclockwise/clockwise */
    val inside: Boolean,
) {
}
