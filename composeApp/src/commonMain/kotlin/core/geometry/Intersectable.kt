package core.geometry

sealed interface Intersectable {
    fun calculateIntersectionPoints(other: Intersectable): List<Point>
}