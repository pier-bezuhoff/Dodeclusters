package data.geometry

import androidx.compose.runtime.Immutable
import domain.signNonZero
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.acosh
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sqrt

/** A circle, line, imaginary circle or point */
@Immutable
sealed interface GCircle {
    fun scale(focusX: Double, focusY: Double, zoom: Double): GCircle
}

// TODO: Clifford algebra (geometric product + other operations)
/**
 * Conformal-projective CGA representation of circles/lines/points/imaginary circles
 * via homogenous coordinates.
 * */
@Serializable
@Immutable
data class GeneralizedCircle(
    /** e_0 projection, homogenous scaling factor
     *
     * e_0 = (e_minus - e_plus) / 2
     * */
    val w: Double,
    val x: Double,
    val y: Double,
    /** e_infinity projection
     *
     * e_inf = e_plus + e_minus
     *
     * Circle upcasting: [z] = ([x]^2 + [y]^2 - r^2) / 2
     * */
    val z: Double
) {
    init {
        require(listOf(w,x,y,z).any { it != 0.0 }) { "(0,0,0,0) Homogenous coordinates are invalid" }
    }

    val ePlusProjection: Double get() =
        -w/2 + z
    val eMinusProjection: Double get() =
        w/2 + z

    // norm=0 for points
    val norm2: Double get() =
        x*x + y*y - 2*w*z
//        this scalarProduct this
    // NOTE: for circles, c.norm = w*radius
    val norm: Double get() =
        sqrt(abs(norm2))

    val isConformalInfinity: Boolean get() =
        this.normalized().run {
            abs(w) < EPSILON && abs(x) < EPSILON && abs(y) < EPSILON
        }

    val isLine: Boolean get() =
        !isConformalInfinity && (w == 0.0 || abs(this.normalized().w) < EPSILON)

    /** Radius squared */
    val r2: Double get() =
        if (isConformalInfinity) 0.0
        else if (isLine) Double.POSITIVE_INFINITY
        else norm2/(w*w)
//        else (x/w).pow(2) + (y/w).pow(2) - 2*z/w

    val isPoint: Boolean get() = // includes conformal infinity
        !isLine && abs(r2) < EPSILON2

    val isRealCircle: Boolean get() =
        !isLine && r2 >= EPSILON2

    val isImaginaryCircle: Boolean get() =
        !isLine && r2 <= -EPSILON2

    operator fun times(a: Number): GeneralizedCircle {
        val a0 = a.toDouble()
        return GeneralizedCircle(w * a0, x * a0, y * a0, z * a0)
    }

    operator fun plus(other: GeneralizedCircle): GeneralizedCircle =
        GeneralizedCircle(w + other.w, x + other.x, y + other.y, z + other.z)

    operator fun unaryMinus(): GeneralizedCircle =
        GeneralizedCircle(-w, -x, -y, -z)

    operator fun minus(other: GeneralizedCircle): GeneralizedCircle =
        GeneralizedCircle(w - other.w, x - other.x, y - other.y, z - other.z)

    infix fun scalarProduct(other: GeneralizedCircle): Double =
        x*other.x + y*other.y - z*other.w - other.z*w

    fun normalized(): GeneralizedCircle {
        val n = norm
        if (abs(n) < EPSILON) {
            return if (n == 0.0 && abs(w) < EPSILON || n != 0.0 && abs(w/n) < EPSILON) // is conformal infinity?
                this * (1/z)
            else
                this * (1/w)
        }
        val a = if (abs(w/n) < EPSILON) {
            this.copy(w = 0.0) * (1/n)
        } else {
            this * (1/n)
        }
        return  if (
            a.w < 0 ||
            a.w == 0.0 && a.x < 0 ||
            a.x == 0.0 && a.y < 0 ||
            a.y == 0.0 && a.z < 0
        )
            -a // making sure c.normalized().w >= 0
        else
            a
    }

    fun normalizedPreservingDirection(): GeneralizedCircle {
        val n = norm
        return if (abs(n) < EPSILON) {
            if (n == 0.0 && abs(w) < EPSILON ||
                n != 0.0 && abs(w/n) < EPSILON
            ) // is conformal infinity?
                GeneralizedCircle(
                    0.0, 0.0, 0.0,
                    z = if (w == 0.0) sign(z) else sign(w)*sign(z)
                )
            else
                this * (1/abs(w))
        } else if (abs(w/n) < EPSILON) {
            this.copy(w = 0.0) * (1/n)
        } else {
            this * (1/n)
        }
    }

    //  X == k*X where k>0
    fun homogenousEquals(other: GeneralizedCircle, epsilon: Double = EPSILON): Boolean {
        val (w1,x1,y1,z1) = this.normalizedPreservingDirection()
        val (w2,x2,y2,z2) = other.normalizedPreservingDirection()
        return (w2 == 0.0 && abs(w1) < epsilon || abs(w1/w2 - 1.0) < epsilon) &&
                (x2 == 0.0 && abs(x1) < epsilon || abs(x1/x2 - 1.0) < epsilon) &&
                (y2 == 0.0 && abs(y1) < epsilon || abs(y1/y2 - 1.0) < epsilon) &&
                (z2 == 0.0 && abs(z1) < epsilon || abs(z1/z2 - 1.0) < epsilon)
    }

    //  X == k*X where k!=0
    fun homogenousEqualsNonOriented(other: GeneralizedCircle, epsilon: Double = EPSILON): Boolean {
        val (w1,x1,y1,z1) = this.normalized()
        val (w2,x2,y2,z2) = other.normalized()
        return (w2 == 0.0 && abs(w1) < epsilon || abs(w1/w2 - 1.0) < epsilon) &&
                (x2 == 0.0 && abs(x1) < epsilon || abs(x1/x2 - 1.0) < epsilon) &&
                (y2 == 0.0 && abs(y1) < epsilon || abs(y1/y2 - 1.0) < epsilon) &&
                (z2 == 0.0 && abs(z1) < epsilon || abs(z1/z2 - 1.0) < epsilon)
    }

    // NOTE: Let C:= 0.5 * A.normalized + 0.5 * B.normalized;
    //  the resulting bisector C will NOT be normalized
    /** Even though the inputs are pre-normalized before combining,
     * the output is NOT normalized */
    fun affineCombination(other: GeneralizedCircle, k: Double): GeneralizedCircle =
        this.normalizedPreservingDirection()*k + other.normalizedPreservingDirection()*(1 - k)

    /**
     * Apply reflect [target] with respect to [this]
     *
     * = [this].dual * [target].dual * [this].dual */
    fun applyTo(target: GeneralizedCircle): GeneralizedCircle {
        val (w0,x0,y0,z0) = target
        return GeneralizedCircle(
            -2*w*w*z0 + 2*w*x*x0 + 2*w*y*y0 - w0*x*x - w0*y*y,
            -2*w*x*z0 + 2*w*x0*z - 2*w0*x*z + x*x*x0 + 2*x*y*y0 - x0*y*y,
            -2*w*y*z0 + 2*w*y0*z - 2*w0*y*z - x*x*y0 + 2*x*x0*y + y*y*y0,
            -2*w0*z*z - x*x*z0 + 2*x*x0*z - y*y*z0 + 2*y*y0*z,
        ).normalizedPreservingDirection() // to avoid cumulative overflow
    }

    /** If [index]=m & [nOfSections]=n, select m-th n-sector among (n-1) possible,
     * counting from [this] circle's side. [index]=0 being [this] circle. */
    fun bisector(
        other: GeneralizedCircle,
        nOfSections: Int = 2,
        index: Int = 1,
        inBetween: Boolean = true
    ): GeneralizedCircle {
        require(nOfSections >= 1)
        // signifies relative direction of [this] wrt. [other]
        val sign = signNonZero(this.scalarProduct(other))
        val a = this.normalizedPreservingDirection()
        val b = other.normalizedPreservingDirection()
        val d = a scalarProduct b
        val pencilType = a.calculatePencilType(b)
        val maxInterpolationParameter = when (pencilType) {
            CirclePencilType.PARABOLIC -> 1.0
            CirclePencilType.ELLIPTIC ->
                if (inBetween) acos(abs(d))
                else PI - acos(abs(d))
            CirclePencilType.HYPERBOLIC -> acosh(abs(d))
            null -> 0.0
        }
        val inOutSign = if (inBetween || pencilType != CirclePencilType.ELLIPTIC) +1 else -1
        val k = sign * inOutSign * index.toDouble()/nOfSections * maxInterpolationParameter
        // exp(-k/2 * (a^b).normalized) >>> a
        val bivector = Rotor.fromOuterProduct(a, b)
            .normalized()
//        println("pencil: ${a.calculatePencilType(b)}")
//        println("maxK = $maxInterpolationParameter")
//        println("k = $k, $index/$nOfSections")
//        println("a = $a, plus=${a.ePlusProjection}, minus=${a.eMinusProjection}")
//        println("b = $b, plus=${b.ePlusProjection}, minus=${b.eMinusProjection}")
//        println("bivector = $bivector")
//        println("bivector.norm2 = ${bivector.norm2}")
        val rotor = (bivector * (-k/2.0)).exp()
        val result = rotor.applyTo(a)
//        println("rotor = $rotor")
//        println("rotor.norm2 = ${bivector.norm2}")
//        println("result = $result, plus=${result.ePlusProjection}, minus=${result.eMinusProjection}")
        return result
    }

    /** Recommended to pre-scale inputs in -10..10 range, cuz we are working with dimension cubed often */
    fun loxodromicShift(
        start: GeneralizedCircle, end: GeneralizedCircle,
        angle: Double, logDilation: Double
    ): GeneralizedCircle {
        val a = this.normalizedPreservingDirection()
        val pencil = Rotor.fromOuterProduct(start, end).normalized()
        val perpPencil = pencil.dual()
        val rotation = (perpPencil * (-angle/2.0)).exp()
        val dilation = (pencil * (logDilation/2.0)).exp()
        // rotation and dilation commute by construction
        val result = dilation.applyTo(rotation.applyTo(a))
        return result
    }

    /**
     * `abs < 1` => 2 intersection points;
     * `abs = 1` => they touch, 1 intersection point;
     * `abs > 1` => 0 intersection points;
     *
     * `0` => perpendicular,
     * `<0` => anti-parallel
     *
     *  Not to be confused with "inversive angle", that is `acos(inversiveDistance)` and
     *  in case of intersecting circles is simply the oriented angle between them
     * */
    fun inversiveDistance(other: GeneralizedCircle): Double =
        this.normalizedPreservingDirection() scalarProduct other.normalizedPreservingDirection()

    fun inversiveAngle(other: GeneralizedCircle): Double {
        val a = this.normalizedPreservingDirection()
        val b = other.normalizedPreservingDirection()
        val d = a scalarProduct b
        return when (a.calculatePencilType(b)) {
            CirclePencilType.PARABOLIC ->
                0.0
//                when {
//                    a.isLine && b.isLine -> {
//                        // tis wrong
////                    val la = a.toGCircle() as Line
////                    val lb = b.toGCircle() as Line
////                    val pb = lb.project(0.0, 0.0)
////                    abs(la.a*pb.x + la.b*pb.y + la.c)/norm // distance
//                        abs(a.z - b.z) // since both are normalized dz is the distance between them
//                    }
//                    a.isLine && b.isRealCircle -> 1.0/sqrt(b.r2)
//                    a.isRealCircle && b.isLine -> 1.0/sqrt(a.r2)
//                    a.isRealCircle && b.isRealCircle -> {
//                        val ca = a.toGCircle() as Circle
//                        val cb = b.toGCircle() as Circle
//                        if (hypot(ca.x - cb.x, ca.y - cb.y) > abs(ca.radius - cb.radius) + EPSILON)
//                            1.0/ca.radius + 1.0/cb.radius
//                        else
//                            abs(1.0/cb.radius - 1.0/ca.radius)
//                    }
//                    else -> 0.0
//                }
            CirclePencilType.ELLIPTIC -> acos(d)
            CirclePencilType.HYPERBOLIC -> acosh(abs(d))
            null -> 0.0
        }
    }

    // in non-elliptic pencils subdivide+out is hardly applicable
    // in particular, in hyperbolic pencil it returns imaginary circles or points
    // and in parabolic pencil it loses meaning (i think)
    fun calculatePencilType(other: GeneralizedCircle): CirclePencilType? =
        if (this.homogenousEquals(other)) {
            null
        } else {
            // sign of the scalar product is relative direction of [this] wrt. to [other]
            val d = inversiveDistance(other)
            val d0 = abs(d)
            when {
                abs(1 - d0) < EPSILON -> CirclePencilType.PARABOLIC
                // s0 = 0 => they are perpendicular
                d0 < 1.0 -> CirclePencilType.ELLIPTIC
                d0 > 1.0 -> CirclePencilType.HYPERBOLIC
                else -> throw IllegalStateException("Never")
            }
        }

    fun toGCircle(): GCircle =
        when {
            w == 0.0 && x == 0.0 && y == 0.0 -> Point.CONFORMAL_INFINITY
            isLine -> Line(x, y, -z) // i'll be real, idk why there is a minus before z
            isPoint -> Point(x / w, y / w)
            isRealCircle -> Circle(x / w, y / w, sqrt(r2))
            isImaginaryCircle -> ImaginaryCircle(x / w, y / w, sqrt(abs(r2)))
            else -> throw IllegalStateException("Never. $this")
        }

        fun toDirectedCircleOrLine(): DirectedCircleOrLine? =
            when {
                w == 0.0 && x == 0.0 && y == 0.0 -> null
                isLine -> Line(x, y, -z) // i'll be real, idk why there is a minus before z
                isPoint -> null
                isRealCircle -> DirectedCircle(
                    x / w, y / w, sqrt(r2),
                    inside = sign(w) > 0
                )
                isImaginaryCircle -> null
                else -> throw IllegalStateException("Never. $this")
            }

    companion object {
        fun fromGCircle(gCircle: GCircle): GeneralizedCircle =
            when (gCircle) {
                is Circle -> GeneralizedCircle(
                    1.0,
                    gCircle.x, gCircle.y,
                    (gCircle.x.pow(2) + gCircle.y.pow(2) - gCircle.radius.pow(2))/2
                ).normalizedPreservingDirection()
                // a*x + b*y + c = 0
                // -> a*e_x + b*e_y + c*e_inf
                is Line ->
                    GeneralizedCircle(0.0, gCircle.a, gCircle.b, -gCircle.c)
                        .normalizedPreservingDirection()
                Point.CONFORMAL_INFINITY -> GeneralizedCircle(0.0, 0.0, 0.0, 1.0)
                is Point -> GeneralizedCircle(
                    1.0,
                    gCircle.x, gCircle.y,
                    (gCircle.x.pow(2) + gCircle.y.pow(2))/2
                ).normalizedPreservingDirection()
                is ImaginaryCircle -> GeneralizedCircle(
                    1.0, gCircle.x, gCircle.y,
                    (gCircle.x.pow(2) + gCircle.y.pow(2) + gCircle.radius.pow(2))/2
                ).normalizedPreservingDirection()
            }

        /** Construct the GC perpendicular to the given 3, includes circle by 3 points, etc.
         * In CGA: `c1^c2^c3 == !( (!c1) v (!c2) v (!c3) )` */
        fun perp3(c1: GeneralizedCircle, c2: GeneralizedCircle, c3: GeneralizedCircle): GeneralizedCircle? {
            val (w1, x1, y1, z1) = c1
            val (w2, x2, y2, z2) = c2
            val (w3, x3, y3, z3) = c3
            // det-like totally antisymmetric product
            val w = w1*x2*y3 - w1*x3*y2 - w2*x1*y3 + w2*x3*y1 + w3*x1*y2 - w3*x2*y1
            val x = -w1*y2*z3 + w1*y3*z2 + w2*y1*z3 - w2*y3*z1 - w3*y1*z2 + w3*y2*z1
            val y = w1*x2*z3 - w1*x3*z2 - w2*x1*z3 + w2*x3*z1 + w3*x1*z2 - w3*x2*z1
            val z = -x1*y2*z3 + x1*y3*z2 + x2*y1*z3 - x2*y3*z1 - x3*y1*z2 + x3*y2*z1
            if (is0000(w,x,y,z)) {
                println("GeneralizedCircle.perp3 resulted in near-zero: ($w, $x, $y, $z) aka 0 or infinite number of solutions")
                return null
            }
            if (listOf(w,x,y,z).any { it.isNaN() || it.isInfinite() }) {
                return null
            }
            return GeneralizedCircle(w, x, y, z).normalizedPreservingDirection()
        }

        /** Construct the GC from the same pencil as [c1] and [c2], that is perpendicular to [perp], includes circle from pencil thru point.
         * In CGA: `(!(c1^c2) ^ c3) == !( (!c1) ^ !((!c2) ^ (!c3)) )` */
        fun parallel2perp1(c1: GeneralizedCircle, c2: GeneralizedCircle, perp: GeneralizedCircle): GeneralizedCircle? {
            val (w1, x1, y1, z1) = c1
            val (w2, x2, y2, z2) = c2
            val (w3, x3, y3, z3) = perp
            val w = -w3*w2*z1 + w3*w1*z2 + w2*x3*x1 + w2*y3*y1 - w1*x3*x2 - w1*y3*y2
            val x = -w3*x2*z1 + w3*x1*z2 + w2*x1*z3 - w1*x2*z3 + x2*y3*y1 - x1*y3*y2
            val y = -w3*y2*z1 + w3*y1*z2 + w2*y1*z3 - w1*y2*z3 - x3*x2*y1 + x3*x1*y2
            val z = w2*z3*z1 - w1*z3*z2 - x3*x2*z1 + x3*x1*z2 - y3*y2*z1 + y3*y1*z2
            if (is0000(w,x,y,z)) {
                println("GeneralizedCircle.parallel2perp1 resulted in near-zero: ($w, $x, $y, $z) aka 0 or infinite number of solutions")
                return null
            }
            if (listOf(w,x,y,z).any { it.isNaN() || it.isInfinite() }) {
                return null
            }
            return GeneralizedCircle(w, x, y, z).normalizedPreservingDirection()
        }

        // there was an attempt..
        fun is0000(w: Double, x: Double, y: Double, z: Double): Boolean =
            setOf(w,x,y,z).all { abs(it) < EPSILON2 } // NOTE: ehh, kinda risky
//            w == 0.0 && x == 0.0 && y == 0.0 && z == 0.0
    }
}

enum class CirclePencilType {
    ELLIPTIC, // lines with 1 common point, circles with 2 common points
    PARABOLIC, // parallel lines, circles tangential to 1 common line at 1 common point
    HYPERBOLIC, // concentric circles, circles perpendicular to every circle of a fixed (dual) elliptic pencil
}
