package data.geometry

import androidx.compose.runtime.Immutable
import domain.Ix
import domain.TAU
import domain.signNonZero
import domain.updated
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot

sealed interface ArcPathPoint {
    sealed interface Vertex : ArcPathPoint
    val point: Point
    data class Free(override val point: Point) : Vertex
    /** The vertex already exists as a point @ [index] */
    data class Eq(override val point: Point, val index: Ix) : Vertex
    /** The vertex is incident to a carrier object @ [carrierIndex] */
    data class Incident(override val point: Point, val carrierIndex: Ix) : Vertex
    /** The vertex is the 1st intersection of [carrier1Index] and [carrier2Index].
     * Before using this check if such point already exists and use [Eq] in if it does */
    data class Intersection(override val point: Point, val carrier1Index: Ix, val carrier2Index: Ix) : Vertex
    /** Midpoints should not be constrained, always free */
//    data class Midpoint(override val point: Point) : ArcPathPoint
}

sealed interface ArcPathCircle {
    /** `null` corresponds to a [Line] */
    val circle: Circle?
    data class Free(override val circle: Circle?) : ArcPathCircle
    /** The circle or line already exists @ [index] */
    data class Eq(override val circle: Circle?, val index: Ix) : ArcPathCircle
}

// BUG: arcs often break when moving diff pts
@Immutable
data class PartialArcPath(
    val startVertex: ArcPathPoint.Vertex,
    val vertices: List<ArcPathPoint.Vertex> = emptyList(),
    // if midpoint snaps to an existing circle, it should be
    // marked by setting corresponding circle as Eq
    val midpoints: List<Point> = emptyList(),
    val circles: List<ArcPathCircle> = emptyList(),
    val startAngles: List<Double> = emptyList(),
    val sweepAngles: List<Double> = emptyList(),
    val isClosed: Boolean = false,
    /** Grabbed node: any vertex or midpoint */
    val focus: Focus? = null,
) {
    val lastVertex: ArcPathPoint.Vertex get() =
        vertices.lastOrNull() ?: startVertex

    val nArcs: Int get() =
        vertices.size

    @Immutable
    sealed interface Focus {
        data object StartPoint : Focus
        data class Point(val index: Int) : Focus
        data class MidPoint(val index: Int) : Focus
    }

    fun previousVertex(j: Int): ArcPathPoint.Vertex =
        if (j == 0) startVertex
        else vertices[j - 1]

    fun addNewVertex(newVertex: ArcPathPoint.Vertex): PartialArcPath = copy(
        startVertex = startVertex,
        vertices = vertices + newVertex, midpoints = midpoints + lastVertex.point.middle(newVertex.point),
        circles = circles + ArcPathCircle.Free(null),
        startAngles = startAngles + 0.0, sweepAngles = sweepAngles + 0.0
    )

    // i=0 is startPoint
    fun updateVertex(i: Int, newVertex: ArcPathPoint.Vertex): PartialArcPath =
        // TODO: moving start or end when closed
        if (i == 0) {
            val newPoint = newVertex.point
            if (vertices.isEmpty()) {
                copy(startVertex = newVertex)
            } else { // only forward
                val point = startVertex.point
                val nextPoint = vertices[0].point
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
                    startVertex = newVertex, vertices = vertices,
                    midpoints = midpoints.updated(0, newMidpoint),
                    circles = circles.updated(0, ArcPathCircle.Free(newNextCircle)),
                    startAngles = startAngles.updated(0, newNextStartAngle),
                    sweepAngles = sweepAngles // sweep angle stays the same
                )
            }
        } else if (i == vertices.size) { // only backward
            val newPoint = newVertex.point
            val j = i - 1
            val point = vertices[j].point
            val previousPoint = previousVertex(j).point
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
                startVertex = startVertex, vertices = vertices.updated(j, newVertex),
                midpoints = midpoints.updated(j, newMidpoint),
                circles = circles.updated(j, ArcPathCircle.Free(newPreviousCircle)),
                startAngles = startAngles.updated(j, newPreviousStartAngle),
                sweepAngles = sweepAngles // sweep angle stays the same
            )
        } else { // backward + forward
            val newPoint = newVertex.point
            val j = i - 1
            val point = vertices[j].point
            val previousPoint = previousVertex(j).point
            val nextPoint = vertices[j + 1].point
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
                startVertex = startVertex, vertices = vertices.updated(j, newVertex),
                midpoints = midpoints.updated(j, newPreviousMidpoint).updated(i, newNextMidpoint),
                circles = circles
                    .updated(j, ArcPathCircle.Free(newPreviousCircle))
                    .updated(i, ArcPathCircle.Free(newNextCircle)),
                startAngles = startAngles.updated(j, newPreviousStartAngle).updated(i, newNextStartAngle),
                sweepAngles = sweepAngles // sweep angle stays the same
            )
        }

    fun updateMidpoint(j: Int, newMidpoint: Point): PartialArcPath {
        val start = previousVertex(j).point
        val end = vertices[j].point
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
            circles = circles.updated(j, ArcPathCircle.Free(newCircle)),
            startAngles = startAngles.updated(j, newStartAngle),
            sweepAngles = sweepAngles.updated(j, newSweepAngle)
        )
    }

    // TODO: snapping to create smooth connection between arcs (their circles are to touch tangentially)
    //  also snap to existing points, intersections, existing circle=arc, incidence with existing circles
    //  + snap midpoints to straight segment
    fun moveFocused(newPoint: Point): PartialArcPath =
        when (focus) {
            Focus.StartPoint -> updateVertex(0, ArcPathPoint.Free(newPoint))
            is Focus.Point -> updateVertex(focus.index + 1, ArcPathPoint.Free(newPoint))
            is Focus.MidPoint -> updateMidpoint(focus.index, newPoint)
            null -> this
        }

    fun closeLoop(): PartialArcPath =
        addNewVertex(startVertex).copy(isClosed = true)

    fun deleteVertex(i: Int): PartialArcPath {
        // rm point[i]
        // and flatten 2 adjacent arcs
        // or mk the deleted point into a new midpoint for its 2 neighbors
        TODO()
    }
}

