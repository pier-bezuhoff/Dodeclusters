package core.geometry

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isFinite
import domain.radians
import domain.squareSum
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * 2D Point
 *
 * NOTE: JSON doesn't support [Double.POSITIVE_INFINITY] by default, remember to add
 *  `Json { allowSpecialFloatingPointValues = true }` to serializers
 *
 * NOTE: do NOT forget [Point.CONFORMAL_INFINITY] checks
 */
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

    val isFinite: Boolean get() =
        x.isFinite() && y.isFinite()

    inline val isInfinite: Boolean get() =
        this == CONFORMAL_INFINITY

    fun toOffset(): Offset =
        if (isInfinite)
            Offset.Infinite
        else
            Offset(x.toFloat(), y.toFloat())

    override fun distanceFrom(point: Point): Double = when {
        this.isInfinite && point.isInfinite -> 0.0
        this.isInfinite && point.isFinite -> Double.POSITIVE_INFINITY
        this.isFinite && point.isInfinite -> Double.POSITIVE_INFINITY
        else -> hypot(point.x - x, point.y - y)
    }

    override fun distanceFrom(x: Double, y: Double): Double =
        if (this.isInfinite)
            Double.POSITIVE_INFINITY
        else
            hypot(this.x - x, this.y - y)

    override fun distanceFrom(point: Offset): Double =
        if (this.isInfinite)
            Double.POSITIVE_INFINITY
        else
            hypot(x - point.x, y - point.y)

    fun distance2From(x: Double, y: Double): Double =
        if (isInfinite)
            Double.POSITIVE_INFINITY
        else
            squareSum(this.x - x, this.y - y)

    fun distance2From(point: Point): Double = when {
        this.isInfinite && point.isInfinite -> 0.0
        this.isInfinite && point.isFinite -> Double.POSITIVE_INFINITY
        this.isFinite && point.isInfinite -> Double.POSITIVE_INFINITY
        else -> squareSum(point.x - x, point.y - y)
    }

    override fun translated(vector: Offset): Point =
        if (isInfinite)
            CONFORMAL_INFINITY
        else
            Point(x + vector.x, y + vector.y)

    override fun translated(dx: Double, dy: Double): Point =
        if (isInfinite)
            CONFORMAL_INFINITY
        else
            Point(x + dx, y + dy)

    fun scaled(focus: Offset, zoom: Float): Point =
        if (isInfinite)
            CONFORMAL_INFINITY
        else
            Point(
                (x - focus.x) * zoom + focus.x,
                (y - focus.y) * zoom + focus.y,
            )

    override fun scaled(focusX: Double, focusY: Double, zoom: Double): Point =
        if (isInfinite)
            CONFORMAL_INFINITY
        else Point(
            (x - focusX) * zoom + focusX,
            (y - focusY) * zoom + focusY,
        )

    override fun rotated(focusX: Double, focusY: Double, angleInRadians: Double): Point =
        if (isInfinite)
            CONFORMAL_INFINITY
        else { // cmp with Offset.rotateBy
            val x0 = x - focusX
            val y0 = y - focusY
            val cosine = cos(angleInRadians)
            val sine = sin(angleInRadians)
            Point(
                (x0 * cosine - y0 * sine) + focusX,
                (x0 * sine + y0 * cosine) + focusY,
            )
        }

    fun rotated(focus: Offset, angleDeg: Float): Point =
        if (isInfinite)
            CONFORMAL_INFINITY
        else { // cmp with Offset.rotateBy
            val focusX = focus.x
            val focusY = focus.y
            val x0 = x - focusX
            val y0 = y - focusY
            val angle: Double = angleDeg.radians
            val cosine = cos(angle)
            val sine = sin(angle)
            Point(
                (x0 * cosine - y0 * sine) + focusX,
                (x0 * sine + y0 * cosine) + focusY,
            )
        }

    override fun transformed(
        translation: Offset,
        focus: Offset,
        zoom: Float,
        rotationAngle: Float
    ): Point {
        if (isInfinite)
            return CONFORMAL_INFINITY
        var newX: Double = x + translation.x
        var newY: Double = y + translation.y
        if (focus != Offset.Unspecified) {
            val (focusX, focusY) = focus
            // cmp. Offset.rotateBy & zoom and rotation are commutative
            val dx = newX - focusX
            val dy = newY - focusY
            val angle: Double = rotationAngle.radians
            val cosine = cos(angle)
            val sine = sin(angle)
            newX = (dx * cosine - dy * sine) * zoom + focusX
            newY = (dx * sine + dy * cosine) * zoom + focusY
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
        if (isInfinite)
            return CONFORMAL_INFINITY
        var newX: Double = x + translationX
        var newY: Double = y + translationY
        // cmp. Offset.rotateBy & zoom and rotation are commutative
        val dx = newX - focusX
        val dy = newY - focusY
        val angle: Double = rotationAngle.radians
        val cosine = cos(angle)
        val sine = sin(angle)
        newX = (dx * cosine - dy * sine) * zoom + focusX
        newY = (dx * sine + dy * cosine) * zoom + focusY
        return Point(newX, newY)
    }

    /** = `(this + point)/2` */
    infix fun middle(point: Point): Point =
        if (this.isInfinite || point.isInfinite)
            CONFORMAL_INFINITY
        else Point((x + point.x)/2, (y + point.y)/2)

    /** dot product `this->p1 ⋅ this->p2` */
    inline fun dot(p1: Point, p2: Point): Double =
        (p1.x - x)*(p2.x - x) + (p1.y - y)*(p2.y - y)

    /** cross product `this->p1 x this->p2`, projected on Oz */
    inline fun cross(p1: Point, p2: Point): Double {
        // we negate the usual `dx1*dy2 - dx2*dy1` formula cuz of left-hand-ness
        return -(p1.x - x)*(p2.y - y) + (p1.y - y)*(p2.x - x)
    }

    companion object {
        /** All lines pass through this point, it's a stereographic projection of the North pole */
        val CONFORMAL_INFINITY =
            Point(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY)

        fun fromOffset(offset: Offset): Point =
            if (offset.isFinite)
                Point(offset.x.toDouble(), offset.y.toDouble())
            else CONFORMAL_INFINITY

        /** CCW angle from [start] to [end] in `[-PI; PI]` */
        fun calculateAngle(center: Point, start: Point, end: Point): Double {
            if (start.isInfinite || center.isInfinite || end.isInfinite)
                return 0.0
            val v1x = start.x - center.x
            val v1y = start.y - center.y
            val v2x = end.x - center.x
            val v2y = end.y - center.y
            return atan2(
                v1x*v2y - v1y*v2x,
                v1x*v2x + v1y*v2y
            )
        }

        /** dot product `p1->p2 ⋅ p3->p4` */
        inline fun dot(p1: Point, p2: Point, p3: Point, p4: Point): Double =
            (p2.x - p1.x)*(p4.x - p4.x) + (p2.y - p1.y)*(p4.y - p3.y)

        /** cross product `p1->p2 x p3->p4`, projected on Oz */
        inline fun cross(p1: Point, p2: Point, p3: Point, p4: Point): Double {
            // we negate the usual `dx1*dy2 - dx2*dy1` formula cuz of left-hand-ness
            return -(p2.x - p1.x)*(p4.y - p3.y) + (p2.y - p1.y)*(p4.x - p3.x)
        }

        /** counterclockwise -> true, clockwise -> false */
        fun calculateOrientation(p1: Point, p2: Point, p3: Point): Boolean {
            return p1.cross(p2, p3) >= 0.0
        }
    }
}