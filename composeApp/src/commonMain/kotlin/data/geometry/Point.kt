package data.geometry

import androidx.compose.ui.geometry.Offset
import kotlinx.serialization.Serializable
import kotlin.math.hypot

@Serializable
data class Point(
    val x: Double,
    val y: Double
) : GCircle {
    fun toOffset(): Offset =
        Offset(x.toFloat(), y.toFloat())

    fun distanceFrom(point: Point): Double =
        when {
            this == CONFORMAL_INFINITY && point == CONFORMAL_INFINITY -> 0.0
            this == CONFORMAL_INFINITY && point != CONFORMAL_INFINITY -> Double.POSITIVE_INFINITY
            this != CONFORMAL_INFINITY && point == CONFORMAL_INFINITY -> Double.POSITIVE_INFINITY
            else ->
                hypot(point.x - x, point.y - y)
        }

    companion object {
        /** All lines pass through this point, it's a stereographic projection of the North pole */
        val CONFORMAL_INFINITY = Point(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY)

        fun fromOffset(offset: Offset): Point =
            Point(offset.x.toDouble(), offset.y.toDouble())
    }
}