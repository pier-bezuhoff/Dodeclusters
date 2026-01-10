package core.geometry.projective

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlin.math.cos
import kotlin.math.sin

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
 * Equivalent matrix representation:
 * ```
 *   pd + md      cd        -x
 *      cd     pd - md      -y
 *     -x        -y         2*p
 * ```
 */
@Immutable
@Serializable
data class Conic(
    val x: Double,
    val y: Double,
    val p: Double,
    val pd: Double,
    val m: Double,
    val md: Double,
    val c: Double,
    val cd: Double,
) {
    @Immutable
    @Serializable
    enum class Type {
        ELLIPSE, HYPERBOLA,
        PARABOLA,
        DOUBLE_LINE, TWO_LINES,
        POINT, // aka 2 imaginary lines
        EMPTY,
    }

    val type: Type = TODO()
    val isDegenerate: Boolean = TODO()

    inline fun <reified R> fold(
        crossinline onPoint: (PPoint) -> R,
        crossinline onLine: (PLine) -> R,
        // on ellipse/hyperbola/parabola
        crossinline onEmpty: () -> R,
    ): R = TODO("when type")

    companion object {
        fun fromPointXY(x: Double, y: Double): Conic = Conic(
            x = x, y = y,
            p = 0.5*(x*x + y*y),
            pd = 1.0,
            m = 0.5*(x*x - y*y),
            md = 0.0,
            c = x*y,
            cd = 0.0
        )
        private inline fun fromGeneric(
            alpha: Double, beta: Double,
            x: Double = 0.0, y: Double = 0.0,
            angle: Double = 0.0,
        ): Conic {
            val s = sin(2*angle)
            val c = cos(2*angle)
            return Conic(
                x = x + x*alpha*c - y*alpha*s,
                y = y + y*alpha*c - x*alpha*s,
                p = 0.5*(x*x + y*y - beta - (x*x - y*y)*alpha*c - 2*x*y*alpha*s),
                pd = 1.0,
                m = 0.0,
                md = -alpha*c,
                c = 0.0,
                cd = -alpha*s,
            )
        }
        fun fromPoint(point: PPoint): Conic = TODO()
        fun from2Lines(line1: PLine, line2: PLine): Conic = TODO()
        fun fromLine(line: PLine): Conic =
            from2Lines(line, line)
        /** Line `a*x + b*y + c = 0` */
        fun fromLine(a: Double, b: Double, c: Double): Conic = Conic(
            x = a, y = b,
            p = -c,
            pd = 0.0, m = 0.0, md = 0.0, c = 0.0, cd = 0.0,
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
        fun fromPerpendicularLines(
            x: Double, y: Double,
            tiltAngle: Double,
        ): Conic {
            val s = sin(2*tiltAngle)
            val c = cos(2*tiltAngle)
            return Conic(
                x = x*c - y*s,
                y = y*c - x*s,
                p = -0.5*((x*x - y*y)*c + 2*x*y*s),
                pd = 0.0,
                m = 0.0,
                md = c,
                c = 0.0,
                cd = -s,
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
            x = x, y = y,
            p = 0.5*(x*x + y*y - radius*radius),
            pd = 1.0,
            m = 0.0, md = 0.0, c = 0.0, cd = 0.0,
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
            val beta = (2*a2*b2)/d
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
            val beta = -(2*a2*b2)/d
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
                x = x + x*c + y*s - 2*p*sin(angle),
                y = y - y*c + x*s + 2*p*cos(angle),
                p = 0.5*(x*x + y*y + (x*x - y*y)*c + 2*x*y*s - 4*p*x*sin(angle) + 4*p*y*cos(angle)),
                pd = 1.0,
                m = 0.0,
                md = c,
                c = 0.0,
                cd = s,
            )
        }
        // from 5 points
        // from 5 conics !?
        // ellipse/hyperbola from rotated rectangle
        // ellipse/hyperbola from 2 focuses and a point
        // parabola by directrix and focus

        // for axis-aligned conic by 4 points c5=e_c
        // for circle by 3 points c4=e_m, c5=e_c
        // for line by 2 points c3=e_p, c4=e_m, c5=e_c
        fun wedge(c1: Conic, c2: Conic, c3: Conic, c4: Conic, c5: Conic): Conic =
            TODO("wedge -> outer product repr -> *e_pd*e_md*e_cd*e_x*e_y*e_p")
        fun wedge(c1: Conic, c2: Conic, c3: Conic, c4: Conic): Quadruplet =
            TODO("pencil quadruplet")
        fun wedge(c1: Conic, c2: Conic): Quadruplet =
            TODO("intersection quadruplet")
    }
}