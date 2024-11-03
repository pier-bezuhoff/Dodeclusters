package data.geometry

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import data.kmath_complex.ComplexField
import data.kmath_complex.r
import data.kmath_complex.r2
import domain.TAU
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
const val EPSILON2: Double = EPSILON*EPSILON

/** Circle with center ([x], [y]) and [radius]
 * @param[isCCW] Counterclockwise or clockwise direction (_in_ vs _out_)
 * */
@SerialName("circle")
@Serializable
@Immutable
data class Circle(
    override val x: Double,
    override val y: Double,
    override val radius: Double,
    /** Counterclockwise or clockwise direction
     *
     * _internal orientation_ of CCW direction by convention is associated with
     * _external orientation_ of the circle's _inside_, and CW with _outside_
     *
     * note: odd number of inversions/reflections desyncs internal and external orientations
     * */
    val isCCW: Boolean = true,
) : UndirectedCircle {
    val center: Offset get() =
        Offset(x.toFloat(), y.toFloat())

    val centerPoint: Point get() =
        Point(x, y)

    val r2: Double get() =
        radius * radius

    init {
        require(radius > 0.0) // points and imaginary circle should not be mixed in
    }

    constructor(center: Offset, radius: Double, isCCW: Boolean = true) :
        this(center.x.toDouble(), center.y.toDouble(), radius, isCCW)

    constructor(center: Offset, radius: Float, isCCW: Boolean = true) :
        this(center.x.toDouble(), center.y.toDouble(), radius.toDouble(), isCCW)

    override fun project(point: Point): Point {
        val (x1, y1) = point
        if (x == x1 && y == y1 || point == Point.CONFORMAL_INFINITY) {
            println("WARNING: bad projection")
            return order2point(0.0)
        }
        val vx = x1 - x
        val vy = y1 - y
        val vLength = hypot(vx, vy)
        return Point(
            x + (vx/vLength)*radius,
            y + (vy/vLength)*radius
        )
    }

    override fun distanceFrom(point: Offset): Double =
        abs((point - center).getDistance() - radius)

    override fun distanceFrom(point: Point): Double =
        abs(hypot(point.x - x, point.y - y) - radius)

    fun distanceBetweenCenters(circle: Circle): Double =
        hypot(x - circle.x, y - circle.y)

    /** -1 = inside, 0 on the circle, +1 = outside */
    override fun checkPosition(point: Offset): Int {
        val distance = (center - point).getDistance()
        val r = radius.toFloat()
        return when {
            distance < r -> if (isCCW) -1 else +1
            distance == r -> 0
            distance > r -> if (isCCW) +1 else -1
            else -> throw IllegalStateException("Illegal comparison")
        }
    }

    /** -1 = inside, 0 on the circle, +1 = outside */
    override fun checkPositionEpsilon(point: Point): Int {
        if (point == Point.CONFORMAL_INFINITY)
            return if (isCCW) +1 else -1
        val distance = hypot(point.x - x, point.y - y)
        return if (abs(radius - distance) < EPSILON)
            0
        else if (distance < radius)
            if (isCCW) -1 else +1
        else // outside
            if (isCCW) +1 else -1
    }

    /** CCW order starting from the East: ENWS */
    override fun point2order(point: Point): Double {
        // NOTE: atan2 uses CCW y-top, x-right coordinates
        //  so we negate y for CCW direction
        val order = atan2(-point.y + y, point.x - x)
        return if (isCCW) order else -order
    }

    override fun order2point(order: Double): Point {
        val o = if (isCCW) order else -order
        return Point(
            x + radius * cos(o),
            y - radius * sin(o)
        )
    }

    override fun orderInBetween(order1: Double, order2: Double): Double =
        if (order2 > order1)
            order1 + (order2 - order1)/2.0
        else // includes order1 == order2 case
            order1 + (2*PI - (order1 - order2))/2.0
//        val half = (order2 - order1).mod(2*PI)/2.0

    override fun translate(vector: Offset): Circle =
        Circle(center + vector, radius, isCCW)

    override fun scale(focus: Offset, zoom: Float): Circle {
        val newOffset = (center - focus) * zoom + focus
        return Circle(newOffset, zoom * radius, isCCW)
    }

    override fun scale(focusX: Double, focusY: Double, zoom: Double): Circle {
        val newX = (x - focusX) * zoom + focusX
        val newY = (y - focusY) * zoom + focusY
        return Circle(newX, newY, zoom * radius, isCCW)
    }

    override fun rotate(focus: Offset, angleDeg: Float): Circle {
        val newOffset = (center - focus).rotateBy(angleDeg) + focus
        return Circle(newOffset, radius, isCCW)
    }

    override fun reversed(): Circle =
        copy(isCCW = !isCCW)

    /** "⭗" case, anti-symmetric in args */
    infix fun isIn(circle: Circle): Boolean =
        distanceBetweenCenters(circle) + radius <= circle.radius

    /** "o o" case, symmetric in args */
    infix fun isOutBeside(circle: Circle): Boolean =
        distanceBetweenCenters(circle) >= radius + circle.radius

    // MAYBE: use epsilon?
    override fun isInside(circle: CircleOrLine): Boolean =
        when (circle) {
            is Circle ->
                when {
                    this.isCCW && circle.isCCW -> // "⭗" case
                        this isIn circle
                    this.isCCW && !circle.isCCW -> // "o o" case
                        this isOutBeside circle
                    !this.isCCW && circle.isCCW ->
                        false
                    else -> // both are outsides, "⭗'" case
                        circle isIn this
                }
            is Line ->
                if (isCCW) // " o |" case
                    circle.hasInside(center) && circle.distanceFrom(centerPoint) >= radius
                else
                    false
        }

    override fun isOutside(circle: CircleOrLine): Boolean =
        when (circle) {
            is Circle ->
                when {
                    this.isCCW && circle.isCCW -> // "o o" case
                        this isOutBeside circle
                    this.isCCW && !circle.isCCW -> // "⭗" case
                        this isIn circle
                    !this.isCCW && circle.isCCW -> // "⭗'" case
                        circle isIn this
                    else -> // both are outsides
                        false
                }
            is Line ->
                if (this.isCCW) // "| o" case
                    circle.hasOutside(center) && circle.distanceFrom(center) >= radius
                else
                    false
        }

    fun approximateToLine(screenCenter: Offset): Line {
        // here = visible center
        val hereX = screenCenter.x // here = point P
        val hereY = screenCenter.y
        val toCX = x - hereX // center = point C
        val toCY = y - hereY
        val pc = hypot(toCX, toCY)
        val weAreIn = radius > pc // <here> is inside the big circle
        val inSign = if (weAreIn) -1 else 1
        val rho = abs(pc - radius) // distance from <here> to the line
        val radiusSign = if (isCCW) +1 else -1
        val nx = inSign * toCX/pc // normal to the line from P = (cos phi, sin phi)
        val ny = inSign * toCY/pc
        // <PX, n> = x*nx + y*ny = rho is the line's equation
        val directionSign = (radiusSign * inSign) // -1 is cancelled by xOy system being left-handed / y-axis is upside down
//        val vx = directionSign * -ny // v = direction-vector of the line
//        val vy = directionSign * nx // (-ny, nx) = CCW 90 deg rotation of (nx, ny)
        val p0x = hereX + nx * rho // closest point on the line to <here>
        val p0y = hereY + ny * rho
        val c = -p0x*nx - p0y*ny
        return Line(nx*directionSign, ny*directionSign, c*directionSign)
    }

    companion object {
        @Deprecated("Superseded by more general and stable method GeneralizedCircle.perp3")
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
                    throw IllegalArgumentException("Not a line: 2 line points $p1 and $p2 near-coincide")
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
            return result.toGCircle()
        }

        // we use superior technology in this house
        @Deprecated("Superseded by more general and stable GeneralizedCircle.applyTo, see Circle.invert")
        fun _invert(inverting: CircleOrLine, theOneBeingInverted: CircleOrLine): CircleOrLine =
            when (inverting) {
                is Line -> when (theOneBeingInverted) {
                    is Line -> {
//                        val n0 = inverting.normalVector
//                        val n1 = theOneBeingInverted.normalVector
//                        val n2 = n1 - n0 * (2 * (n0.x*n1.x + n0.y*n1.y))
                        TODO()
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
                        TODO()
                    }
                    is Circle -> {
                        val (x, y, r) = inverting
                        val (x0, y0, r0) = theOneBeingInverted
                        when {
                            r == Double.POSITIVE_INFINITY -> // return a line
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
                circle1 is Line && circle2 is Line -> {
                    val (a1, b1, c1) = circle1
                    val (a2, b2, c2) = circle2
                    val w = a1*b2 - a2*b1
                    if (abs(w/circle1.norm/circle2.norm) < EPSILON) { // parallel condition
                        listOf(Point.CONFORMAL_INFINITY)
                    } else {
                        val wx = b1*c2 - b2*c1 // det in homogenous coordinates
                        val wy = a2*c1 - a1*c2
                        // we know that w != 0 (non-parallel)
                        val p = Point(wx/w, wy/w)
                        val q = Point.CONFORMAL_INFINITY
                        if (circle1.directionX*a2 + circle1.directionY*b2 >= 0)
                            listOf(p, q)
                        else
                            listOf(q, p)
                    }
                }
                circle1 is Line && circle2 is Circle -> {
                    val (cx, cy, r) = circle2
                    val (px, py) = circle1.project(Point(cx, cy))
                    val distance = hypot(px - cx, py - cy)
                    if (distance > r + EPSILON) {
                        emptyList()
                    } else if (abs(distance - r) < EPSILON) { // they touch (hold hands ///)
                        listOf(Point(px, py))
                    } else {
                        val pToIntersection = sqrt(r.pow(2) - distance*distance)
                        val vx = circle1.directionX
                        val vy = circle1.directionY
                        val p = Point(px + vx*pToIntersection, py + vy*pToIntersection)
                        val q = Point(px - vx*pToIntersection, py - vy*pToIntersection)
                        val s = circle1.pointInBetween(p, q) // directed segment p->s->q
                        if (circle2.hasInsideEpsilon(s))
                            listOf(p, q)
                        else
                            listOf(q, p)
                    }
                }
                circle1 is Circle && circle2 is Line ->
                    calculateIntersectionPoints(circle2, circle1).reversed() // ^^^
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
                        val p = Point(pcx + vy, pcy - vx)
                        val q = Point(pcx - vy, pcy + vx)
                        val s = circle1.pointInBetween(p, q) // directed arc p->s->q
                        if (circle2.hasInsideEpsilon(s))
                            listOf(p, q)
                        else
                            listOf(q, p)
                    }
                }
                else -> throw IllegalStateException("Never")
            }
    }
}

