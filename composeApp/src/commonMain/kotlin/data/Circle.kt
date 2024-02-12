package data

import androidx.compose.ui.geometry.Offset
import kotlinx.serialization.Serializable
import utils.ComplexField
import utils.r
import utils.r2
import utils.toComplex
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

@Serializable
data class Circle(
    val x: Double,
    val y: Double,
    val radius: Double,
) {
    val offset: Offset
        get() = Offset(x.toFloat(), y.toFloat())

    val r2: Double
        get() = radius * radius

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

        fun invert(inverting: Circle, theOneBeingInverted: Circle): Circle {
            val (x, y, r) = inverting
            val (x0, y0, r0) = theOneBeingInverted
            return when {
                r == Double.POSITIVE_INFINITY -> // return a line somehow
                    throw NumberFormatException("Not a circle")
                x == x0 && y == y0 ->
                    Circle(x, y, r*r / r0)
                else -> {
                    val dx = x0 - x
                    val dy = y0 - y
                    val d = hypot(dx, dy)
                    if (d == r0) // inverting.center ∈ theOneBeingInverted
                        throw NumberFormatException("Not a circle")
//                        d2 += 1e-6 // cheat to avoid returning a straight line
                    val ratio = r*r / (d*d - r0*r0)
                    val newX = x + ratio * dx
                    val newY = y + ratio * dy
                    // NOTE: sign(ratio) = sign(d - r0)
                    //  ratio > 0 => orientation changes, ratio < 0 orientation stays the same
                    //  ratio == 0 => line
                    val newRadius = abs(ratio) * r0
                    return Circle(newX, newY, newRadius)
                }
            }
        }

        fun findIntersectionPoints(circle0: Circle, circle: Circle): Pair<Pair<Double, Double>, Pair<Double, Double>>? {
            val (x0,y0,r0) = circle0
            val (x,y,r) = circle
            val r12 = circle0.r2
            val r22 = circle.r2
            val dcx = x - x0
            val dcy = y - y0
            val d2 = dcx*dcx + dcy*dcy
            val d = sqrt(d2)
            if (abs(r0 - r) > d || d > r0 + r)
                return null
            val dr2 = r12 - r22
            // reference (0=this, 1=circle):
            // https://stackoverflow.com/questions/3349125/circle-circle-intersection-points#answer-3349134
            val a = (d2 + dr2)/(2 * d)
            val h = sqrt(r12 - a*a)
            val pcx = x0 + a * dcx / d
            val pcy = y0 + a * dcy / d
            val vx = h * dcx / d
            val vy = h * dcy / d
            // Q: does point order depend on input circle order?
            return Pair(
                Pair(pcx + vy, pcy - vx),
                Pair(pcx - vy, pcy + vx)
            )
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