/**
 * when [start] -> [newStart],
 * how does the arc's [midpoint] move?
 *
 * Also works for moving end
 * */
private fun updateMidpointFromMovingEnd(
    start: Point,
    end: Point,
    midpoint: Point,
    newStart: Point
): Point {
    val hx = midpoint.x - start.middle(end).x
    val hy = midpoint.y - start.middle(end).y
    val vx = end.x - start.x
    val vy = end.y - start.y
    val vLength = hypot(vx, vy)
    if (vLength == 0.0)
        return newStart.middle(end)
    val left = signNonZero(-vy*hx + vx*hy)
    val h = left * hypot(hx, hy)
    val newVx = end.x - newStart.x
    val newVy = end.y - newStart.y
    val newHx = -newVy*h/vLength
    val newHy = newVx*h/vLength
    return Point(
        (newStart.x + end.x)/2 + newHx,
        (newStart.y + end.y)/2 + newHy,
    )
}

/** From the East, clockwise, in `[0; 2*PI]` */
private fun calculateStartAngle(start: Point, circle: Circle): Double {
    val x = circle.x - start.x
    val y = circle.y - start.y
//    val eastX = circle.x + circle.radius
//    val eastY = circle.y
    return PI + atan2(y, x) // CCW and reversed y-axis cancel each other
}

/** Clockwise, `(-2*PI; 2*PI)` */
private fun calculateSweepAngle(start: Point, midpoint: Point, end: Point, circle: Circle): Double {
    val angle = calculateAngle(circle.centerPoint, start, end)
    val midAngle = calculateAngle(circle.centerPoint, start, midpoint)
    val angle1 = (angle + TAU) % TAU // in [0; TAU)
    val midAngle1 = (midAngle + TAU) % TAU
    val otherArc = midAngle1 > angle1
    return if (otherArc)
        angle1 - TAU // i think thats correct?
    else
        angle1
}