@Immutable
sealed interface UndirectedCircle : CircleOrLine {
    val x: Double
    val y: Double
    val radius: Double
}

@Serializable
@Immutable
sealed interface CircleOrLine : GCircle, LocusWithOrder {
    fun project(point: Point): Point
    fun distanceFrom(point: Point): Double
    fun distanceFrom(point: Offset): Double =
        distanceFrom(Point.fromOffset(point))
    /** <0 = inside, 0 on the circle, >0 = outside */
    fun checkPosition(point: Offset): Int
    /** -1 = inside, 0 on the circle, +1 = outside; also
     * returns 0 when the distance is in (-[EPSILON]; +[EPSILON]) */
    fun checkPositionEpsilon(point: Point): Int
    fun hasInside(point: Offset): Boolean =
        checkPosition(point) < 0
    fun hasOutside(point: Offset): Boolean =
        checkPosition(point) > 0
    fun hasInsideEpsilon(point: Point): Boolean =
        checkPositionEpsilon(point) < 0
    fun hasOutsideEpsilon(point: Point): Boolean =
        checkPositionEpsilon(point) > 0
    /** partial order ⊆ on circles (treated as either inside or outside regions) */
    infix fun isInside(circle: CircleOrLine): Boolean
    /** partial order ⊇ on circles (treated as either inside or outside regions)
     * `A isOutside B` == A ⊆ Bꟲ*/
    infix fun isOutside(circle: CircleOrLine): Boolean
    fun translate(vector: Offset): CircleOrLine
    fun scale(focus: Offset, zoom: Float): CircleOrLine
    override fun scale(focusX: Double, focusY: Double, zoom: Double): CircleOrLine
    fun rotate(focus: Offset, angleDeg: Float): CircleOrLine
    override fun reversed(): CircleOrLine
}

/** Represents totally ordered set of points isomorphic to ℝ or S¹ */
@Immutable
sealed interface LocusWithOrder {
    /** Either reverses the order of points within or does nothing ig */
    fun reversed(): LocusWithOrder
    // Constraints:
    // order2point(point2order(p)) === p
    // point2order(order2point(o)) === o
    /** sort points on the circle in the order they lie on it (starting from wherever) */
    fun point2order(point: Point): Double
    fun order2point(order: Double): Point
    fun orderInBetween(order1: Double, order2: Double): Double

    fun orderPoints(points: Collection<Point>): List<Point> =
        points.sortedBy { point2order(it) }

    fun pointInBetween(point1: Point, point2: Point) =
        order2point(orderInBetween(point2order(point1), point2order(point2)))
}
