package core.geometry

import androidx.compose.ui.geometry.Offset

// one could be tempted to add distanceSquared method,
// but it's not faster for circles/lines
interface CanMeasureDistanceFromPoint {
    fun distanceFrom(point: Point): Double
    fun distanceFrom(point: Offset): Double
}