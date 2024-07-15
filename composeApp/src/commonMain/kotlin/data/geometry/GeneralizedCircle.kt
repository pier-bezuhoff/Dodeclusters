package data.geometry

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/** A circle, line, imaginary circle or point */
sealed interface GCircle

// TODO: Clifford algebra (geometric product + other operations)
/**
 * Conformal-projective CGA representation of circles/lines/points/imaginary circles
 * via homogenous coordinates.
 * */
@Serializable
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
        this scalarProduct this
    val norm: Double get() =
        sqrt(abs(norm2))

    val isLine: Boolean get() =
        w == 0.0
//        abs(w) < EPSILON

    /** Radius squared */
    val r2: Double get() =
        if (isLine) Double.POSITIVE_INFINITY
        else (x/w).pow(2) + (y/w).pow(2) - 2*z/w

    val isPoint: Boolean get() =
        abs(norm2) < EPSILON

    val isRealCircle: Boolean get() =
        norm2 >= EPSILON

    val isImaginaryCircle: Boolean get() =
        norm2 <= -EPSILON

    /** Upcast a point (x, y) */
    constructor(x: Double, y: Double) : this(
        1.0, x, y, (x.pow(2) + y.pow(2))/2
    )

    constructor(circle: Circle) : this(
        1.0,
        circle.x, circle.y,
        (circle.x.pow(2) + circle.y.pow(2) - circle.radius.pow(2))/2
    )

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
        (z - w/2)*(other.z - other.w/2) + // e_plus part
        - (z + w/2)*(other.z + other.w/2) // e_minus part

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

    // NOTE: -X != X, sign represents direction
    //  X == k*X where k>0
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

    // = affineCombination(other, infinity)
    fun altBisector(other: GeneralizedCircle): GeneralizedCircle =
        this - other

    // in non-elliptic pencils subdivide+out is meaningless
    fun calculatePencilType(other: GeneralizedCircle): CirclePencilType? =
        if (this homogenousEquals other) {
            null
        } else {
            val s = this.normalized() scalarProduct other.normalized()
            when {
                abs(s) < EPSILON -> CirclePencilType.PARABOLIC
                s > 0 -> CirclePencilType.ELLIPTIC
                s < 0 -> CirclePencilType.HYPERBOLIC
                else -> throw IllegalStateException("Never")
            }
        }

    fun toGCircle(): GCircle =
        when {
            isRealCircle -> Circle(x / w, y / w, sqrt(r2))
            isLine -> Line(x, y, z)
            isPoint -> Point(x / w, y / w)
            isImaginaryCircle -> ImaginaryCircle(x / w, y / w, sqrt(abs(r2)))
            else -> throw IllegalStateException("Never")
        }

    companion object {
        fun fromGCircle(gCircles: GCircle): GeneralizedCircle =
            when (gCircles) {
                is Circle -> GeneralizedCircle(gCircles)
                // a*x + b*y + c = 0
                // -> a*e_x + b*e_y + c*e_inf
                is Line -> GeneralizedCircle(0.0, gCircles.a, gCircles.b, gCircles.c)
                is Point -> GeneralizedCircle(gCircles.x, gCircles.y)
                is ImaginaryCircle -> GeneralizedCircle(
                    1.0, gCircles.x, gCircles.y,
                    (gCircles.x.pow(2) + gCircles.y.pow(2) + gCircles.radius.pow(2))/2
                )
            }
    }
}

enum class CirclePencilType {
    ELLIPTIC, // lines with 1 common point, circles with 2 common points
    PARABOLIC, // parallel lines, circles tangential to 1 common line at 1 common point
    HYPERBOLIC, // concentric circles, circles perpendicular to every circle of a fixed (dual) elliptic pencil
}
