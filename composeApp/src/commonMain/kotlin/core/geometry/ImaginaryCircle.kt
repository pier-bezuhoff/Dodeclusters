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
        val cosine = cos(angleInRadians)
        val sine = sin(angleInRadians)
        return copy(
            x = (x0 * cosine - y0 * sine) + focusX,
            y = (x0 * sine + y0 * cosine) + focusY,
        )
    }

    override fun transformed(
        translation: Offset,
        focus: Offset,
        zoom: Float,
        rotationAngle: Float
    ): ImaginaryCircle {
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
        }
        return ImaginaryCircle(newX, newY, zoom * radius)
    }
}