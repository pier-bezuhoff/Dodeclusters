@file:Suppress("NOTHING_TO_INLINE")

package core.geometry

import androidx.compose.runtime.Immutable
import domain.never
import domain.signNonZero
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.acosh
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sqrt

// MAYBE: Clifford algebra (geometric product + other operations)
/**
 * Conformal-projective CGA representation of
 * circles/lines/points/imaginary circles via __NORMALIZED__ homogenous coordinates.
 * Often they are represented as trivectors, but we use dual,
 * vector representation for simplicity. For upcast see [GeneralizedCircle.fromGCircle].
 * In this notation `a ^ b` (outer product) corresponds to intersection/__meet__ and
 * `a v b` (regressive product) to __join__. Being normalized implies that its [norm2] is
 * either -1, +1 or 0. And if it's 0, the first non-zero coefficient of (w,x,y,z) is ±1.
 * When working with scaled versions, be careful, cuz most methods expect the normalized version.
 */
@Immutable
@Serializable
data class GeneralizedCircle(
    /** e_0 projection, homogenous scaling factor
     *
     * e_0 = (e_minus - e_plus) / 2
     */
    val w: Double,
    val x: Double,
    val y: Double,
    /** e_infinity projection
     *
     * e_inf = e_plus + e_minus
     *
     * Circle upcasting: [z] = ([x]^2 + [y]^2 - r^2) / 2
     */
    val z: Double
) {
    init {
        require(isValidHomogenousCoordinates(w,x,y,z)) {
            "GeneralizedCircle.init: homogenous coordinates ($w,$x,$y,$z) are invalid"
        }
    }

    @Suppress("unused")
    inline val ePlusProjection: Double get() =
        -w/2 + z
    @Suppress("unused")
    inline val eMinusProjection: Double get() =
        w/2 + z

    inline val norm2: Double get() =
        x*x + y*y - 2*w*z
//        this scalarProduct this
    // NOTE: norm=0 for points, norm=w*radius for circles
    inline val norm: Double get() =
        sqrt(abs(norm2))

    /** Assumes normalization. Radius squared */
    inline val r2: Double get() =
        if (abs(w) < EPSILON) {
            if (abs(x) < EPSILON && abs(y) < EPSILON)
                0.0 // conformal infinity
            else Double.POSITIVE_INFINITY // line
        } else { // norm2/(w*w)
            (x/w).pow(2) + (y/w).pow(2) - 2*z/w
        }

    /** Assumes normalization. */
    private inline val _isConformalInfinity: Boolean get() =
        abs(w) < EPSILON && abs(x) < EPSILON && abs(y) < EPSILON

    /** Assumes normalization. Test [_isConformalInfinity] first */
    private inline val _isLine: Boolean get() =
        w == 0.0 || abs(w) < EPSILON

    /** Assumes normalization. */
    private inline val _isPoint: Boolean get() = // includes conformal infinity
        abs(r2) < EPSILON2

    /** Assumes normalization. Test [_isConformalInfinity] and [_isLine] first */
    private inline val _isRealCircle: Boolean get() =
        r2 >= EPSILON2

    /** Assumes normalization. Test [_isConformalInfinity] and [_isLine] first */
    private inline val _isImaginaryCircle: Boolean get() =
        r2 <= -EPSILON2

    val isLine: Boolean get() =
        normalizedPreservingDirection().run {
            !_isConformalInfinity && _isLine
        }

    val isConformalInfinity: Boolean get() =
        normalizedPreservingDirection().run {
            abs(w) < EPSILON && abs(x) < EPSILON && abs(y) < EPSILON
        }

    val isPoint: Boolean get() =
        normalizedPreservingDirection().run {
            _isConformalInfinity || !_isLine && _isPoint
        }

    val isRealCircle: Boolean get() =
        normalizedPreservingDirection().run {
            !_isConformalInfinity && !_isLine && _isRealCircle
        }

    val isImaginaryCircle: Boolean get() =
        normalizedPreservingDirection().run {
            !_isConformalInfinity && !_isLine && _isImaginaryCircle
        }

    // NOTE: since we assume GeneralizedCircle to be normalized, be careful with these
    operator fun times(a: Double): GeneralizedCircle {
        return GeneralizedCircle(w * a, x * a, y * a, z * a)
    }

    operator fun times(a: Float): GeneralizedCircle {
        return GeneralizedCircle(w * a, x * a, y * a, z * a)
    }

    operator fun times(a: Int): GeneralizedCircle {
        return GeneralizedCircle(w * a, x * a, y * a, z * a)
    }

    operator fun plus(other: GeneralizedCircle): GeneralizedCircle =
        GeneralizedCircle(w + other.w, x + other.x, y + other.y, z + other.z)

    operator fun unaryMinus(): GeneralizedCircle =
        GeneralizedCircle(-w, -x, -y, -z)

    operator fun minus(other: GeneralizedCircle): GeneralizedCircle =
        GeneralizedCircle(w - other.w, x - other.x, y - other.y, z - other.z)

    infix fun scalarProduct(other: GeneralizedCircle): Double =
        x*other.x + y*other.y - z*other.w - other.z*w

    /** To be used for temporary non-normalized results */
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
        return if (
            a.w < 0 ||
            a.w == 0.0 && a.x < 0 ||
            a.x == 0.0 && a.y < 0 ||
            a.y == 0.0 && a.z < 0
        )
            -a // making sure c.normalized().w >= 0
        else
            a
    }

    /** To be used for temporary non-normalized results */
    fun normalizedPreservingDirection(): GeneralizedCircle {
        val n = norm
        val absN = abs(n)
        return if (absN < EPSILON) {
            if (n == 0.0 && abs(w) < EPSILON ||
                n != 0.0 && abs(w / n) < EPSILON
            ) // is conformal infinity?
                GeneralizedCircle(
                    0.0, 0.0, 0.0,
                    z = if (w == 0.0) sign(z) else sign(w) * sign(z)
                )
            else
                this * (1 / abs(w))
//        } else if (absN > 1e10) {
//            CONFORMAL_INFINITY // more clutches stashed here
        } else if (abs(w/n) < EPSILON) {
            this.copy(w = 0.0) * (1/n)
        } else {
            this * (1/n)
        }
    }

    //  X == k*X where k>0
    /** Assumes normalization */
    fun homogenousEquals(other: GeneralizedCircle, epsilon: Double = EPSILON): Boolean {
        val (w1,x1,y1,z1) = this
        val (w2,x2,y2,z2) = other
        return (w2 == 0.0 && abs(w1) < epsilon || w2 != 0.0 && abs(w1/w2 - 1.0) < epsilon) &&
                (x2 == 0.0 && abs(x1) < epsilon || x2 != 0.0 && abs(x1/x2 - 1.0) < epsilon) &&
                (y2 == 0.0 && abs(y1) < epsilon || y2 != 0.0 && abs(y1/y2 - 1.0) < epsilon) &&
                (z2 == 0.0 && abs(z1) < epsilon || z2 != 0.0 && abs(z1/z2 - 1.0) < epsilon)
    }

    // NOTE: Let C:= 0.5 * A.normalized + 0.5 * B.normalized;
    //  the resulting bisector C will NOT be normalized
    fun affineCombination(other: GeneralizedCircle, k: Double): GeneralizedCircle =
        this*k + other*(1 - k)

    /** Reflect [target] with respect to `this`
     * @return `this.dual * `[target]`.dual * this.dual`, normalized */
    fun applyTo(target: GeneralizedCircle): GeneralizedCircle {
        val (w0,x0,y0,z0) = target
        return GeneralizedCircle(
            -2*w*w*z0 + 2*w*x*x0 + 2*w*y*y0 - w0*x*x - w0*y*y,
            -2*w*x*z0 + 2*w*x0*z - 2*w0*x*z + x*x*x0 + 2*x*y*y0 - x0*y*y,
            -2*w*y*z0 + 2*w*y0*z - 2*w0*y*z - x*x*y0 + 2*x*x0*y + y*y*y0,
            -2*w0*z*z - x*x*z0 + 2*x*x0*z - y*y*z0 + 2*y*y0*z,
        ).normalizedPreservingDirection()
    }

    /**
     * Assumes normalization.
     * Same as [bisector] but is not continuous in f(`this`, [other]).
     * If [index]=m & [nOfSections]=n, select m-th n-sector among (n-1) possible,
     * counting from `this` circle's side. [index]=0 being `this` circle.
     * @return normalized n-sector */
    fun naiveBisector(
        other: GeneralizedCircle,
        nOfSections: Int = 2,
        index: Int = 1,
        inBetween: Boolean = true
    ): GeneralizedCircle {
        require(nOfSections >= 1)
        // signifies relative direction of [this] wrt. [other]
        val sign = signNonZero(this.scalarProduct(other))
        val d = this scalarProduct other
        val coDirected = d >= 0.0
        val pencilType = this.calculatePencilType(other)
        val maxInterpolationParameter = when (pencilType) {
            CirclePencilType.PARABOLIC -> 1.0
            CirclePencilType.ELLIPTIC -> {
                val inBetweenSign = if (inBetween) -1 else +1
                acos(inBetweenSign * d)
            }

            CirclePencilType.HYPERBOLIC -> acosh(abs(d))
            null -> 0.0
        }
        val inOutSign = when (pencilType) {
            CirclePencilType.ELLIPTIC -> {
                if (inBetween != coDirected) +1
                else -1
            }

            else -> +1
        }
        val k = sign * inOutSign * index.toDouble() / nOfSections * maxInterpolationParameter
        // exp(-k/2 * (a^b).normalized) >>> a
        val bivector = Rotor.fromOuterProduct(this, other).normalized()
        val rotor = (bivector * (-k / 2.0)).exp()
        return rotor.applyTo(this)
    }

    /** Assumes normalization.
     * If [index]=m & [nOfSections]=n, select m-th n-sector among (n-1) possible,
     * counting from `this` circle's side. [index]=0 being `this` circle.
     * For complementary n-sector input -[other] instead of +[other]
     * @return normalized n-sector
     */
    fun bisector(
        other: GeneralizedCircle,
        nOfSections: Int = 2,
        index: Int = 1,
    ): GeneralizedCircle {
        require(nOfSections >= 1)
        // NOTE: for imaginary circles this k!=0.5 is not very meaningful
        //  there is no meaningful "imaginary trisector"
        //  tho maybe there can be defined an "imaginary 4-sector"?
        val fraction = index.toDouble() / nOfSections - 0.5
        val pencil = Rotor.fromPencil(this, other)
        // exp(-k/2 * (a^b).normalized) >>> (a±b)
        val rotor = (pencil * (fraction / 2.0)).exp()
        val target = this + other // this trick allows to find imaginary bisector
        return rotor.applyTo(target)
    }

    /** Assumes normalization.
     * Apply composition of inversion wrt [engine1];[engine2] [speed] times (potentially
     * with interpolation).
     * Recommended to pre-scale inputs in -10..10 range, cuz
     * we are working with dimension cubed often
     * @return normalized result
     */
    fun biInversion(
        engine1: GeneralizedCircle, engine2: GeneralizedCircle,
        speed: Double
    ): GeneralizedCircle {
        val bivector = Rotor.fromPencil(engine1, engine2) * speed
        val rotor = bivector.exp()
        return rotor.applyTo(this)
    }

    /** Assumes normalization.
     * Recommended to pre-scale inputs in -10..10 range, cuz
     * we are working with dimension cubed often
     * @return normalized result
     */
    fun loxodromicShift(
        start: GeneralizedCircle, end: GeneralizedCircle,
        angle: Double, logDilation: Double
    ): GeneralizedCircle {
        val pencil = Rotor.fromOuterProduct(start, end).normalized()
        val perpPencil = pencil.dual()
        val rotation = (perpPencil * (-angle/2.0)).exp()
        val dilation = (pencil * (logDilation/2.0)).exp()
        // rotation and dilation commute by construction
        return dilation.applyTo(rotation.applyTo(this))
    }

    /**
     * Assumes normalization.
     *
     * `abs < 1` => 2 intersection points;
     * `abs = 1` => they touch, 1 intersection point;
     * `abs > 1` => 0 intersection points;
     *
     * `0` => perpendicular,
     * `<0` => anti-parallel
     *
     *  Not to be confused with "inversive angle", that is `acos(inversiveDistance)` and
     *  in case of intersecting circles is simply the oriented angle between them
     */
    inline fun inversiveDistance(other: GeneralizedCircle): Double =
        this scalarProduct other

    /**
     * Assumes normalization.
     * @return in-pencil angle (in radians),
     * for elliptic pencil - standard boring angle `[0; PI]`,
     * for hyperbolic -- `(hyperbolic angle)/i`,
     * for parabolic -- always `0`
     */
    fun inversiveAngle(other: GeneralizedCircle): Double =
        if (this.homogenousEquals(other)) {
            0.0 // no pencil
        } else {
            // sign of the scalar product is relative direction of [this] wrt. to [other]
            val d = inversiveDistance(other)
            val d0 = abs(d)
            when { // pencil type test
                abs(1 - d0) < EPSILON -> { // parabolic
                    0.0 // there might be some dual number trick
                }
                // s0 = 0 => they are perpendicular
                d0 < 1.0 -> { // elliptic
                    acos(d)
                }
                d0 > 1.0 -> { // hyperbolic
                    acosh(abs(d))
                }
                else -> never()
            }
        }

    // in non-elliptic pencils subdivide+out is hardly applicable
    // in particular, in hyperbolic pencil it returns imaginary circles or points
    // and in parabolic pencil it loses meaning (i think)
    /** Assumes normalization */
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
                else -> never()
            }
        }

    /** Assumes normalization */
    fun toGCircle(): GCircle =
        if (abs(w) < EPSILON) {
//            if (abs(z) > EPSILON && abs(x/z) < EPSILON && abs(y/z) < EPSILON) { // more accurate, less efficient
            if (abs(x) < EPSILON && abs(y) < EPSILON) // implies z > EPSILON
                Point.CONFORMAL_INFINITY
            else
                Line(x, y, -z)
        } else {
            val x0 = x/w
            val y0 = y/w
            val r2 = x0.pow(2) + y0.pow(2) - 2*z/w
            when {
                r2 >= EPSILON2 ->
                    Circle(x0, y0, radius = sqrt(r2), isCCW = w >= 0)
                r2 <= -EPSILON2 ->
                    ImaginaryCircle(x0, y0, sqrt(abs(r2)))
                else -> // abs(r2) < EPSILON2
                    Point(x0, y0)
            }
        }

    /** Assumes normalization.
     * Same as convert [toGCircle], but also force the result to be of the
     * same type as [sameGCircleTypeAs], i.e.
     *
     * [Point] => [Point],
     *
     * [CircleOrLine] => [CircleOrLine],
     *
     * [ImaginaryCircle] => [ImaginaryCircle]
     *
     * otherwise => `null`
     */
    fun toGCircleAs(sameGCircleTypeAs: GCircle): GCircle? =
        if (abs(w) < EPSILON) {
//            if (abs(z) > EPSILON && abs(x/z) < EPSILON && abs(y/z) < EPSILON) { // more accurate, less efficient
            if (abs(x) < EPSILON && abs(y) < EPSILON) {
                if (sameGCircleTypeAs is Point)
                    Point.CONFORMAL_INFINITY
                else null
            } else {
                if (sameGCircleTypeAs is CircleOrLine)
                    Line(x, y, -z)
                else null
            }
        } else {
            val x0 = x/w
            val y0 = y/w
            val d2 = x0.pow(2) + y0.pow(2)
            val r2 = d2 - 2*z/w
            when (sameGCircleTypeAs) {
                is Point -> // we combat random radius fluctuations this way
                    // ideally abs(r2) < EPSILON2
                    if (d2 > 1e12) // coordinates in millions is NG
                        Point.CONFORMAL_INFINITY
                    else
                        Point(x0, y0)
                is CircleOrLine ->
                    if (r2 >= EPSILON2) {
                        val r = sqrt(r2)
                        val isCCW = w >= 0
                        Circle(x0, y0, r, isCCW)
                    } else null
                is ImaginaryCircle ->
                    if (r2 <= -EPSILON2)
                        ImaginaryCircle(x0, y0, sqrt(abs(r2)))
                    else null
            }
        }

    companion object {
        val CONFORMAL_INFINITY = GeneralizedCircle(0.0, 0.0, 0.0, 1.0)

        /**
         * @return normalized [GeneralizedCircle] constructed from the given [gCircle]
         */
        fun fromGCircle(gCircle: GCircle): GeneralizedCircle =
            when (gCircle) {
                is Circle -> {
                    val sign = if (gCircle.isCCW) +1 else -1
                    // circle.norm = w*radius
                    val w = sign/gCircle.radius
                    GeneralizedCircle(
                        w,
                        w*gCircle.x,
                        w*gCircle.y,
                        w*(
                            (gCircle.x.pow(2) + gCircle.y.pow(2) - gCircle.radius.pow(2))/2
                        )
                    )
                }
                // a*x + b*y + c = 0
                // -> a*e_x + b*e_y - c*e_inf
                is Line ->
                    GeneralizedCircle(0.0, gCircle.a, gCircle.b, -gCircle.c)
                        .normalizedPreservingDirection()
                Point.CONFORMAL_INFINITY -> CONFORMAL_INFINITY
                is Point -> GeneralizedCircle(
                    1.0,
                    gCircle.x, gCircle.y,
                    (gCircle.x.pow(2) + gCircle.y.pow(2))/2
                ) // no need, it already has norm=0 and w=1
                is ImaginaryCircle -> GeneralizedCircle(
                    1.0, gCircle.x, gCircle.y,
                    (gCircle.x.pow(2) + gCircle.y.pow(2) + gCircle.radius.pow(2))/2
                ).normalizedPreservingDirection()
            }

        /** Assumes normalization.
         * Construct the GC perpendicular to the given 3, includes circle by 3 points, etc.
         * In CGA: `c1^c2^c3 == !( (!c1) v (!c2) v (!c3) )`
         * @return normalized result
         */
        fun perp3(
            c1: GeneralizedCircle,
            c2: GeneralizedCircle,
            c3: GeneralizedCircle
        ): GeneralizedCircle? {
            val (w1, x1, y1, z1) = c1
            val (w2, x2, y2, z2) = c2
            val (w3, x3, y3, z3) = c3
            // determinant-like totally antisymmetric product
            val w = w1*x2*y3 - w1*x3*y2 - w2*x1*y3 + w2*x3*y1 + w3*x1*y2 - w3*x2*y1
            val x = -w1*y2*z3 + w1*y3*z2 + w2*y1*z3 - w2*y3*z1 - w3*y1*z2 + w3*y2*z1
            val y = w1*x2*z3 - w1*x3*z2 - w2*x1*z3 + w2*x3*z1 + w3*x1*z2 - w3*x2*z1
            val z = -x1*y2*z3 + x1*y3*z2 + x2*y1*z3 - x2*y3*z1 - x3*y1*z2 + x3*y2*z1
            if (isNear0000(w,x,y,z)) {
                // case of c1-c2-c3 being in the same pencil
//                println("GeneralizedCircle.perp3 resulted in near-zero: ($w, $x, $y, $z) aka 0 or infinite number of solutions")
                return null
            }
            if (!isValidHomogenousCoordinates(w,x,y,z)) {
                return null
            }
            return GeneralizedCircle(w, x, y, z).normalizedPreservingDirection()
        }

        /** Assumes normalization.
         * Constructs the GC from the same pencil as [c1] and [c2], that is perpendicular to [perp],
         * includes circle from pencil thru point.
         * In CGA: `(!(c1^c2) ^ c3) == !( (!c1) ^ !((!c2) ^ (!c3)) )`
         * @return normalized result
         */
        fun parallel2perp1(
            c1: GeneralizedCircle,
            c2: GeneralizedCircle,
            perp: GeneralizedCircle
        ): GeneralizedCircle? {
            val (w1, x1, y1, z1) = c1
            val (w2, x2, y2, z2) = c2
            val (w3, x3, y3, z3) = perp
            // note cubic power, it can get out of hand quite fast
            val w = -w3*w2*z1 + w3*w1*z2 + w2*x3*x1 + w2*y3*y1 - w1*x3*x2 - w1*y3*y2
            val x = -w3*x2*z1 + w3*x1*z2 + w2*x1*z3 - w1*x2*z3 + x2*y3*y1 - x1*y3*y2
            val y = -w3*y2*z1 + w3*y1*z2 + w2*y1*z3 - w1*y2*z3 - x3*x2*y1 + x3*x1*y2
            val z = w2*z3*z1 - w1*z3*z2 - x3*x2*z1 + x3*x1*z2 - y3*y2*z1 + y3*y1*z2
            if (isNear0000(w,x,y,z)) {
                println(
                    "GeneralizedCircle.parallel2perp1 resulted in near-zero: " +
                    "($w, $x, $y, $z) aka 0 or infinite number of solutions"
                )
                return null
            }
            if (!isValidHomogenousCoordinates(w,x,y,z)) {
                return null
            }
            return GeneralizedCircle(w, x, y, z).normalizedPreservingDirection()
        }

        // there was an attempt..
        private inline fun isNear0000(w: Double, x: Double, y: Double, z: Double): Boolean =
            // NOTE: ehh, kinda risky
            abs(w) < EPSILON2 && abs(x) < EPSILON2 && abs(y) < EPSILON2 && abs(z) < EPSILON2

        inline fun isValidHomogenousCoordinates(w: Double, x: Double, y: Double, z: Double): Boolean =
            w.isFinite() && x.isFinite() && y.isFinite() && z.isFinite() &&
            (w != 0.0 || x != 0.0 || y != 0.0 || z != 0.0) // (0,0,0,0) is invalid in homogenous
    }
}

