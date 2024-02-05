package utils

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
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

fun Offset.angleCos(other: Offset): Double {
    return (x.toDouble()*other.x + y*other.y)/getDistance()/other.getDistance()
}

fun Offset.angleSin(other: Offset): Double {
    return (x.toDouble()*other.y - y*other.x)/getDistance()/other.getDistance()
}
