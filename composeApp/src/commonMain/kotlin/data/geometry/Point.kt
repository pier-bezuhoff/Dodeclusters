package data.geometry

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isFinite
import data.geometry.RegionPointLocation.BORDERING
import data.geometry.RegionPointLocation.IN
import data.geometry.RegionPointLocation.OUT
import domain.radians
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

// NOTE: JSON doesn't support Infinity, add `allowSpecialFloatingPointValues = true` to
//  Json { ... } serializers
// NOTE: do NOT forget CONFORMAL_INFINITY checks
@Immutable
@Serializable
@SerialName("point")
data class Point(
    val x: Double,
    val y: Double
) : LineOrPoint, CircleOrLineOrPoint, GCircle {

    init {
        require(
            x.isFinite() && y.isFinite() ||
            x.isInfinite() && y.isInfinite()
        ) { "Invalid Point($x, $y)" }
    }

    fun toOffset(): Offset =
        if (this == CONFORMAL_INFINITY)
            Offset.Infinite
        else
            Offset(x.toFloat(), y.toFloat())

    override fun distanceFrom(point: Point): Double =
        when {
            this == CONFORMAL_INFINITY && point == CONFORMAL_INFINITY -> 0.0
            this == CONFORMAL_INFINITY && point != CONFORMAL_INFINITY -> Double.POSITIVE_INFINITY
            this != CONFORMAL_INFINITY && point == CONFORMAL_INFINITY -> Double.POSITIVE_INFINITY
            else ->
                hypot(point.x - x, point.y - y)
        }

    override fun translated(vector: Offset): Point =
        if (this == CONFORMAL_INFINITY)
            CONFORMAL_INFINITY
        else
            Point(x + vector.x, y + vector.y)

    override fun translated(dx: Double, dy: Double): Point =
        if (this == CONFORMAL_INFINITY)
            CONFORMAL_INFINITY
        else
            Point(x + dx, y + dy)

    fun scaled(focus: Offset, zoom: Float): Point =
        if (this == CONFORMAL_INFINITY)
            CONFORMAL_INFINITY
        else
            Point(
                (x - focus.x) * zoom + focus.x,
                (y - focus.y) * zoom + focus.y,
            )

    override fun scaled(focusX: Double, focusY: Double, zoom: Double): Point =
        if (this == CONFORMAL_INFINITY)
            CONFORMAL_INFINITY
        else Point(
            (x - focusX) * zoom + focusX,
            (y - focusY) * zoom + focusY,
        )

    override fun rotated(focusX: Double, focusY: Double, angleInRadians: Double): Point =
        if (this == CONFORMAL_INFINITY)
            CONFORMAL_INFINITY
        else { // cmp with Offset.rotateBy
            val x0 = x - focusX
            val y0 = y - focusY
            val cosPhi = cos(angleInRadians)
            val sinPhi = sin(angleInRadians)
            Point(
                (x0 * cosPhi - y0 * sinPhi) + focusX,
                (x0 * sinPhi + y0 * cosPhi) + focusY,
            )
        }

    fun rotated(focus: Offset, angleDeg: Float): Point =
        if (this == CONFORMAL_INFINITY)
            CONFORMAL_INFINITY
        else { // cmp with Offset.rotateBy
            val focusX = focus.x
            val focusY = focus.y
            val x0 = x - focusX
            val y0 = y - focusY
            val phi: Double = angleDeg.radians
            val cosPhi = cos(phi)
            val sinPhi = sin(phi)
            Point(
                (x0 * cosPhi - y0 * sinPhi) + focusX,
                (x0 * sinPhi + y0 * cosPhi) + focusY,
            )
        }

    override fun transformed(translation: Offset, focus: Offset, zoom: Float, rotationAngle: Float): Point {
        if (this == CONFORMAL_INFINITY)
            return CONFORMAL_INFINITY
        var newX: Double = x + translation.x
        var newY: Double = y + translation.y
        if (focus != Offset.Unspecified) {
            val (focusX, focusY) = focus
            // cmp. Offset.rotateBy & zoom and rotation are commutative
            val dx = newX - focusX
            val dy = newY - focusY
            val phi: Double = rotationAngle.radians
            val cosPhi = cos(phi)
            val sinPhi = sin(phi)
            newX = (dx * cosPhi - dy * sinPhi) * zoom + focusX
            newY = (dx * sinPhi + dy * cosPhi) * zoom + focusY
        } // tbf because of T;S;R order it is not completely accurate
        return Point(newX, newY)
    }

    fun transformed(
        translationX: Double = 0.0,
        translationY: Double = 0.0,
        focusX: Double,
        focusY: Double,
        zoom: Double = 1.0,
        rotationAngle: Float = 0f,
    ): Point {
        if (this == CONFORMAL_INFINITY)
            return CONFORMAL_INFINITY
        var newX: Double = x + translationX
        var newY: Double = y + translationY
        // cmp. Offset.rotateBy & zoom and rotation are commutative
        val dx = newX - focusX
        val dy = newY - focusY
        val phi: Double = rotationAngle.radians
        val cosPhi = cos(phi)
        val sinPhi = sin(phi)
        newX = (dx * cosPhi - dy * sinPhi) * zoom + focusX
        newY = (dx * sinPhi + dy * cosPhi) * zoom + focusY
        return Point(newX, newY)
    }

    /** = `(this + point)/2` */
    fun middle(point: Point): Point =
        if (this == CONFORMAL_INFINITY || point == CONFORMAL_INFINITY)
            CONFORMAL_INFINITY
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

/** A point is either [IN] a region, [BORDERING] it or [OUT]side of it */
@Immutable
enum class RegionPointLocation {
    /** A point is inside of a region */
    IN,
    /** A point is on the border of a region */
    BORDERING,
    /** A point is outside of a region */
    OUT,
}

/** CCW angle from [start] to [end] in `[-PI; PI]` */
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