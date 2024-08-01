package data.geometry

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlin.math.abs
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
        (z - 2*w)/2
    val eMinusProjection: Double get() =
        (2*w + z)/2

    val norm2: Double get() =
        x*x + y*y - 2*w*z
//        this scalarProduct this
    // NOTE: for circles, c.norm = w*radius
    val norm: Double get() =
        sqrt(abs(norm2))

    val isLine: Boolean get() =
        w == 0.0 ||
        abs(this.normalized().w) < EPSILON

    /** Radius squared */
    val r2: Double get() =
        if (isLine) Double.POSITIVE_INFINITY
        else norm2/(w*w)
//        else (x/w).pow(2) + (y/w).pow(2) - 2*z/w

    val isPoint: Boolean get() =
        !isLine && abs(norm) < EPSILON

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
        if (n == 0.0)
            return this * (1/w)
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
        return if (n == 0.0)
            this * (1/abs(w)) // n=0 => w!=0
        else this * (1/n)
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

    fun applyTo(target: GeneralizedCircle): GeneralizedCircle {
        val (w0,x0,y0,z0) = target
        return GeneralizedCircle(
//            (x*x0 + y*y0 - 2*w*z0)*w + (w*x0 - w0*x)*x + (w*y0 - w0*y)*y,
//            (x*x0 + y*y0 - 2*w0*z)*x + (x*y0 - x0*y)*y - 2*(x*z0 - x0*z)*w,
//            (x*x0 + y*y0 - 2*w0*z)*y - (x*y0 - x0*y)*x - 2*(y*z0 - y0*z)*w,
//            (x*x0 + y*y0 - 2*w0*z)*z - (x*z0 - x0*z)*x - (y*z0 - y0*z)*y

            -2*w*w*z0 + 2*w*x*x0 + 2*w*y*y0 - w0*x*x - w0*y*y,
            -2*w*x*z0 + 2*w*x0*z - 2*w0*x*z + x*x*x0 + 2*x*y*y0 - x0*y*y,
            -2*w*y*z0 + 2*w*y0*z - 2*w0*y*z - x*x*y0 + 2*x*x0*y + y*y*y0,
            -2*w0*z*z - x*x*z0 + 2*x*x0*z - y*y*z0 + 2*y*y0*z,

//            2*(x*x0 + y*y0)*w - (x*x + y*y)*w0 - 2*z0*w*w,
//            2*x*y*y0 + x0*(x*x - y*y) - 2*(x*w0 - x0*w)*z - 2*x*z0*w,
//            2*x*x0*y + y0*(y*y - x*x) - 2*(y*w0 - y0*w)*z - 2*y*z0*w,
//            2*(x*x0 + y*y0)*z - (x*x + y*y)*z0 - 2*z*z*w0
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
        // BUG: inBetween doesn't work
        val inOutSign = if (inBetween) +1 else -1
        val a0 = this.normalizedPreservingDirection()
        val b0 = other.normalizedPreservingDirection()*sign//*inOutSign
        val k = (nOfSections - index).toDouble()/nOfSections
        return a0*k + b0*(1.0-k)
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
            isLine -> Line(x, y, -z) // i'll be real, idk why there is a minus before z
            isRealCircle -> Circle(x / w, y / w, sqrt(r2))
            isPoint -> Point(x / w, y / w)
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
                is Point -> GeneralizedCircle(
                    1.0,
                    gCircle.x, gCircle.y,
                    (gCircle.x.pow(2) + gCircle.y.pow(2))
                )
                is ImaginaryCircle -> GeneralizedCircle(
                    1.0, gCircle.x, gCircle.y,
                    (gCircle.x.pow(2) + gCircle.y.pow(2) + gCircle.radius.pow(2))/2
                )
            }
    }
}

enum class CirclePencilType {
    ELLIPTIC, // lines with 1 common point, circles with 2 common points
    PARABOLIC, // parallel lines, circles tangential to 1 common line at 1 common point
    HYPERBOLIC, // concentric circles, circles perpendicular to every circle of a fixed (dual) elliptic pencil
}
