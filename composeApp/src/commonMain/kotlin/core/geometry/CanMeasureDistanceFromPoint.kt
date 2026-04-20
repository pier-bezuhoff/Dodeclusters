package core.geometry

import androidx.compose.ui.geometry.Offset

// one could be tempted to add distanceSquared method,
// but it's not actually faster for circles/lines
interface CanMeasureDistanceFromPoint {
    fun distanceFrom(point: Point): Double
    fun distanceFrom(x: Double, y: Double): Double =
        distanceFrom(Point(x, y))
    fun distanceFrom(point: Offset): Double =
        distanceFrom(Point.fromOffset(point))
}