package data.geometry

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import data.kmath_complex.ComplexField
import data.kmath_complex.r
import data.kmath_complex.r2
import domain.rotateBy
import domain.toComplex
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

const val EPSILON: Double = 1e-6

@Serializable
@Immutable
sealed interface CircleOrLine : GCircle {
    fun distanceFrom(point: Offset): Double
    /** <0 = inside, 0 on the circle, >0 = outside */
    fun checkPosition(point: Offset): Int
    /** same as [checkPosition] but additionally
     * returns 0 when the distance in (-[EPSILON]; +[EPSILON]) */
    fun checkPositionEpsilon(point: Point): Int
    fun hasInside(point: Offset): Boolean =
        checkPosition(point) < 0
    fun hasOutside(point: Offset): Boolean =
        checkPosition(point) > 0
    /** semiorder ⊆ on circles' insides (⭗) */
    infix fun isInside(circle: CircleOrLine): Boolean
    /** semiorder ⊇ on circles, includes side-by-side (oo) but not encapsulating (⭗) case */
    infix fun isOutside(circle: CircleOrLine): Boolean
    /** sort points on the circle in the order they lie on it (starting from wherever) */
    fun orderPoints(points: Collection<Point>): List<Point>
    fun midArc(p1: Point, p2: Point): Point
    fun translate(vector: Offset): CircleOrLine
    fun scale(focus: Offset, zoom: Float): CircleOrLine
    fun rotate(focus: Offset, angleDeg: Float): CircleOrLine
}

