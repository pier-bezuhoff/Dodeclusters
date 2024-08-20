package data.geometry

import androidx.compose.ui.geometry.Offset
import kotlinx.serialization.Serializable
import kotlin.math.atan2
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

    fun middle(point: Point): Point =
        if (this == CONFORMAL_INFINITY || point == CONFORMAL_INFINITY) CONFORMAL_INFINITY
        else Point((x + point.x)/2, (y + point.y)/2)

    companion object {
        /** All lines pass through this point, it's a stereographic projection of the North pole */
        val CONFORMAL_INFINITY = Point(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY)

        fun fromOffset(offset: Offset): Point =
            Point(offset.x.toDouble(), offset.y.toDouble())
    }
}

/** CCW angle from [start] to [end] in (-[PI]; [PI]] */
fun calculateAngle(center: Point, start: Point, end: Point): Double {
    val v1x = start.x - center.x
    val v1y = start.y - center.y
    val v2x = end.x - center.x
    val v2y = end.y - center.y
    return atan2(
        v1x*v2y - v1y*v2x,
        v1x*v2x + v1y*v2y
    )
}