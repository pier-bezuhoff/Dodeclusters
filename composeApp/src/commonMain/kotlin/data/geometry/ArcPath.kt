package data.geometry

import domain.TAU
import domain.signNonZero
import domain.updated
import kotlin.math.atan2
import kotlin.math.hypot

data class ArcPath(
    val startPoint: Point,
    val points: List<Point> = emptyList(),
    val midpoints: List<Point> = emptyList(),
    // dependent data
    val length: Int = 1,
    // null Circle corresponds to a Line
    val circles: List<Circle?> = emptyList(),
    val startAngles: List<Double> = emptyList(),
    val sweepAngles: List<Double> = emptyList(),
    val focus: Focus? = null,
) {
    val lastPoint: Point get() =
        points.lastOrNull() ?: startPoint

    val allAnchors: List<Point> get() =
        listOf(startPoint) + points

    sealed interface Focus {
        data object StartPoint : Focus
        data class Point(val index: Int) : Focus
        data class MidPoint(val index: Int) : Focus
    }

    fun addNewPoint(newPoint: Point): ArcPath = copy(
        startPoint = startPoint,
        points = points + newPoint, midpoints = midpoints + lastPoint.middle(newPoint),
        length = length + 1,
        circles = circles + null,
        startAngles = startAngles + 0.0, sweepAngles = sweepAngles + 0.0
    )

    // i=0 is startPoint
    fun updatePoint(i: Int, newPoint: Point): ArcPath =
        if (i == 0) {
            if (length == 1) {
                copy(startPoint = newPoint)
            } else { // only forward
                val point = startPoint
                val nextPoint = points[0]
                val newMidpoint = updateMidpointFromMovingEnd(point, nextPoint, midpoints[0], newPoint)
                val newNextCircle = GeneralizedCircle.perp3(
                    GeneralizedCircle.fromGCircle(newPoint),
                    GeneralizedCircle.fromGCircle(newMidpoint),
                    GeneralizedCircle.fromGCircle(nextPoint),
                )?.toGCircle() as? Circle
                val newNextStartAngle =
                    if (newNextCircle != null)
                        calculateStartAngle(newPoint, newNextCircle)
                    else 0.0
                copy(
                    startPoint = newPoint, points = points,
                    midpoints = midpoints.updated(0, newMidpoint),
                    length = length,
                    circles = circles.updated(0, newNextCircle),
                    startAngles = startAngles.updated(0, newNextStartAngle),
                    sweepAngles = sweepAngles // sweep angle stays the same
                )
            }
        } else if (i == length - 1) { // only backward
            val j = i - 1
            val point = points[j]
            val previousPoint = if (j == 0) startPoint else points[j - 1]
            val newMidpoint = updateMidpointFromMovingEnd(point, previousPoint, midpoints[j], newPoint)
            val newPreviousCircle = GeneralizedCircle.perp3(
                GeneralizedCircle.fromGCircle(previousPoint),
                GeneralizedCircle.fromGCircle(newMidpoint),
                GeneralizedCircle.fromGCircle(newPoint),
            )?.toGCircle() as? Circle
            val newPreviousStartAngle =
                if (newPreviousCircle is Circle)
                    calculateStartAngle(previousPoint, newPreviousCircle)
                else 0.0
            copy(
                startPoint = newPoint, points = points.updated(j, newPoint),
                midpoints = midpoints.updated(j, newMidpoint),
                length = length,
                circles = circles.updated(j, newPreviousCircle),
                startAngles = startAngles.updated(j, newPreviousStartAngle),
                sweepAngles = sweepAngles // sweep angle stays the same
            )
        } else { // backward + forward
            val j = i - 1
            val point = points[j]
            val previousPoint = if (j == 0) startPoint else points[j - 1]
            val nextPoint = points[j + 1]
            val newPreviousMidpoint = updateMidpointFromMovingEnd(point, previousPoint, midpoints[j], newPoint)
            val newPreviousCircle = GeneralizedCircle.perp3(
                GeneralizedCircle.fromGCircle(previousPoint),
                GeneralizedCircle.fromGCircle(newPreviousMidpoint),
                GeneralizedCircle.fromGCircle(newPoint),
            )?.toGCircle() as? Circle
            val newPreviousStartAngle =
                if (newPreviousCircle != null)
                    calculateStartAngle(previousPoint, newPreviousCircle)
                else 0.0
            val newNextMidpoint = updateMidpointFromMovingEnd(point, nextPoint, midpoints[i], newPoint)
            val newNextCircle = GeneralizedCircle.perp3(
                GeneralizedCircle.fromGCircle(newPoint),
                GeneralizedCircle.fromGCircle(newNextMidpoint),
                GeneralizedCircle.fromGCircle(nextPoint),
            )?.toGCircle() as? Circle
            val newNextStartAngle =
                if (newNextCircle is Circle)
                    calculateStartAngle(newPoint, newNextCircle)
                else 0.0
            copy(
                startPoint = newPoint, points = points.updated(j, newPoint),
                midpoints = midpoints.updated(j, newPreviousMidpoint).updated(i, newNextMidpoint),
                length = length,
                circles = circles.updated(j, newPreviousCircle).updated(i, newNextCircle),
                startAngles = startAngles.updated(j, newPreviousStartAngle).updated(i, newNextStartAngle),
                sweepAngles = sweepAngles // sweep angle stays the same
            )
        }

    fun updateMidpoint(j: Int, newMidpoint: Point): ArcPath {
        val start = if (j == 0) startPoint else points[j - 1]
        val end = points[j]
        val newCircle = GeneralizedCircle.perp3(
            GeneralizedCircle.fromGCircle(start),
            GeneralizedCircle.fromGCircle(newMidpoint),
            GeneralizedCircle.fromGCircle(end),
        )?.toGCircle() as? Circle
        val newStartAngle =
            if (newCircle == null) 0.0
            else calculateStartAngle(start, newCircle)
        val newSweepAngle =
            if (newCircle == null) 0.0
            else calculateSweepAngle(start, newMidpoint, end, newCircle)
        return copy(
            midpoints = midpoints.updated(j, newMidpoint),
            circles = circles.updated(j, newCircle),
            startAngles = startAngles.updated(j, newStartAngle),
            sweepAngles = sweepAngles.updated(j, newSweepAngle)
        )
    }

    fun moveFocused(newPoint: Point): ArcPath =
        when (focus) {
            Focus.StartPoint -> updatePoint(0, newPoint)
            is Focus.Point -> updatePoint(focus.index + 1, newPoint)
            is Focus.MidPoint -> updateMidpoint(focus.index, newPoint)
            null -> this
        }
}