@SerialName("circle")
@Serializable
@Immutable
data class Circle(
    val x: Double,
    val y: Double,
    val radius: Double,
) : GCircle, CircleOrLine {
    val center: Offset get() =
        Offset(x.toFloat(), y.toFloat())

    val r2: Double get() =
        radius * radius

    constructor(center: Offset, radius: Double) :
        this(center.x.toDouble(), center.y.toDouble(), radius)

    constructor(center: Offset, radius: Float) :
        this(center.x.toDouble(), center.y.toDouble(), radius.toDouble())

    override fun distanceFrom(point: Offset): Double =
        abs((point - center).getDistance() - radius)

    /** <0 = inside, 0 on the circle, >0 = outside */
    override fun checkPosition(point: Offset): Int {
        return (center - point).getDistance().compareTo(radius)
    }

    /** <0 = inside, 0 on the circle, >0 = outside */
    override fun checkPositionEpsilon(point: Point): Int {
        val distance = hypot(point.x - x, point.y - y)
        return if (abs(radius - distance) < EPSILON)
            0
        else if (distance < radius)
            -1
        else
            +1
    }

    fun orderPoint(point: Point): Double =
        // NOTE: atan2 uses CCW y-top, x-right coordinates
        //  so we negate y for CCW direction
        atan2(-point.y + y, point.x - x)

    override fun orderPoints(points: Collection<Point>): List<Point> =
        points.sortedBy { orderPoint(it) }

    override fun midArc(p1: Point, p2: Point): Point {
        val phi1 = orderPoint(p1)
        val phi2 = orderPoint(p2)
        val mid =
            if (phi2 > phi1)
                phi1 + (phi2 - phi1)/2.0
            else // includes phi1 == phi2 case
                phi1 + (2*PI - (phi1 - phi2))/2.0
//        val half = (phi2 - phi1).mod(2*PI)/2.0
        return Point(
            x + radius*cos(mid),
            y - radius*sin(mid)
        )
    }

    override fun translate(vector: Offset): Circle =
        Circle(center + vector, radius)

    override fun scale(focus: Offset, zoom: Float): Circle {
        val newOffset = (center - focus) * zoom + focus
        return Circle(newOffset, zoom * radius)
    }

    override fun rotate(focus: Offset, angleDeg: Float): Circle {
        val newOffset = (center - focus).rotateBy(angleDeg) + focus
        return Circle(newOffset, radius)
    }

    override fun isInside(circle: CircleOrLine): Boolean =
        when (circle) {
            is Circle ->
                (center - circle.center).getDistance() + radius <= circle.radius
            is Line ->
                circle.hasInside(center) && circle.distanceFrom(center) >= radius
        }

    override fun isOutside(circle: CircleOrLine): Boolean =
        when (circle) {
            is Circle ->
                (center - circle.center).getDistance() >= circle.radius + radius
            is Line ->
                circle.hasOutside(center) && circle.distanceFrom(center) >= radius
        }

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

        /** Apply the [inverting] circle or line to [theOneBeingInverted]
         *
         * [theOneBeingInverted] transforms accordingly depending to its type:
         *
         * [CircleOrLine] -> [CircleOrLine]
         *
         * [Point] -> [Point]
         *
         * [ImaginaryCircle] -> [ImaginaryCircle] */
        fun invert(inverting: CircleOrLine, theOneBeingInverted: GCircle): GCircle {
            val engine = GeneralizedCircle.fromGCircle(inverting).normalized()
            val target = GeneralizedCircle.fromGCircle(theOneBeingInverted).normalized()
            val result = engine.applyTo(target).normalized()
//            println("\nengine = $engine, norm=${engine.norm}, r2=${engine.r2}; $inverting")
//            println("target = $target, norm=${target.norm}, r2=${target.r2}; $theOneBeingInverted")
//            println("result = $result, norm=${result.norm}, r2=${result.r2}; ${result.toGCircle()}")
            return result.toGCircle()
        }

        fun _invert(inverting: CircleOrLine, theOneBeingInverted: CircleOrLine): CircleOrLine =
            when (inverting) {
                is Line -> when (theOneBeingInverted) {
                    is Line -> {
//                        val n0 = inverting.normalVector
//                        val n1 = theOneBeingInverted.normalVector
//                        val n2 = n1 - n0 * (2 * (n0.x*n1.x + n0.y*n1.y))
                        theOneBeingInverted
                    }
                    is Circle -> {
                        val c = theOneBeingInverted.center
                        val centerProjection = inverting.project(c)
                        val newCenter = centerProjection + (centerProjection - c)
                        Circle(newCenter, theOneBeingInverted.radius)
                    }
                }
                is Circle -> when (theOneBeingInverted) {
                    is Line -> {
                        theOneBeingInverted
                    }
                    is Circle -> {
                        val (x, y, r) = inverting
                        val (x0, y0, r0) = theOneBeingInverted
                        when {
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
                                Circle(newX, newY, newRadius)
                            }
                        }
                    }
                }
            }

        fun calculateIntersectionPoints(
            circle1: CircleOrLine, circle2: CircleOrLine
        ): List<Point> =
            when {
                // MAYBE: dont use Offsets and Floats for better precision
                circle1 is Line && circle2 is Line -> {
                    val (a1, b1, c1) = circle1
                    val (a2, b2, c2) = circle2
                    val n1 = circle1.normalVector
                    val n2 = circle2.normalVector
                    val vectorProduct = n1.x*n2.y - n2.x*n1.y
                    if (abs(vectorProduct) < EPSILON) { // parallel condition
                        listOf(Point.CONFORMAL_INFINITY)
                    } else {
                        val wx = b1*c2 - b2*c1 // det in homogenous coordinates
                        val wy = a2*c1 - a1*c2
                        val w = a1*b2 - a2*b1 // we know that w != 0 (non-parallel)
                        listOf(Point.CONFORMAL_INFINITY, Point(wx/w, wy/w))
                    }
                }
                circle1 is Line && circle2 is Circle -> {
                    val c = circle2.center
                    val r = circle2.radius
                    val p = circle1.project(c)
                    val distance = (p - c).getDistance()
                    if (distance > r + EPSILON) {
                        emptyList()
                    } else if (abs(distance - r) < EPSILON) { // they touch
                        listOf(Point.fromOffset(p))
                    } else {
                        val pToIntersection = sqrt(r.toFloat().pow(2) - distance*distance)
                        val v = circle1.directionVector
                        listOf(
                            Point.fromOffset(p + v*pToIntersection),
                            Point.fromOffset(p - v*pToIntersection),
                        )
                    }
                }
                circle1 is Circle && circle2 is Line ->
                    calculateIntersectionPoints(circle2, circle1) // ^^^
                circle1 is Circle && circle2 is Circle -> {
                    val (x1,y1,r1) = circle1
                    val (x2,y2,r2) = circle2
                    val r12 = circle1.r2
                    val r22 = circle2.r2
                    val dcx = x2 - x1
                    val dcy = y2 - y1
                    val d2 = dcx*dcx + dcy*dcy
                    val d = sqrt(d2) // distance between centers
                    if (abs(r1 - r2) > d + EPSILON || d > r1 + r2 + EPSILON) {
                        emptyList()
                    } else if (
                        abs(abs(r1 - r2) - d) < EPSILON || // inner touch
                        abs(d - r1 - r2) < EPSILON // outer touch
                    ) {
                        listOf(Point(x1 + dcx/d*r1, y1 + dcy/d*r1))
                    } else {
                        val dr2 = r12 - r22
                        // reference (0->1, 1->2):
                        // https://stackoverflow.com/questions/3349125/circle-circle-intersection-points#answer-3349134
                        val a = (d2 + dr2)/(2 * d)
                        val h = sqrt(r12 - a*a)
                        val pcx = x1 + a * dcx / d
                        val pcy = y1 + a * dcy / d
                        val vx = h * dcx / d
                        val vy = h * dcy / d
                        // Q: does the point order depend on the input circle order?
                        listOf(
                            Point(pcx + vy, pcy - vx),
                            Point(pcx - vy, pcy + vx)
                        )
                    }
                }
                else -> throw IllegalStateException("Never")
            }
    }
}

// idk if i'll ever use it, technically it is also represented by GeneralizedCircle
@Serializable
data class DirectedCircle(
    val x: Double,
    val y: Double,
    val radius: Double,
    /** Circle direction, inside/outside ~ counterclockwise/clockwise */
    val inside: Boolean,
)
