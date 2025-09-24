package core.geometry

import androidx.compose.runtime.Immutable

/** Represents totally ordered set of points isomorphic to `ℝ[0; 1]` or `S¹`
 * with `order` being local 1 dimensional coordinate */
@Immutable
sealed interface LocusWithOrder {
    /** Reverses the order of points within */
    fun reversed(): LocusWithOrder
    // Constraints:
    // order2point(point2order(p)) === p
    // point2order(order2point(o)) === o
    /** sort points on the circle in the order they lie on it (starting from wherever) */
    fun point2order(point: Point): Double
    fun order2point(order: Double): Point
    fun orderInBetween(order1: Double, order2: Double): Double
    fun orderIsInBetween(startOrder: Double, order: Double, endOrder: Double): Boolean

    /** assumes all of them lie on `this` object */
    fun pointIsInBetween(startPoint: Point, point: Point, endPoint: Point): Boolean =
        orderIsInBetween(point2order(startPoint), point2order(point), point2order(endPoint))

    fun orderPoints(points: Collection<Point>): List<Point> =
        points.sortedBy { point2order(it) }

    fun pointInBetween(point1: Point, point2: Point) =
        order2point(orderInBetween(point2order(point1), point2order(point2)))
}