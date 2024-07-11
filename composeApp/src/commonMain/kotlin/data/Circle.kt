package data

import androidx.compose.ui.geometry.Offset
import data.kmath_complex.ComplexField
import data.kmath_complex.r
import data.kmath_complex.r2
import domain.toComplex
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sqrt

const val EPSILON: Double = 1e-6

@Serializable
data class Circle(
    val x: Double,
    val y: Double,
    val radius: Double,
) {
    /** center offset */
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

    fun toCircleF(): CircleF =
        CircleF(x.toFloat(), y.toFloat(), radius.toFloat())

    companion object {
        fun by3Points(p1: Offset, p2: Offset, p3: Offset): Circle {
            // reference: https://math.stackexchange.com/a/3503338
            if (p1 == p2)
                return almostALine(p1, p3)
            else if (p1 == p3 || p2 == p3)
                return almostALine(p1, p2)
            with (ComplexField) {
                val z1 = p1.toComplex()
                val z2 = p2.toComplex()
                val z3 = p3.toComplex()
                val w = (z3 - z1)/(z2 - z1)
                if (abs(w.im) <= EPSILON) // z1, z2, z3 are collinear
                    return almostALine(p1, p2)
                val c = (z2 - z1)*(w - w.r2)/(2.0*w.im*i) + z1
                val r = (z1 - c).r
                return Circle(c.re, c.im, r)
            }
        }

        /** Not really a line but still might be useful;
         * returns a circle thru [p1], [p2] with a very big radius and center to the right of (p2-p1) */
        fun almostALine(p1: Offset, p2: Offset): Circle {
            val veryBigRadius = 100_000.0
            with (ComplexField) {
                val z1 = p1.toComplex()
                val z2 = p2.toComplex()
                val v = z2 - z1
                if (v.r <= EPSILON)
                    throw NumberFormatException("Not a line: 2 line points coincide")
                val center = (z1 + z2)/2 + veryBigRadius*(v/v.r)*i
                return Circle(center.re, center.im, veryBigRadius)
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
//                        d2 += EPSILON // cheat to avoid returning a straight line
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
data class CircleF(
    val x: Float,
    val y: Float,
    val radius: Float,
) {
    @Transient
    val center = Offset(x, y)
}

@Serializable
data class DirectedCircle(
    val x: Double,
    val y: Double,
    val radius: Double,
    /** Circle direction, inside/outside ~ counterclockwise/clockwise */
    val inside: Boolean,
)

// TODO: Clifford algebra
/**
 * Projective-conformal representation of circles/lines/points/imaginary circles via homogenous coordinates.
 *
 * e_0 = (e_minus - e_plus) / 2,
 * e_inf = e_plus + e_minus
 *
 * e_0 = w = 1,
 * x = cx,
 * y = cy,
 * e_inf = z = (cx^2 + cy^2 - r^2) / 2
 * */
@Serializable
data class GeneralizedCircle(
    val w: Double,
    val x: Double,
    val y: Double,
    val z: Double
) {
    init {
        require(listOf(w,x,y,z).any { abs(it) > EPSILON }) { "Homogenous coordinates are invalid" }
    }

    val isLine: Boolean =
        abs(w) < EPSILON
    /** Radius squared */
    val r2: Double =
        if (isLine) Double.POSITIVE_INFINITY
        else (x/w).pow(2) + (y/w).pow(2) - 2*z/w
    val isPoint: Boolean =
        !isLine && abs(r2) < EPSILON
    val isRealCircle: Boolean =
        !isLine && r2 >= EPSILON
    val isImaginaryCircle: Boolean =
        !isLine && r2 <= EPSILON

    /** Upcast a point (x, y) */
    constructor(x: Double, y: Double) : this(
        1.0, x, y, (x.pow(2) + y.pow(2))/2
    )

    constructor(circle: Circle) : this(
        1.0,
        circle.x, circle.y,
        (circle.x.pow(2) + circle.y.pow(2) - circle.radius.pow(2))/2
    )

    fun toCircle(): Circle? =
        if (isRealCircle)
            Circle(x/w, y/w, sqrt(r2))
        else null

    operator fun times(a: Number): GeneralizedCircle {
        val a0 = a.toDouble()
        return GeneralizedCircle(w*a0, x*a0, y*a0, z*a0)
    }

    operator fun plus(other: GeneralizedCircle): GeneralizedCircle =
        GeneralizedCircle(w + other.w, x + other.x, y + other.y, z + other.z)

    operator fun minus(other: GeneralizedCircle): GeneralizedCircle =
        GeneralizedCircle(w - other.w, x - other.x, y - other.y, z - other.z)

    infix fun homogenousEquals(other: GeneralizedCircle): Boolean =
        if (isLine || other.isLine) {
            if (isLine != other.isLine)
                false
            else if (x >= EPSILON)
                abs(x - other.x) < EPSILON &&
                abs(y/x - other.y/other.x) < EPSILON &&  // x == x' implies x' > 0
                abs(z/x - other.z/other.x) < EPSILON
            else if (y >= EPSILON)
                abs(y - other.y) < EPSILON &&
                abs(z/y - other.z/other.y) < EPSILON // y == y' implies y' > 0
            else
                abs(z - other.z) < EPSILON
        } else { // w > 0 && w' > 0
            abs(x/w - other.x/other.w) < EPSILON &&
            abs(y/w - other.y/other.w) < EPSILON &&
            abs(z/w - other.z/other.w) < EPSILON
        }

    fun affineCombination(other: GeneralizedCircle, k: Double): GeneralizedCircle =
        this*k + other*(1 - k)

    fun applyTo(target: GeneralizedCircle, times: Int = 1): GeneralizedCircle {
        require(times > 0)
        return affineCombination(target, times + 1.0)
    }

    /** If [index]=m & [nOfSections]=n, select m-th n-sector among (n-1) possible,
     * counting from [this] circle's side */
    fun bisector(other: GeneralizedCircle, nOfSections: Int = 2, index: Int = 1): GeneralizedCircle {
        require(nOfSections >= 1)
        return affineCombination(other, (nOfSections - index).toDouble()/nOfSections)
    }

    // = affineCombination(other, inf)
    fun altBisector(other: GeneralizedCircle): GeneralizedCircle =
        this - other

    fun calculatePencilType(other: GeneralizedCircle): CirclePencilType? =
        if (this homogenousEquals other)
            null
        else
            TODO()

    companion object {
        /**
         * a*x + b*y + c = 0
         * -> a*e_x + b*e_y + c*e_inf
         */
        fun line(a: Double, b: Double, c: Double): GeneralizedCircle =
            GeneralizedCircle(0.0, a, b, c)

        fun lineBy2Points(p1: Offset, p2: Offset): GeneralizedCircle {
            val dy = p2.y.toDouble() - p1.y
            val dx = p2.x.toDouble() - p1.x
            val c = p1.y*dx - p1.x*dy
            return line(dy, -dx, c)
        }
    }
}

enum class CirclePencilType {
    ELLIPTIC, // lines with 1 common point, circles with 2 common points
    PARABOLIC, // parallel lines, circles tangential to 1 common line at 1 common point
    HYPERBOLIC, // concentric circles, circles perpendicular to every circle of a fixed (dual) elliptic pencil
}