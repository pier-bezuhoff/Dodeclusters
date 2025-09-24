package core.geometry

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import domain.radians
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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

    override fun translated(dx: Double, dy: Double): ImaginaryCircle =
        copy(x = x + dx, y = y + dy)

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

    override fun rotated(focusX: Double, focusY: Double, angleInRadians: Double): ImaginaryCircle {
        val x0 = x - focusY
        val y0 = y - focusY
        val cosPhi = cos(angleInRadians)
        val sinPhi = sin(angleInRadians)
        return copy(
            x = (x0 * cosPhi - y0 * sinPhi) + focusX,
            y = (x0 * sinPhi + y0 * cosPhi) + focusY,
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
            val phi: Double = rotationAngle.radians
            val cosPhi = cos(phi)
            val sinPhi = sin(phi)
            newX = (dx * cosPhi - dy * sinPhi) * zoom + focusX
            newY = (dx * sinPhi + dy * cosPhi) * zoom + focusY
        }
        return ImaginaryCircle(newX, newY, zoom * radius)
    }
}