package data.geometry

import androidx.compose.ui.geometry.Offset
import kotlinx.serialization.Serializable

@Serializable
data class Point(
    val x: Double,
    val y: Double
) : GCircle {
    fun toOffset(): Offset =
        Offset(x.toFloat(), y.toFloat())

    companion object {
        fun fromOffset(offset: Offset): Point =
            Point(offset.x.toDouble(), offset.y.toDouble())
    }
}