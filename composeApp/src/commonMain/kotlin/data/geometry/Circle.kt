package data.geometry

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import data.kmath_complex.ComplexField
import data.kmath_complex.r
import data.kmath_complex.r2
import domain.TAU
import domain.degrees
import domain.never
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
@Immutable
@Serializable
@SerialName("circle")
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
    val center: Offset
        get() =
            Offset(x.toFloat(), y.toFloat())

    val centerPoint: Point
        get() =
            Point(x, y)

    val r2: Double get() =
        radius * radius

    init {
        require(
            x.isFinite() && y.isFinite() && radius.isFinite() &&
            radius > 0.0
        ) { "Invalid Circle($x, $y, $radius, isCCW = $isCCW)" }
        // points and imaginary circle should not be mixed in
    }

    constructor(center: Offset, radius: Double, isCCW: Boolean = true) :
            this(center.x.toDouble(), center.y.toDouble(), radius, isCCW)

    constructor(center: Offset, radius: Float, isCCW: Boolean = true) :
            this(center.x.toDouble(), center.y.toDouble(), radius.toDouble(), isCCW)

    override fun project(point: Point): Point {
        val (x1, y1) = point
        if (x == x1 && y == y1 || point == Point.CONFORMAL_INFINITY) {
            println("WARNING: bad projection at Circle.project")
            return order2point(0.0)
        }
        val vx = x1 - x
        val vy = y1 - y
        val vLength = hypot(vx, vy)
        return Point(
            x + (vx / vLength) * radius,
            y + (vy / vLength) * radius
        )
    }

    override fun distanceFrom(point: Offset): Double =
        abs((point - center).getDistance() - radius)

    override fun distanceFrom(point: Point): Double =
        if (point == Point.CONFORMAL_INFINITY) Double.POSITIVE_INFINITY
        else abs(hypot(point.x - x, point.y - y) - radius)

    fun distanceBetweenCenters(circle: Circle): Double =
        hypot(x - circle.x, y - circle.y)

    override fun calculateLocation(point: Offset): RegionPointLocation {
        val distance = (center - point).getDistance()
        val r = radius.toFloat()
        return when {
            distance < r ->
                if (isCCW) RegionPointLocation.IN
                else RegionPointLocation.OUT
            distance == r -> RegionPointLocation.BORDERING // this prob never happens, strict double equality
            distance > r ->
                if (isCCW) RegionPointLocation.OUT
                else RegionPointLocation.IN
            else -> throw IllegalStateException("Illegal comparison")
        }
    }

    override fun calculateLocationEpsilon(point: Point): RegionPointLocation {
        if (point == Point.CONFORMAL_INFINITY) {
            return if (isCCW) RegionPointLocation.OUT else RegionPointLocation.IN
        }
        val distance = hypot(point.x - x, point.y - y)
        return if (abs(radius - distance) < EPSILON) {
            RegionPointLocation.BORDERING
        } else if (distance < radius) {
            if (isCCW) RegionPointLocation.IN else RegionPointLocation.OUT
        } else {
            // outside
            if (isCCW) RegionPointLocation.OUT else RegionPointLocation.IN
        }
    }

    override fun point2angle(point: Point): Float {
        require(point != Point.CONFORMAL_INFINITY && point != centerPoint)
        return atan2(-point.y + y, point.x - x).degrees
    }

    /** CCW order in [-[PI]; +[PI]] starting from the East: ENWS */
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
            order1 + (2* PI - (order1 - order2))/2.0
