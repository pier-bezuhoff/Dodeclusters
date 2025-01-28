package data.geometry

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

sealed interface CircleOrLineOrImaginaryCircle : GCircle

@Immutable
@Serializable
data class ImaginaryCircle(
    val x: Double,
    val y: Double,
    val radius: Double,
) : CircleOrLineOrImaginaryCircle {
    override fun translated(vector: Offset): ImaginaryCircle =
        ImaginaryCircle(x + vector.x, y + vector.y, radius)

    override fun scaled(focusX: Double, focusY: Double, zoom: Double): ImaginaryCircle {
        val newX = (x - focusX) * zoom + focusX
        val newY = (y - focusY) * zoom + focusY
        return ImaginaryCircle(newX, newY, radius*zoom)
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

    fun scaled(focus: Offset, zoom: Float): ImaginaryCircle {
        val newX = (x - focus.x) * zoom + focus.x
        val newY = (y - focus.y) * zoom + focus.y
        return ImaginaryCircle(newX, newY, zoom * radius)
    }
}