package domain

import androidx.compose.ui.geometry.Offset
import data.kmath_complex.Complex
import kotlin.jvm.JvmName
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Rotates the given offset around the origin by the given angle in degrees.
 *
 * A positive angle indicates a counterclockwise rotation around the right-handed 2D Cartesian
 * coordinate system.
 *
 * See: [Rotation matrix](https://en.wikipedia.org/wiki/Rotation_matrix)
 */
fun Offset.rotateBy(angle: Float): Offset {
    val angleInRadians = angle * PI / 180
    return Offset(
        (x * cos(angleInRadians) - y * sin(angleInRadians)).toFloat(),
        (x * sin(angleInRadians) + y * cos(angleInRadians)).toFloat()
    )
}

fun Offset.rotateBy(angleCos: Double, angleSin: Double): Offset {
    return Offset(
        (x * angleCos - y * angleSin).toFloat(),
        (x * angleSin + y * angleCos).toFloat()
    )
}

fun Offset.angleDeg(other: Offset): Float =
    (atan2(
        x*other.y - y*other.x,
        x*other.x + y*other.y
    ) * 180/PI).toFloat()

fun Offset.angleCos(other: Offset): Double {
    return (x.toDouble()*other.x + y*other.y)/getDistance()/other.getDistance()
}

fun Offset.angleSin(other: Offset): Double {
    return (x.toDouble()*other.y - y*other.x)/getDistance()/other.getDistance()
}

fun Offset.toComplex(): Complex =
    Complex(x, y)

@JvmName("averageOfOffset") fun Iterable<Offset>.average(): Offset {
    var n = 0
    var sumX = 0f
    var sumY = 0f
    for ((x, y) in this) {
        n += 1
        sumX += x
        sumY += y
    }
    return if (n == 0)
        Offset.Zero
    else
        Offset(sumX/n, sumY/n)
}