package data.geometry

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import data.geometry.Point.Companion.CONFORMAL_INFINITY
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

sealed interface CircleOrLineOrImaginaryCircle : GCircle

@Immutable
@Serializable
@SerialName("imaginary-circle")
data class ImaginaryCircle(
    val x: Double,
    val y: Double,
    val radius: Double,
) : CircleOrLineOrImaginaryCircle, GCircle {

    init {
        require(
            x.isFinite() && y.isFinite() && radius.isFinite() && radius > 0.0
        ) { "Invalid ImaginaryCircle($x, $y, $radius)" }
    }

    fun toRealCircle(): Circle =
        Circle(x, y, radius)

    override fun translated(vector: Offset): ImaginaryCircle =
        copy(x = x + vector.x, y = y + vector.y)

    override fun scaled(focusX: Double, focusY: Double, zoom: Double): ImaginaryCircle {
        val newX = (x - focusX) * zoom + focusX
        val newY = (y - focusY) * zoom + focusY
        return ImaginaryCircle(newX, newY, radius*zoom)
    }

    fun scaled(focus: Offset, zoom: Float): ImaginaryCircle {
        val newX = (x - focus.x) * zoom + focus.x
        val newY = (y - focus.y) * zoom + focus.y
        return ImaginaryCircle(newX, newY, zoom * radius)
    }

    override fun rotated(focus: Point, angleInRadians: Double): ImaginaryCircle {
        val x0 = x - focus.x
        val y0 = y - focus.y
        val cosPhi = cos(angleInRadians)
        val sinPhi = sin(angleInRadians)
        return copy(
            x = (x0 * cosPhi - y0 * sinPhi) + focus.x,
            y = (x0 * sinPhi + y0 * cosPhi) + focus.y,
        )
    }

    override fun transformed(translation: Offset, focus: Offset, zoom: Float, rotationAngle: Float): ImaginaryCircle {
        var newX: Double = x + translation.x
        var newY: Double = y + translation.y
        if (focus != Offset.Unspecified) {
            val (focusX, focusY) = focus
            // cmp. Offset.rotateBy & zoom and rotation are commutative
            val dx = newX - focusX
            val dy = newY - focusY
            val phi: Double = rotationAngle * PI/180.0
            val cosPhi = cos(phi)
            val sinPhi = sin(phi)
            newX = (dx * cosPhi - dy * sinPhi) * zoom + focusX
            newY = (dx * sinPhi + dy * cosPhi) * zoom + focusY
        } // tbf because of T;S;R order it is not completely accurate
        return ImaginaryCircle(newX, newY, zoom * radius)
    }
}