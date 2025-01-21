package data.geometry

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class ImaginaryCircle(
    val x: Double,
    val y: Double,
    val radius: Double,
) : GCircle {
    override fun translated(vector: Offset): ImaginaryCircle =
        ImaginaryCircle(x + vector.x, y + vector.y, radius)

    override fun scaled(focusX: Double, focusY: Double, zoom: Double): ImaginaryCircle {
        val newX = (x - focusX) * zoom + focusX
        val newY = (y - focusY) * zoom + focusY
        return ImaginaryCircle(newX, newY, radius*zoom)
    }
}