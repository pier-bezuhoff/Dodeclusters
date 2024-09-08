package data.geometry

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt

/**
 * CGA2D scalar + bivector
 *
 * Rotor = [s] +
 * [xy] * e_x ^ e_y + [xp] * e_x ^ e_plus + [xm] * e_x ^ e_minus +
 * [yp] * e_y ^ e_plus + [ym] * e_y ^ e_minus + [pm] * e_plus ^ e_minus +
 * 0 * e_x ^ e_y ^ e_plus ^ e_minus
 *
 * NB: can be not normalized, but `R*R.reversed = scalar`
 * */
@Serializable
@Immutable
data class Rotor(
    val s: Double,
    val xy: Double,
    val xp: Double,
    val xm: Double,
    val yp: Double,
    val ym: Double,
    val pm: Double,
    // MAYBE: add the pseudo-scalar part (orientation-altering)
) {
    // NOTE: in some cases [this]*[this] can contain non-scalar parts,
    //  norm2 = ([this]*[this]).grade(0)
    val norm2 get() =
        s.pow(2) - xy.pow(2) - xp.pow(2) + xm.pow(2) - yp.pow(2) + ym.pow(2) + pm.pow(2)

    fun normalized(): Rotor {
        val n2 = norm2
        return if (abs(n2) > EPSILON2) {
            this*(1.0/sqrt(abs(n2)))
        } else {
            this
        }
    }

    operator fun times(k: Double): Rotor =
        Rotor(s*k, xy*k, xp*k, xm*k, yp*k, ym*k, pm*k)

    fun reversed(): Rotor =
        times(-1.0).copy(s = this.s)

    /** `A.dual = A*I` where
     * `I = e_x * e_y * e_plus * e_minus` */
    fun dual(): Rotor {
        require(s == 0.0) {
            "since we don't store the pseudo-scalar component," +
            "we can only dualize *pure* bivectors. But $this contains non-zero scalar part"
        }
        return Rotor(
            0.0,
            pm, -ym, -yp,
            xm, xp, -xy
        )
    }

    // reference: "Geometric Algebra for Computer Science", page 185
    fun exp(): Rotor {
        if (setOf(s, xy, xp, xm, yp, ym, pm).all { it == 0.0 })
            return Rotor(1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        require(s == 0.0) { "Exponentiation requires pure grade=2 bivector" }
        val n2 = this.norm2
        val n = sqrt(abs(n2))
        return if (n < EPSILON) { // parabolic motion
            this.copy(s = 1.0)
        } else if (n2 < 0) { // elliptic motion
            (this * (sin(n)/n))
                .copy(s = cos(n))
        } else { // n2 > 0, hyperbolic motion
            (this * (sinh(n)/n))
                .copy(s = cosh(n))
        }
    }

    /** Applies [this] rotor to the [target] and then leaves only grade=1 component
     *
     * [this] * [target] * [this].reversed() */
    fun applyTo(target: GeneralizedCircle): GeneralizedCircle {
        val (w, x, y, z) = target
        val x1 = pm.pow(2)*x + pm*w*xm + pm*w*xp - 2*pm*xm*z + 2*pm*xp*z - s.pow(2)*x + s*w*xm + s*w*xp + 2*s*xm*z - 2*s*xp*z - 2*s*xy*y + w*xy*ym + w*xy*yp - x*xm.pow(2) + x*xp.pow(2) + x*xy.pow(2) + x*ym.pow(2) - x*yp.pow(2) - 2*xm*y*ym + 2*xp*y*yp + 2*xy*ym*z - 2*xy*yp*z
        val y1 = pm.pow(2)*y + pm*w*ym + pm*w*yp - 2*pm*ym*z + 2*pm*yp*z - s.pow(2)*y + s*w*ym + s*w*yp + 2*s*x*xy + 2*s*ym*z - 2*s*yp*z - w*xm*xy - w*xp*xy - 2*x*xm*ym + 2*x*xp*yp + xm.pow(2)*y - 2*xm*xy*z - xp.pow(2)*y + 2*xp*xy*z + xy.pow(2)*y - y*ym.pow(2) + y*yp.pow(2)
        val plus = pm.pow(2)*w/2 - pm.pow(2)*z + pm*s*w + 2*pm*s*z - 2*pm*x*xm - 2*pm*y*ym + s.pow(2)*w/2 - s.pow(2)*z + 2*s*x*xp + 2*s*y*yp - w*xm.pow(2)/2 - w*xm*xp - w*xp.pow(2)/2 + w*xy.pow(2)/2 - w*ym.pow(2)/2 - w*ym*yp - w*yp.pow(2)/2 - 2*x*xy*yp + xm.pow(2)*z - 2*xm*xp*z + xp.pow(2)*z + 2*xp*xy*y - xy.pow(2)*z + ym.pow(2)*z - 2*ym*yp*z + yp.pow(2)*z
        val minus = -pm.pow(2)*w/2 - pm.pow(2)*z - pm*s*w + 2*pm*s*z - 2*pm*x*xp - 2*pm*y*yp - s.pow(2)*w/2 - s.pow(2)*z + 2*s*x*xm + 2*s*y*ym - w*xm.pow(2)/2 - w*xm*xp - w*xp.pow(2)/2 - w*xy.pow(2)/2 - w*ym.pow(2)/2 - w*ym*yp - w*yp.pow(2)/2 - 2*x*xy*ym - xm.pow(2)*z + 2*xm*xp*z + 2*xm*xy*y - xp.pow(2)*z - xy.pow(2)*z - ym.pow(2)*z + 2*ym*yp*z - yp.pow(2)*z
        return -GeneralizedCircle(
            (minus - plus),
            x1,
            y1,
            (plus + minus)/2
        ).normalizedPreservingDirection()
    }

    companion object {
        /** = [a] ^ [b] */
        fun fromOuterProduct(a: GeneralizedCircle, b: GeneralizedCircle): Rotor {
            val (w1, x1, y1, z1) = a
            val (w2, x2, y2, z2) = b
            return Rotor(
                0.0,
                -x2*y1 + x1*y2,
                -w2*x1/2 + w1*x2/2 - x2*z1 + x1*z2,
                w2*x1/2 - w1*x2/2 - x2*z1 + x1*z2,
                -w2*y1/2 + w1*y2/2 - y2*z1 + y1*z2,
                w2*y1/2 - w1*y2/2 - y2*z1 + y1*z2,
                w2*z1 - w1*z2
            )
        }
    }
}