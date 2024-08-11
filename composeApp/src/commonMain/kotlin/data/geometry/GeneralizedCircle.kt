package data.geometry

import androidx.compose.runtime.Immutable
import data.round
import kotlinx.serialization.Serializable
import ui.colorpicker.toDegree
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.acosh
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sqrt

/** A circle, line, imaginary circle or point */
@Immutable
sealed interface GCircle

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

    val norm2: Double get() =
        x*x + y*y - 2*w*z
//        this scalarProduct this
    // NOTE: for circles, c.norm = w*radius
    val norm: Double get() =
        sqrt(abs(norm2))

    val isConformalInfinity: Boolean get() =
        this.normalized().run {
            w < EPSILON && x < EPSILON && y < EPSILON
        }

    val isLine: Boolean get() =
        !isConformalInfinity && (w == 0.0 || abs(this.normalized().w) < EPSILON)

    /** Radius squared */
    val r2: Double get() =
        if (isLine) Double.POSITIVE_INFINITY
        else norm2/(w*w)
//        else (x/w).pow(2) + (y/w).pow(2) - 2*z/w

    val isPoint: Boolean get() = // includes conformal infinity
        !isLine && abs(norm2) < EPSILON2

    val isRealCircle: Boolean get() =
        !isLine && r2 >= EPSILON

    val isImaginaryCircle: Boolean get() =
        !isLine && r2 <= -EPSILON

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
        x*other.x +
        y*other.y +
        (z - w/2)*(other.z - other.w/2) - // e_plus part
        (z + w/2)*(other.z + other.w/2) // e_minus part

    fun normalized(): GeneralizedCircle {
        val n = norm
        if (n == 0.0) {
            return if (w == 0.0)
                this * (1/z)
            else
                this * (1/w)
        }
        val a = this * (1/norm)
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
        return if (n == 0.0) {
            if (w == 0.0) // conformal infinity
                this * (1/abs(z))
            else
                this * (1/abs(w))
        } else {
            this * (1/n)
        }
    }

    //  X == k*X where k>0
    // NOTE: ignores direction since it uses .normalized()
    infix fun homogenousEquals(other: GeneralizedCircle): Boolean {
        val (wxyz1, wxyz2) = listOf(
            this.normalized(),
            other.normalized()
        ).map { listOf(it.w, it.x, it.y, it.z) }
        return wxyz1.zip(wxyz2)
            .all { (a1, a2) ->
                abs(a1 - a2) < EPSILON
            }
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
        val sign = this.scalarProduct(other).let {
            if (it >= 0) +1
            else -1
        }
        val inOutSign = if (inBetween) +1 else -1
        val a = this.normalizedPreservingDirection()
        val b = other.normalizedPreservingDirection()
        val d = a scalarProduct b
        // some problems with lines
        val maxInterpolationParameter = when (a.calculatePencilType(b)) {
            CirclePencilType.PARABOLIC -> when {
                a.isLine && b.isLine -> {
                    // tis wrong
//                    val la = a.toGCircle() as Line
//                    val lb = b.toGCircle() as Line
//                    val pb = lb.project(0.0, 0.0)
//                    abs(la.a*pb.x + la.b*pb.y + la.c)/norm // distance
                    abs(a.z - b.z)
                }
                a.isLine && b.isRealCircle -> 1.0/sqrt(b.r2)
                a.isRealCircle && b.isLine -> 1.0/sqrt(a.r2)
                a.isRealCircle && b.isRealCircle -> {
                    // TODO: test inverse radii cases
                    val ca = a.toGCircle() as Circle
                    val cb = b.toGCircle() as Circle
                    if (hypot(ca.x - cb.x, ca.y - cb.y) > abs(ca.radius - cb.radius) + EPSILON)
                        1.0/ca.radius + 1.0/cb.radius
                    else
                        abs(1.0/cb.radius - 1.0/ca.radius)
                }
                else -> 0.0
            }
            CirclePencilType.ELLIPTIC -> acos(d).also { println("maxK = ${it.toDegree().round(2)}°") }
            CirclePencilType.HYPERBOLIC -> acosh(abs(d))
            null -> 0.0
        }
        val k = sign * inOutSign * index.toDouble()/nOfSections * maxInterpolationParameter
        // exp(-k/2 * (a^b)) >>> a
        val bivector = Rotor.fromOuterProduct(a, b).normalized()
        println("pencil: ${a.calculatePencilType(b)}")
        println("maxK = $maxInterpolationParameter")
        println("k = $k, $index/$nOfSections")
//        println("a = $a, plus=${a.ePlusProjection}, minus=${a.eMinusProjection}")
//        println("b = $b, plus=${b.ePlusProjection}, minus=${b.eMinusProjection}")
        println("bivector = $bivector")
        val rotor = (bivector * (-k/2)).exp()
        val result = rotor.applyTo(a)
//        println("bivector.norm2 = ${bivector.norm2}")
        println("rotor = $rotor")
//        println("rotor.norm2 = ${bivector.norm2}")
        println("result = $result, plus=${result.ePlusProjection}, minus=${result.eMinusProjection}")
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

    // in non-elliptic pencils subdivide+out is hardly applicable
    // in particular, in hyperbolic pencil it returns imaginary circles or points
    // and in parabolic pencil it loses meaning (i think)
    fun calculatePencilType(other: GeneralizedCircle): CirclePencilType? =
        if (this homogenousEquals other) {
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

    fun toGCircle(): GCircle {
        return when {
            w == 0.0 && x == 0.0 && y == 0.0 -> Point.CONFORMAL_INFINITY
            isLine -> Line(x, y, -z) // i'll be real, idk why there is a minus before z
            isPoint -> Point(x / w, y / w)
            isRealCircle -> Circle(x / w, y / w, sqrt(r2))
            isImaginaryCircle -> ImaginaryCircle(x / w, y / w, sqrt(abs(r2)))
            else -> throw IllegalStateException("Never. $this")
        }
    }

    companion object {
        fun fromGCircle(gCircle: GCircle): GeneralizedCircle =
            when (gCircle) {
                is Circle -> GeneralizedCircle(
                    1.0,
                    gCircle.x, gCircle.y,
                    (gCircle.x.pow(2) + gCircle.y.pow(2) - gCircle.radius.pow(2))/2
                )
                // a*x + b*y + c = 0
                // -> a*e_x + b*e_y + c*e_inf
                is Line -> GeneralizedCircle(0.0, gCircle.a, gCircle.b, -gCircle.c).normalized()
                Point.CONFORMAL_INFINITY -> GeneralizedCircle(0.0, 0.0, 0.0, 1.0)
                is Point -> GeneralizedCircle(
                    1.0,
                    gCircle.x, gCircle.y,
                    (gCircle.x.pow(2) + gCircle.y.pow(2))/2
                )
                is ImaginaryCircle -> GeneralizedCircle(
                    1.0, gCircle.x, gCircle.y,
                    (gCircle.x.pow(2) + gCircle.y.pow(2) + gCircle.radius.pow(2))/2
                )
            }

        /** Construct GC perpendicular to the given 3, includes circle by 3 points, etc.
         * In CGA: `!( (!c1) ^ (!c2) ^ (!c3) )` */
        fun perp3(c1: GeneralizedCircle, c2: GeneralizedCircle, c3: GeneralizedCircle): GeneralizedCircle? {
            val (w1, x1, y1, z1) = c1
            val (w2, x2, y2, z2) = c2
            val (w3, x3, y3, z3) = c3
            // det-like totally antisymmetric product
            val w = w1*x2*y3 - w1*x3*y2 - w2*x1*y3 + w2*x3*y1 + w3*x1*y2 - w3*x2*y1
            val x = -w1*y2*z3 + w1*y3*z2 + w2*y1*z3 - w2*y3*z1 - w3*y1*z2 + w3*y2*z1
            val y = w1*x2*z3 - w1*x3*z2 - w2*x1*z3 + w2*x3*z1 + w3*x1*z2 - w3*x2*z1
            val z = -x1*y2*z3 + x1*y3*z2 + x2*y1*z3 - x2*y3*z1 - x3*y1*z2 + x3*y2*z1
            if (w == 0.0 && x == 0.0 && y == 0.0 && z == 0.0)
                return null
            return GeneralizedCircle(w, x, y, z).normalizedPreservingDirection()
        }
    }
}

enum class CirclePencilType {
    ELLIPTIC, // lines with 1 common point, circles with 2 common points
    PARABOLIC, // parallel lines, circles tangential to 1 common line at 1 common point
    HYPERBOLIC, // concentric circles, circles perpendicular to every circle of a fixed (dual) elliptic pencil
}
