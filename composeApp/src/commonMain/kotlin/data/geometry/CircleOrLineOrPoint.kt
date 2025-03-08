package data.geometry

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

fun CircleOrLine.translateUntilTangency(base: CircleOrLineOrPoint): CircleOrLine =
    when (this) {
        is Circle -> when (base) {
            is Circle -> {
                val b = base.project(this.centerPoint)
                val p = this.project(b)
                this.translated(b.x - p.x, b.y - p.y)
            }
            is Line -> {
                val b = base.project(this.centerPoint)
                val p = this.project(b)
                this.translated(b.x - p.x, b.y - p.y)
            }
            is Point -> {
                val p = this.project(base)
                this.translated(base.x - p.x, base.y - p.y)
            }
        }
        is Line -> when (base) {
            is Circle -> this.translatedTo(base.project(this.project(base.centerPoint)))
            is Line -> base // we assume collinearity
            is Point -> this.translatedTo(base)
        }
    }