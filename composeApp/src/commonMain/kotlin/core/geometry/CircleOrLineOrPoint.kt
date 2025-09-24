package core.geometry

import kotlin.math.abs

sealed interface CircleOrLineOrPoint : GCircle {
    fun distanceFrom(point: Point): Double
}

/**
 * Minimal distance between objects across common perpendicular line (disregarding orientation),
 * if no such perpendicular exists, returns [Double.POSITIVE_INFINITY].
 * Non-zero for intersecting objects, but zero for tangentially touching ones.
 * Measure of how far these 2 objects are from tangential touch
  */
infix fun CircleOrLineOrPoint.perpendicularDistance(another: CircleOrLineOrPoint): Double =
    when (this) {
        is Circle -> when (another) {
            is Circle -> {
                val d = this.distanceBetweenCenters(another)
                val d1 = abs(this.radius + another.radius - d)
                val d2 = abs(this.radius - another.radius - d)
                val d3 = abs(another.radius - this.radius - d)
                minOf(d1, d2, d3)
            }
            is Line ->
                abs(another.distanceFrom(this.centerPoint) - this.radius)
            is Point ->
                this.distanceFrom(another)
        }
        is Line -> when (another) {
            is Circle ->
                abs(this.distanceFrom(another.centerPoint) - another.radius)
            is Line ->
                if (this isCollinearTo another)
                    this.distanceFrom(another.order2point(0.0))
                else // intersecting lines have no common perpendicular line
                    Double.POSITIVE_INFINITY
            is Point ->
                this.distanceFrom(another)
        }
        is Point ->
            another.distanceFrom(this)
    }