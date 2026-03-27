package core.geometry

import domain.squareSum
import kotlin.math.abs
import kotlin.math.sign

// circle or line with fixed points
data class PointTriplet(
    val point1: Point,
    val point2: Point,
    val point3: Point,
) {
    val isCircle get() =
        point1.isFinite && point2.isFinite && point3.isFinite &&
        point1 != point2 && point2 != point3 && point3 != point1

    val isLine get() = when {
        point1.isInfinite -> point2.isFinite && point3.isFinite && point2 != point3
        point2.isInfinite -> point1.isFinite && point3.isFinite && point1 != point3
        point3.isInfinite -> point1.isFinite && point2.isFinite && point1 != point2
        else -> false
    }

    /** Assumes [point] lies on self and calculates Re(4-point cross-ratio) */
    fun point2order(point: Point): Double =
        crossRatio(point1, point2, point3, point)

    fun order2point(order: Double): Point {
        // solve cross-ratio equation for complex points
        val (a, b) = point1 // A
        val (c, d) = point2 // B
        val k = point1.x - point3.x // A - C
        val l = point1.y - point3.y
        val m = point2.x - point3.x // B - C
        val n = point2.y - point3.y
        val p = order*m - k
        val q = l - n
        val denominator = squareSum(p, q)
        if (abs(denominator) < EPSILON2)
            return Point.CONFORMAL_INFINITY
        val u = order*(a*m - b*n) - c*k + d*l
        val v = order*(a*n + b*m) - c*l - d*k
        val kx = p*u - q*v
        val ky = p*v + q*u
        if (kx.isInfinite() || ky.isInfinite())
            return Point.CONFORMAL_INFINITY
        // MAYBE: handle inf/inf and 0/0
        val x = kx/denominator
        val y = ky/denominator
        return Point(x, y)
    }
}

/** Real part of complex cross-ratio; the cross-ratio is purely real if all 4 points
 * lie on a circle/line */
private fun crossRatio(
    p1: Point,
    p2: Point,
    p3: Point,
    p4: Point,
): Double {
    // Re[ (z1-z3)*(z2-z4) / ((z2-z3)*(z1-z4)) ]
    val a = p1.x - p3.x
    val b = p1.y - p3.y
    val c = p2.x - p4.x
    val d = p2.y - p4.y
    val e = p2.x - p3.x
    val f = p2.y - p3.y
    val g = p1.x - p4.x
    val h = p1.y - p4.y
    val m = a*c - b*d
    val n = a*d + b*c
    val u = e*g - f*h
    val v = e*h + f*g
    val numerator = m*u + n*v
    val denominator = squareSum(e, f)*squareSum(g, h)
    return if (denominator.isInfinite()) {
        if (numerator.isInfinite())
            sign(numerator)*sign(denominator) // idk
        else // x/0 = inf, x/inf = 0 btw
            numerator/denominator
    } else {
        if (abs(denominator) < EPSILON2 && abs(numerator) < EPSILON2)
            sign(numerator)*sign(denominator) // 0/0 => 0
        else
            numerator/denominator
    }
    // Im = (n*u - m*v)/denominator
    // Im > 0 => p4 is inside p1-3 circle
    // Im < 0 => outside
}