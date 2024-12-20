package data.geometry

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isFinite
import domain.rotateBy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.atan2
import kotlin.math.hypot

@Serializable
@SerialName("point")
@Immutable
data class Point(
    val x: Double,
    val y: Double
) : GCircle {
    fun toOffset(): Offset =
        if (this == CONFORMAL_INFINITY)
            Offset.Infinite
        else
            Offset(x.toFloat(), y.toFloat())

    fun distanceFrom(point: Point): Double =
        when {
            this == CONFORMAL_INFINITY && point == CONFORMAL_INFINITY -> 0.0
            this == CONFORMAL_INFINITY && point != CONFORMAL_INFINITY -> Double.POSITIVE_INFINITY
            this != CONFORMAL_INFINITY && point == CONFORMAL_INFINITY -> Double.POSITIVE_INFINITY
            else ->
                hypot(point.x - x, point.y - y)
        }

    fun translate(vector: Offset): Point =
        Point(x + vector.x, y + vector.y)

    fun scale(focus: Offset, zoom: Float): Point =
        if (this == CONFORMAL_INFINITY)
            CONFORMAL_INFINITY
        else
            fromOffset((toOffset() - focus) * zoom + focus)

    override fun scale(focusX: Double, focusY: Double, zoom: Double): Point {
        val newX = (x - focusX) * zoom + focusX
        val newY = (y - focusY) * zoom + focusY
        return if (this == CONFORMAL_INFINITY)
            CONFORMAL_INFINITY
        else
            Point(newX, newY)
    }

    fun rotate(focus: Offset, angleDeg: Float): Point {
        val newOffset = (toOffset() - focus).rotateBy(angleDeg) + focus
        return fromOffset(newOffset)
    }

    fun middle(point: Point): Point =
        if (this == CONFORMAL_INFINITY || point == CONFORMAL_INFINITY) CONFORMAL_INFINITY
        else Point((x + point.x)/2, (y + point.y)/2)

    companion object {
        /** All lines pass through this point, it's a stereographic projection of the North pole */
        val CONFORMAL_INFINITY = Point(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY)

        fun fromOffset(offset: Offset): Point =
            if (offset.isFinite)
                Point(offset.x.toDouble(), offset.y.toDouble())
            else CONFORMAL_INFINITY
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