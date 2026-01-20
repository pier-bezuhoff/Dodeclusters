@file:Suppress("LocalVariableName", "NOTHING_TO_INLINE")

package core.geometry.projective

import androidx.compose.runtime.Immutable
import core.geometry.EPSILON
import core.geometry.EPSILON2
import core.geometry.conformal.GeneralizedCircle
import domain.squareDiff
import domain.squareSum
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// MAYBE: interface AConic and point, line, ellipse etc inheriting from it

// see GA(5,3,0)
// i want to unify points, [double] lines, ellipses, hyperbolas, parabolas and
// other degenerate conics
// note that 2D CGA is a sub-algebra of GAC
/**
 * Geometric Algebra of Conics (GAC) representation = G(5,3)
 *
 * p = plus, m = minus, c = cross;
 * d = dual
 *
 * dual basis vectors square to -1, all others to +1
 *
 * Equivalent matrix representation (times -1):
 * ```
 *   pd + md      cd        -x
 *      cd     pd - md      -y
 *     -x        -y         2*p
 * ```
 */
@Immutable
@Serializable
data class Conic(
    val dp: Double,
    val dm: Double,
    val dc: Double,
    val x: Double,
    val y: Double,
    val p: Double,
    val m: Double = 0.0,
    val c: Double = 0.0,
) {
    @Immutable
    @Serializable
    enum class Type {
        ELLIPSE, HYPERBOLA,
        PARABOLA,
        // over the complex plane degenerates are either 2 intersecting lines or double line
        TWO_INTERSECTING_LINES, TWO_PARALLEL_LINE, DOUBLE_LINE,
        POINT, // aka 2 imaginary lines
        EMPTY,
    }

    /** can be negative */
    inline val norm2: Double get() =
        -2*dp*p - 2*dm*m - 2*dc*c + x*x + y*y

    /** non-negative */
    inline val norm: Double get() =
        sqrt(abs(norm2))

    // ellipse, parabola, hyperbola discriminator
    val m2det: Double = run {
        val A = dp + dm
        val B = dc
        val C = dp - dm
        A*C - B*B
    }
    // degenerate conic discriminator
    val m3det: Double = run {
        val A = dp + dm
        val B = dc
        val C = dp - dm
        val D = -x
        val E = -y
        val F = 2*p
        A*(C*F - E*E) - B*(B*F - E*D) + D*(B*E - C*D)
    }
    val isDegenerate: Boolean = abs(m3det) < EPSILON
    val type: Type = when {
        isDegenerate -> when {
            abs(m2det) < EPSILON -> {
                val s = x*x + y*y - 4*dp*p // D*D + E*E - (A + C)*F
                when {
                    abs(s) < EPSILON ->
                        if (x == 0.0 && y == 0.0)
                            Type.EMPTY
                        else
                            Type.DOUBLE_LINE
                    s > 0.0 -> Type.TWO_PARALLEL_LINE
                    else -> Type.EMPTY
                }
            }
            m2det > 0.0 -> Type.POINT // degenerate ellipse
            else -> Type.TWO_INTERSECTING_LINES // degenerate hyperbola
        }
        else -> when {
            abs(m2det) < EPSILON -> Type.PARABOLA
            m2det > 0.0 -> Type.ELLIPSE
            else -> Type.HYPERBOLA
        }
    }

    operator fun get(index: Int): Double = when (index) {
        0 -> dp
        1 -> dm
        2 -> dc
        3 -> x
        4 -> y
        5 -> p
        6 -> m
        7 -> c
        else -> throw IndexOutOfBoundsException("Conic component $this[$index] doesn't exist")
    }

    // NOTE: since we assume GeneralizedCircle to be normalized, be careful with these
    operator fun times(k: Double): Conic {
        return Conic(k*dp, k*dm, k*dc, k*x, k*y, k*p, k*m, k*c)
    }

    fun normalized(): Conic {
        val n = norm
        val k = 1.0/n
        if (n < EPSILON) {
            // idk, degenerate conic, or smth even more degenerate
            return this
        } else {
            return Conic(
                dp = roundEpsilonToZero(k*dp),
                dm = roundEpsilonToZero(k*dm),
                dc = roundEpsilonToZero(k*dc),
                x = roundEpsilonToZero(k*x),
                y = roundEpsilonToZero(k*y),
                p = roundEpsilonToZero(k*p),
                m = roundEpsilonToZero(k*m),
                c = roundEpsilonToZero(k*c),
            )
        }
    }

    // c|p < 0 => p inside c?
    /** conic.innerProduct(point) = 0 => the point lies on the conic */
    infix fun innerProduct(v: Conic): Double =
        -(dp*v.p + p*v.dp + dm*v.m + m*v.dm + dc*v.c + c*v.dc) + x*v.x + y*v.y

    // -> Point(x/dp, y/dp)
    // -> Line(x, y, -p)
    inline fun <reified R> fold(
        crossinline onPoint: (PPoint) -> R,
        crossinline onLine: (PLine) -> R,
        // on ellipse/hyperbola/parabola
        crossinline onEmpty: () -> R,
    ): R = TODO("when type")

    companion object {
        private inline fun isNear0(
            dp: Double, dm: Double, dc: Double,
            x: Double, y: Double,
            p: Double, // m,c can be ignored
        ): Boolean =
            abs(dp) < EPSILON2 && abs(dm) < EPSILON2 && abs(dc) < EPSILON2 &&
            abs(x) < EPSILON2 && abs(y) < EPSILON2 &&
            abs(p) < EPSILON2

        private inline fun areValidHomogenousCoordinates(
            dp: Double, dm: Double, dc: Double,
            x: Double, y: Double,
            p: Double, m: Double = 0.0, c: Double = 0.0
        ): Boolean =
            dp.isFinite() && dm.isFinite() && dc.isFinite() &&
            x.isFinite() && y.isFinite() &&
            p.isFinite() && m.isFinite() && c.isFinite() &&
            // (0,0,0,0,0,0,0,0) is invalid in homogenous
            (dp != 0.0 || dm != 0.0 || dc != 0.0 || x != 0.0 || y != 0.0 || p != 0.0)

        fun fromPointXY(x: Double, y: Double): Conic = Conic(
            dp = 1.0,
            dm = 0.0,
            dc = 0.0,
            x = x,
            y = y,
            p = 0.5*(x*x + y*y),
            m = 0.5*(x*x - y*y),
            c = x*y,
        )
        // problem: w=0 point lies on any line
        fun fromPoint(point: PPoint): Conic = Conic(
            dp = point.w.toDouble(), // ideally w squared
            dm = 0.0,
            dc = 0.0,
            x = point.w * point.x,
            y = point.w * point.y,
            p = 0.5*(squareSum(point.x, point.y)),
            m = 0.5*(squareDiff(point.x, point.y)),
            c = point.x*point.y,
        )
        private inline fun fromGeneric(
            alpha: Double, beta: Double,
            x: Double = 0.0, y: Double = 0.0,
            angle: Double = 0.0,
        ): Conic {
            val s = sin(2*angle)
            val c = cos(2*angle)
            return Conic(
                dp = 1.0,
                dm = -alpha*c,
                dc = -alpha*s,
                x = x + x*alpha*c - y*alpha*s,
                y = y + y*alpha*c - x*alpha*s,
                p = 0.5*(x*x + y*y - beta - (x*x - y*y)*alpha*c - 2*x*y*alpha*s),
                m = 0.0,
                c = 0.0,
            )
        }
        fun from2Lines(line1: PLine, line2: PLine): Conic = TODO()
        fun fromLine(line: PLine): Conic =
            from2Lines(line, line)
        // what about the line at infinity
        /** Line `a*x + b*y + c = 0` */
        fun fromLine(a: Double, b: Double, c: Double): Conic = Conic(
            dp = 0.0,
            dm = 0.0,
            dc = 0.0,
            x = a,
            y = b,
            p = -c,
            m = 0.0,
            c = 0.0,
        )
        fun fromParallelLines(
            centerX: Double, centerY: Double,
            halfDistance: Double,
            tiltAngle: Double,
        ): Conic = fromGeneric(-1.0, 2*halfDistance*halfDistance, centerX, centerY, tiltAngle)
        /** @param[k] tan(angle between the lines) */
        fun fromIntersectingLines(
            k: Double,
            centerX: Double, centerY: Double,
            tiltAngle: Double,
        ): Conic = fromGeneric((1+k*k)/(1-k*k), 0.0, centerX, centerY, tiltAngle)
        // non-unique repr, cuz symmetry with tilt (mod pi/2), maybe related to some kind of orientation
        fun fromPerpendicularLines(
            x: Double, y: Double,
            tiltAngle: Double,
        ): Conic {
            val s = sin(2*tiltAngle)
            val c = cos(2*tiltAngle)
            return Conic(
                dp = 0.0,
                dm = c,
                dc = -s,
                x = x*c - y*s,
                y = y*c - x*s,
                p = -0.5*((x*x - y*y)*c + 2*x*y*s),
                m = 0.0,
                c = 0.0,
            )
        }
        // axis-aligned ellipse at origin with semi-axis a,b:
        // -a^2*b^2*e_p + (a^2 + b^2)*e_pd + (a^2 - b^2)*e_md
        // axis-aligned hyperbola at origin with semi-axis a,b:
        // +a^2*b^2*e_p + (a^2 + b^2)*e_pd + (a^2 - b^2)*e_md
        // axis-aligned parabola with semi-latus rectum p:
        // p*e_x + e_pd + e_md
        fun fromCircle(
            x: Double, y: Double,
            radius: Double,
        ): Conic = Conic(
            dp = 1.0,
            dm = 0.0,
            dc = 0.0,
            x = x, y = y,
            p = 0.5*(x*x + y*y - radius*radius),
            m = 0.0,
            c = 0.0,
        )
        /** mb swap minor <-> major
         * @param[tiltAngle] counterclockwise axis tilt angle in radians
         */
        fun fromEllipse(
            semiMinorAxis: Double, semiMajorAxis: Double,
            centerX: Double = 0.0, centerY: Double = 0.0,
            tiltAngle: Double = 0.0,
        ): Conic {
            val a2 = semiMinorAxis*semiMinorAxis
            val b2 = semiMajorAxis*semiMajorAxis
            val d = a2 + b2
            val alpha = (a2 - b2)/d
            val beta = 2*a2*b2/d
            return fromGeneric(alpha, beta, centerX, centerY, tiltAngle)
        }
        /** mb swap minor <-> major
         * @param[tiltAngle] counterclockwise axis tilt angle in radians
         */
        fun fromHyperbola(
            semiMinorAxis: Double, semiMajorAxis: Double,
            centerX: Double = 0.0, centerY: Double = 0.0,
            tiltAngle: Double = 0.0,
        ): Conic {
            val a2 = semiMinorAxis*semiMinorAxis
            val b2 = semiMajorAxis*semiMajorAxis
            val d = a2 - b2
            val alpha = (a2 + b2)/d
            val beta = -2*a2*b2/d
            return fromGeneric(alpha, beta, centerX, centerY, tiltAngle)
        }
        /** mb swap minor <-> major
         * @param[p] semi-latus rectum
         * @param[x] x-coordinate of the parabola center
         * @param[y] y-coordinate of the parabola center
         * @param[angle] counterclockwise axis tilt angle in radians
         */
        fun fromParabola(
            p: Double,
            x: Double = 0.0, y: Double = 0.0,
            angle: Double = 0.0,
        ): Conic {
            val s = sin(2*angle)
            val c = cos(2*angle)
            return Conic(
                dp = 1.0,
                dm = c,
                dc = s,
                x = x + x*c + y*s - 2*p*sin(angle),
                y = y - y*c + x*s + 2*p*cos(angle),
                p = 0.5*(x*x + y*y + (x*x - y*y)*c + 2*x*y*s - 4*p*x*sin(angle) + 4*p*y*cos(angle)),
                m = 0.0,
                c = 0.0,
            )
        }
        // ellipse/hyperbola from rotated rectangle
        // ellipse/hyperbola from 2 focuses and a point
        // parabola by directrix and focus

        // for axis-aligned conic by 4 points c5=e_c
        // for circle by 3 points c4=e_m, c5=e_c
        // for line by 2 points c3=e_p, c4=e_m, c5=e_c
        fun wedge(A: Conic, B: Conic, C: Conic, D: Conic, E: Conic): Conic? {
            // Laplace factorization E,D -> C -> B -> A
            // 2x2 det d_ij = Di*Ej - Dj*Ei
            val d01 = D.dp*E.dm - D.dm*E.dp
            val d02 = D.dp*E.dc - D.dc*E.dp
            val d03 = D.dp*E.x - D.x*E.dp
            val d04 = D.dp*E.y - D.y*E.dp
            val d05 = D.dp*E.p - D.p*E.dp
            val d12 = D.dm*E.dc - D.dc*E.dm
            val d13 = D.dm*E.x - D.x*E.dm
            val d14 = D.dm*E.y - D.y*E.dm
            val d15 = D.dm*E.p - D.p*E.dm
            val d23 = D.dc*E.x - D.x*E.dc
            val d24 = D.dc*E.y - D.y*E.dc
            val d25 = D.dc*E.p - D.p*E.dc
            val d34 = D.x*E.y - D.y*E.x
            val d35 = D.x*E.p - D.p*E.x
            val d45 = D.y*E.p - D.p*E.y
            // 3x3 det d_ijk = Ci*d_jk - Cj*d_ik + Ck*d_ij
            val d012 = C.dp*d12 - C.dm*d02 + C.dc*d01
            val d013 = C.dp*d13 - C.dm*d03 + C.x*d01
            val d014 = C.dp*d14 - C.dm*d04 + C.y*d01
            val d015 = C.dp*d15 - C.dm*d05 + C.p*d01
            val d023 = C.dp*d23 - C.dc*d03 + C.x*d02
            val d024 = C.dp*d24 - C.dc*d04 + C.y*d02
            val d025 = C.dp*d25 - C.dc*d05 + C.p*d02
            val d034 = C.dp*d34 - C.x*d04 + C.y*d03
            val d035 = C.dp*d35 - C.x*d05 + C.p*d03
            val d045 = C.dp*d45 - C.y*d05 + C.p*d04
            val d123 = C.dm*d23 - C.dc*d13 + C.x*d12
            val d124 = C.dm*d24 - C.dc*d14 + C.y*d12
            val d125 = C.dm*d25 - C.dc*d15 + C.p*d12
            val d134 = C.dm*d34 - C.x*d14 + C.y*d13
            val d135 = C.dm*d35 - C.x*d15 + C.p*d13
            val d145 = C.dm*d45 - C.y*d15 + C.p*d14
            val d234 = C.dc*d34 - C.x*d24 + C.y*d23
            val d235 = C.dc*d35 - C.x*d25 + C.p*d23
            val d245 = C.dc*d45 - C.y*d25 + C.p*d24
            val d345 = C.x*d45 - C.y*d35 + C.p*d34
            // 4x4 det d_ijkl = Bi*d_jkl - Bj*d_ikl + Bk*d_ijl - Bl*d_ijk
            val d0123 = B.dp*d123 - B.dm*d023 + B.dc*d013 - B.x*d012
            val d0124 = B.dp*d124 - B.dm*d024 + B.dc*d014 - B.y*d012
            val d0125 = B.dp*d125 - B.dm*d025 + B.dc*d015 - B.p*d012
            val d0134 = B.dp*d134 - B.dm*d034 + B.x*d014 - B.y*d013
            val d0135 = B.dp*d135 - B.dm*d035 + B.x*d015 - B.p*d013
            val d0145 = B.dp*d145 - B.dm*d045 + B.y*d015 - B.p*d014
            val d0234 = B.dp*d234 - B.dc*d034 + B.x*d024 - B.y*d023
            val d0235 = B.dp*d235 - B.dc*d035 + B.x*d025 - B.p*d023
            val d0245 = B.dp*d245 - B.dc*d045 + B.y*d025 - B.p*d024
            val d0345 = B.dp*d345 - B.x*d045 + B.y*d035 - B.p*d034
            val d1234 = B.dm*d234 - B.dc*d134 + B.x*d124 - B.y*d123
            val d1235 = B.dm*d235 - B.dc*d135 + B.x*d125 - B.p*d123
            val d1245 = B.dm*d245 - B.dc*d145 + B.y*d125 - B.p*d124
            val d1345 = B.dm*d345 - B.x*d145 + B.y*d135 - B.p*d134
            val d2345 = B.dc*d345 - B.x*d245 + B.y*d235 - B.p*d234
            // 5x5 det d_ijklm = Ai*d_jklm - Aj*d_iklm + Ak*d_ijlm - Al*d_ijkm + Am*d_ijkl
            val d01234 = A.dp*d1234 - A.dm*d0234 + A.dc*d0134 - A.x*d0124 + A.y*d0123
            val d01235 = A.dp*d1235 - A.dm*d0235 + A.dc*d0135 - A.x*d0125 + A.p*d0123
            val d01245 = A.dp*d1245 - A.dm*d0245 + A.dc*d0145 - A.y*d0125 + A.p*d0124
            val d01345 = A.dp*d1345 - A.dm*d0345 + A.x*d0145 - A.y*d0135 + A.p*d0134
            val d02345 = A.dp*d2345 - A.dc*d0345 + A.x*d0245 - A.y*d0235 + A.p*d0234
            val d12345 = A.dm*d2345 - A.dc*d1345 + A.x*d1245 - A.y*d1235 + A.p*d1234
            if (isNear0(d12345, d02345, d01345, d01245, d01235, d01234))
                return null
            return if (areValidHomogenousCoordinates(d12345, d02345, d01345, d01245, d01235, d01234))
                // signs might need adjustment
                Conic(d12345, d02345, d01345, d01245, d01235, d01234).normalized()
            else
                null
        }

        // 4-points by 4 points... pencil core/dual conic intersection
        fun wedge(c1: Conic, c2: Conic, c3: Conic, c4: Conic): Quadruplet =
            TODO("pencil quadruplet")

        fun wedge(A: Conic, B: Conic): Quadruplet = Quadruplet(
            // .uv = A.u*B.v - A.v*B.u
            dpdm = A.dp*B.dm - A.dm*B.dp,
            dpdc = A.dp*B.dc - A.dc*B.dp,
            dpx = A.dp*B.x - A.x*B.dp,
            dpy = A.dp*B.y - A.y*B.dp,
            dpp = A.dp*B.p - A.p*B.dp,
            dmdc = A.dm*B.dc - A.dc*B.dm,
            dmx = A.dm*B.x - A.x*B.dm,
            dmy = A.dm*B.y - A.y*B.dm,
            dmp = A.dm*B.p - A.p*B.dm,
            dcx = A.dc*B.x - A.x*B.dc,
            dcy = A.dc*B.y - A.y*B.dc,
            dcp = A.dc*B.p - A.p*B.dc,
            xy = A.x*B.y - A.y*B.x,
            xp = A.x*B.p - A.p*B.x,
            yp = A.y*B.p - A.p*B.y,
        )
    }
}

/** if abs([x]) < [EPSILON] return 0.0 */
private inline fun roundEpsilonToZero(x: Double): Double =
    if (abs(x) < EPSILON)
        0.0
    else x