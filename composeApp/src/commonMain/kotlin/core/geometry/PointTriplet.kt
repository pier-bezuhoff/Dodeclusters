package core.geometry

// circle or line with stable points
data class PointTriplet(
    val point1: Point,
    val point2: Point,
    val point3: Point,
) {
    val isCircle get() =
        point1.isFinite && point2.isFinite && point3.isFinite &&
        point1 != point2 && point2 != point3 && point3 != point1

    val isLine get() = when {
        point1.isInfinite -> point2.isFinite && point3.isFinite && point2 != point3
        point2.isInfinite -> point1.isFinite && point3.isFinite && point1 != point3
        point3.isInfinite -> point1.isFinite && point2.isFinite && point1 != point2
        else -> false
    }

    /** project [point] onto self and calculate its invariant parametrization */
    fun point2order(point: Point): Double {
        TODO()
    }
}