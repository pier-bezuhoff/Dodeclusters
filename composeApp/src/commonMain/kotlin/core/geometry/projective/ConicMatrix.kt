package core.geometry.projective

import androidx.compose.runtime.Immutable
import core.geometry.EPSILON
import kotlinx.serialization.Serializable
import kotlin.math.abs

@Immutable
@Serializable
data class ConicMatrix(
    val a: Double,
    val b: Double,
    val c: Double,
    val d: Double,
    val e: Double,
    val f: Double,
) {
    @Immutable
    @Serializable
    enum class Type {
        ELLIPSE, HYPERBOLA,
        PARABOLA,
        // over the complex plane degenerates are always 2 lines (maybe coinciding)
        TWO_LINES,
        POINT, // aka 2 imaginary lines
        EMPTY, // no real points
    }

    // characteristic polynomial of eigenvalue r:
    // chi(r) = r^3 - r^2 * trace + r * minorSum - m3det = 0
    /** Ellipse, parabola, hyperbola discriminator, aka how many points at infinity */
    val det2: Double =
        a*c - b*b // <0 hyperbola, ==0 parabola, >0 ellipse
    /** Degenerate conic discriminator = product of eigenvalues */
    val det: Double =
        a*(c*f - e*e) - b*(b*f - e*d) + d*(b*e - c*d)
    /** Sum of eigenvalues */
    val trace: Double =
        a + c + f
    /** `r1*r2 + r2*r3 + r3*r1` of eigenvalues ri */
    val minorSum: Double =
        a*c + a*f + c*f - b*b - e*e - d*d
    val isNonDegenerate: Boolean = abs(det) > EPSILON
    /** Is ellipse, hyperbola or parabola */
    val isGood: Boolean =
        isNonDegenerate &&
        (det > 0.0 && (trace <= 0.0 || minorSum <= 0.0) || // (+ - -) signature
        det < 0.0 && (trace >= 0.0 || minorSum <= 0.0)) // (+ + -) signature

    /** It is meaningful for an ellipse or hyperbola, for parabola it's the point in which
     * it touches the line at infinity. */
    val center: PPoint get() =
        PPoint(
            b*e - c*d,
            b*d - a*e,
            a*c - b*b,
        ) // M(center) = line at infinity => center = M.inv * (0 0 1)

    private fun inverse() {
        // 1/det times
        c*f - e*e; d*e - b*f; b*e - c*d
                 ; a*f - d*d; b*d - a*e
                            ; a*c - b*b
    }

    fun polar(point: PPoint): PLine =
        PLine(
            a*point.x + b*point.y + d*point.w,
            b*point.x + c*point.y + e*point.w,
            d*point.x + e*point.y + f*point.w,
        )

    fun pole(line: PLine): PPoint? {
        if (!isNonDegenerate)
            return null
        // MAYBE: ignore det
        // inverse matrix entries
        val a1 = (c*f - e*e)/det
        val b1 = (d*e - b*f)/det
        val c1 = (a*f - d*d)/det
        val d1 = (b*e - c*d)/det
        val e1 = (b*d - a*e)/det
        val f1 = (a*c - b*b)/det
        return PPoint(
            a1*line.a + b1*line.b + d1*line.c,
            b1*line.a + c1*line.b + e1*line.c,
            d1*line.a + e1*line.b + f1*line.c,
        )
    }

}