//        val half = (order2 - order1).mod(2*PI)/2.0

    override fun orderIsInBetween(startOrder: Double, order: Double, endOrder: Double): Boolean {
        val o = (order + TAU) % TAU
        val start = (startOrder + TAU) % TAU
        val end = (endOrder + TAU) % TAU
        return if (isCCW) {
            if (start <= end) {
                o in start..end
            } else { // the arc contains order=0
                o in start..0.0 || o in 0.0..end
            }
        } else {
            if (end <= start) { // CW circle order is reversed
                o in end..start
            } else {
                o in end..0.0 || o in 0.0..start
            }
        }
    }

    override fun translated(vector: Offset): Circle =
        Circle(center + vector, radius, isCCW)

    fun translated(dx: Double, dy: Double): Circle =
        Circle(x + dx, y + dy, radius, isCCW)

    override fun scaled(focus: Offset, zoom: Float): Circle {
        val newOffset = (center - focus) * zoom + focus
        return Circle(newOffset, zoom * radius, isCCW)
    }

    override fun scaled(focusX: Double, focusY: Double, zoom: Double): Circle {
        val newX = (x - focusX) * zoom + focusX
        val newY = (y - focusY) * zoom + focusY
        return Circle(newX, newY, zoom * radius, isCCW)
    }

    override fun rotated(focus: Offset, angleInDegrees: Float): Circle {
        val newOffset = (center - focus).rotateBy(angleInDegrees) + focus
        return Circle(newOffset, radius, isCCW)
    }

    override fun transformed(translation: Offset, focus: Offset, zoom: Float, rotationAngle: Float): Circle {
        var newX: Double = x + translation.x
        var newY: Double = y + translation.y
        if (focus != Offset.Unspecified) {
            val (focusX, focusY) = focus
            // cmp. Offset.rotateBy & zoom and rotation are commutative
            val dx = newX - focusX
            val dy = newY - focusY
            val phi: Double = rotationAngle * PI/180.0
            val cosPhi = cos(phi)
            val sinPhi = sin(phi)
            newX = (dx * cosPhi - dy * sinPhi) * zoom + focusX
            newY = (dx * sinPhi + dy * cosPhi) * zoom + focusY
        } // tbf because of T;S;R order it is not completely accurate
        return Circle(newX, newY, zoom * radius, isCCW = isCCW)
    }

    override fun reversed(): Circle =
        copy(isCCW = !isCCW)

    /** tangent line at [project]`(point)`, directed along the circle */
    override fun tangentAt(point: Point): Line {
        val p2cx = x - point.x // center-to-point
        val p2cy = y - point.y
        val l = hypot(p2cx, p2cy)
        val sign = if (isCCW) +1 else -1 // if the circle is CCW, it is to the left of the tangent
        val a = sign*p2cx/l // normal
        val b = sign*p2cy/l
        val (baseX, baseY) = project(point)
        val c = -a*baseX - b*baseY
        return Line(a, b, c)
    }

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
            is Line -> {
                if (isCCW) // " o |" case
                    circle.hasInside(center) && circle.distanceFrom(centerPoint) >= radius
                else
                    false
            }
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
            is Line -> {
                if (this.isCCW) // "| o" case
                    circle.hasOutside(center) && circle.distanceFrom(center) >= radius
                else
                    false
            }
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
        return Line(nx * directionSign, ny * directionSign, c * directionSign)
    }

    fun translatedUntilBiTangency(base1: CircleOrLineOrPoint, base2: CircleOrLineOrPoint): Circle? {
        val b11: CircleOrLine
        val b12: CircleOrLine
        val b21: CircleOrLine
        val b22: CircleOrLine
        when (base1) {
            is Circle -> {
                b11 = Circle(base1.x, base1.y, base1.radius + this.radius)
                b12 = Circle(base1.x, base1.y, abs(base1.radius - this.radius))
            }
            is Line -> {
                val dlx = this.radius*base1.a/base1.norm
                val dly = this.radius*base1.b/base1.norm
                b11 = base1.translated(dlx, dly)
                b12 = base1.translated(-dlx, -dly)
            }
            is Point -> {
                b11 = Circle(base1.x, base1.y, this.radius)
                b12 = b11
            }
        }
        when (base2) {
            is Circle -> {
                b21 = Circle(base2.x, base2.y, base2.radius + this.radius)
                b22 = Circle(base2.x, base2.y, abs(base2.radius - this.radius))
            }
            is Line -> {
                val dlx = this.radius*base2.a/base2.norm
                val dly = this.radius*base2.b/base2.norm
                b21 = base2.translated(dlx, dly)
                b22 = base2.translated(-dlx, -dly)
            }
            is Point -> {
                b21 = Circle(base2.x, base2.y, this.radius)
                b22 = b21
            }
        }
        // out-out, out-in, in-out, in-in tangency cases
        val potentialCenters =
            calculateIntersectionPoints(b11, b21) +
            calculateIntersectionPoints(b11, b22) +
            calculateIntersectionPoints(b12, b21) +
            calculateIntersectionPoints(b12, b22)
        return potentialCenters
            .distinct()
            .minByOrNull { it.distanceFrom(this.centerPoint) }
            ?.let { newCenter ->
                this.copy(x = newCenter.x, y = newCenter.y)
            }
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
                val c = (z2 - z1)*(w - w.r2)/(2.0*w.im* i) + z1
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
                val center = (z1 + z2)/2 + veryBigRadius*(v/v.r)* i
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
            val engine = GeneralizedCircle.fromGCircle(inverting).normalizedPreservingDirection()
            val target = GeneralizedCircle.fromGCircle(theOneBeingInverted).normalizedPreservingDirection()
            val result = engine.applyTo(target).normalizedPreservingDirection()
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
                                Circle(x, y, r * r / r0)
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

        private const val TANGENTIAL_TOUCH_EPSILON = 2 * EPSILON

        // NOTE: on Android triple tangential intersections are brittle
        /** @return list of 0, 1 or 2 intersection points. When there are 2 intersection points,
         * they are ordered as follows:
         * [circle1] "needle" (internal orientation)
         * goes thru
         * [circle2] "fabric" (external orientation). The entrance is the 1st, the
         * exit is the 2nd of the resulting points */
        fun calculateIntersectionPoints(
            circle1: CircleOrLine, circle2: CircleOrLine
        ): List<Point> =
            when {
                circle1 == circle2 ->
                    emptyList()
                circle1 is Line && circle2 is Line -> {
                    val (a1, b1, c1) = circle1
                    val (a2, b2, c2) = circle2
                    val w = a1*b2 - a2*b1
                    // collinearity condition
                    if (abs(w / circle1.norm / circle2.norm) < EPSILON) {
                        listOf(Point.CONFORMAL_INFINITY) // & potentially full coincidence
                    } else {
                        val wx = b1*c2 - b2*c1 // det in homogenous coordinates
                        val wy = a2*c1 - a1*c2
                        // we know that w != 0 (non-parallel)
                        val p = Point(wx / w, wy / w)
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
                    if (distance >= r + TANGENTIAL_TOUCH_EPSILON) {
                        emptyList()
                    } else if (abs(distance - r) < TANGENTIAL_TOUCH_EPSILON) { // they touch (hold hands ///)
                        listOf(Point(px, py))
                    } else {
                        val pToIntersection = sqrt(r.pow(2) - distance * distance)
                        val vx = circle1.directionX
                        val vy = circle1.directionY
                        val p = Point(px + vx * pToIntersection, py + vy * pToIntersection)
                        val q = Point(px - vx * pToIntersection, py - vy * pToIntersection)
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
                    if (abs(r1 - r2) >= d + TANGENTIAL_TOUCH_EPSILON || d >= r1 + r2 + TANGENTIAL_TOUCH_EPSILON) {
                        emptyList()
                    } else if (
                        abs(abs(r1 - r2) - d) < TANGENTIAL_TOUCH_EPSILON || // inner touch
                        abs(d - r1 - r2) < TANGENTIAL_TOUCH_EPSILON // outer touch
                    ) {
                        listOf(Point(x1 + dcx / d * r1, y1 + dcy / d * r1))
                    } else {
                        val dr2 = r12 - r22
                        // reference (0->1, 1->2):
                        // https://stackoverflow.com/questions/3349125/circle-circle-intersection-points#answer-3349134
                        val a = (d2 + dr2)/(2 * d)
                        val h = sqrt(r12 - a * a)
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
                else -> never()
            }
    }
}
