package data.geometry

import androidx.compose.ui.geometry.Offset
import kotlinx.serialization.Serializable

/** [a]*x + [b]*y + [c] = 0 */
@Serializable
data class Line(
    val a: Double,
    val b: Double,
    val c: Double
) : GCircle, CircleOrLine {
    companion object {
        fun lineBy2Points(p1: Offset, p2: Offset): Line {
            val dy = p2.y.toDouble() - p1.y
            val dx = p2.x.toDouble() - p1.x
            val c = p1.y*dx - p1.x*dy
            return Line(dy, -dx, c)
        }
    }
}