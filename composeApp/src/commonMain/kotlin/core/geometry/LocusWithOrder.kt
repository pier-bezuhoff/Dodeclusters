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
    fun agreesWithOrientation(startOrder: Double, middleOrder: Double, endOrder: Double): Boolean

    /** Whether 3-point orientation agrees with this object's orientation
     * (assuming all 3 points lie on `this` object).
     *
     * Alternatively: whether [middlePoint] lies on an arc [startPoint]->[endPoint]
     * along the direction of this object */
    fun agreesWithOrientation(startPoint: Point, middlePoint: Point, endPoint: Point): Boolean =
        agreesWithOrientation(point2order(startPoint), point2order(middlePoint), point2order(endPoint))

    fun orderPoints(points: Collection<Point>): List<Point> =
        points.sortedBy { point2order(it) }

    fun pointInBetween(point1: Point, point2: Point) =
        order2point(orderInBetween(point2order(point1), point2order(point2)))
}