/**
 * when [start] -> [newStart],
 * how moves the arc [midpoint]?
 *
 * Also works for moving end
 * */
private fun updateMidpointFromMovingEnd(
    start: Point,
    end: Point,
    midpoint: Point,
    newStart: Point
): Point {
    val hx = midpoint.x - start.middle(newStart).x
    val hy = midpoint.y - start.middle(newStart).y
    val vx = end.x - start.x
    val vy = end.y - start.y
    val vLength = hypot(vx, vy)
    if (vLength < EPSILON)
        return newStart.middle(end)
    val left = signNonZero(-vy*hx + vx*hy)
    val h = left* hypot(hx, hy)
    val newVx = newStart.x - newStart.x
    val newVy = newStart.y - newStart.y
    val newHx = -newVy*h/vLength
    val newHy = newVx*h/vLength
    return Point(
        (newStart.x + end.x)/2 + newHx,
        (newStart.y + end.y)/2 + newHy,
    )
}

/** From the East, clockwise */
private fun calculateStartAngle(start: Point, circle: Circle): Double {
    val x = circle.x - start.x
    val y = circle.y - start.y
//    val eastX = circle.x + circle.radius
//    val eastY = circle.y
    return atan2(y, x) // CCW and reversed y-axis cancel each other
}

private fun calculateSweepAngle(start: Point, midpoint: Point, end: Point, circle: Circle): Double {
    val angle = calculateAngle(circle.centerPoint, start, end)
    val midAngle = calculateAngle(circle.centerPoint, start, midpoint)
    val angle1 = (angle + TAU) % TAU // in [0; TAU)
    val midAngle1 = (midAngle + TAU) % TAU
    val otherArc = midAngle1 > angle1
    return if (otherArc)
        TAU - angle1
    else
        angle1
}