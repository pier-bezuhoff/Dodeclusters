package data.geometry

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
@Immutable
data class ImaginaryCircle(
    val x: Double,
    val y: Double,
    val radius: Double,
) : GCircle {
    override fun scaled(focusX: Double, focusY: Double, zoom: Double): ImaginaryCircle {
        val newX = (x - focusX) * zoom + focusX
        val newY = (y - focusY) * zoom + focusY
        return ImaginaryCircle(newX, newY, radius*zoom)
    }
}