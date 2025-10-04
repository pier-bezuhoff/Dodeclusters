package core.geometry

import androidx.compose.runtime.Immutable
import domain.Ix
import domain.TAU
import domain.expressions.computeCircleBy3Points
import domain.signNonZero
import domain.updated
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot

@Immutable
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

    /** Midpoints should not be constrained, other than to circles incident to both arc-start and
     * arc-end points, in which case set [carrierIndex] */
    data class Midpoint(override val point: Point, val carrierIndex: Ix? = null) : ArcPathPoint
}

sealed interface ArcPathCircle {
    /** `null` corresponds to a [Line] */
    val circle: Circle?

    data class Free(override val circle: Circle?) : ArcPathCircle
    /** The circle or line already exists @ [index] */
    data class Eq(override val circle: Circle?, val index: Ix) : ArcPathCircle
}

/** [vertexNumber] = vertexIndex+1; [vertexNumber]=0 is the [startVertex] */
internal typealias VertexNumber = Int

// BUG: arcs often break when moving diff pts
// NOTE: vertexNumber = vertexIndex + 1
//  vertexNumber=0 => startVertex
//  vertexNumber=(i+1) => vertices[i]
@Immutable
data class PartialArcPath(
    val startVertex: ArcPathPoint.Vertex,
    val vertices: List<ArcPathPoint.Vertex> = emptyList(),
    // if a midpoint snaps to an existing circle, it should be
    // marked by setting corresponding circle as Eq
    val midpoints: List<Point> = emptyList(),
    val circles: List<ArcPathCircle> = emptyList(),
    val isClosed: Boolean = false,

    val startAngles: List<Double> = emptyList(),
    val sweepAngles: List<Double> = emptyList(),

    /** Grabbed node: any vertex or midpoint */
    val focus: Focus? = null,
    // moving a vertex should invalidate its snappables
    val vertexNumber2snappableCircles: Map<Ix, Set<Ix>> = emptyMap(),
) {
    val lastVertex: ArcPathPoint.Vertex get() =
        vertices.lastOrNull() ?: startVertex

    val nArcs: Int get() =
        vertices.size

    init {
        val n = vertices.size
        require(midpoints.size == n && circles.size == n && startAngles.size == n && sweepAngles.size == n)
    }

    /** A type of the grabbed node */
    @Immutable
    sealed interface Focus {
        data object StartPoint : Focus
        data class Point(val vertexIndex: Int) : Focus
        data class MidPoint(val midpointIndex: Int) : Focus
    }

    fun previousVertex(vertexIndex: Int): ArcPathPoint.Vertex =
        if (vertexIndex == 0)
            startVertex
        else vertices[vertexIndex - 1]

    fun nextVertex(vertexIndex: Int): ArcPathPoint.Vertex =
        if (vertexIndex == vertices.size - 1)
            startVertex
        else vertices[vertexIndex + 1]

    fun addNewVertex(newVertex: ArcPathPoint.Vertex): PartialArcPath = copy(
        startVertex = startVertex,
        vertices = vertices + newVertex,
        midpoints = midpoints + lastVertex.point.middle(newVertex.point),
        circles = circles + ArcPathCircle.Free(null),
        startAngles = startAngles + 0.0, sweepAngles = sweepAngles + 0.0
    )

    // TODO: moving start or end when closed
    /** [vertexNumber] = vertexIndex+1; [vertexNumber]=0 is the [startVertex] */
    fun updateVertex(vertexNumber: VertexNumber, newVertex: ArcPathPoint.Vertex): PartialArcPath =
        if (vertexNumber == 0 && !isClosed) { // move detached start case
            val newPoint = newVertex.point
            if (vertices.isEmpty()) {
                copy(startVertex = newVertex)
            } else { // only forward arc
                val point = startVertex.point
                val nextPoint = vertices[0].point
                val newMidpoint =
                    updateMidpointFromMovingEnd(point, nextPoint, midpoints[0], newPoint)
                val newNextCircle =
                    computeCircleBy3Points(newPoint, newMidpoint, nextPoint) as? Circle
                val newNextStartAngle =
                    if (newNextCircle != null)
                        calculateStartAngle(newPoint, newNextCircle)
                    else 0.0
                copy(
                    startVertex = newVertex, vertices = vertices,
                    midpoints = midpoints.updated(0, newMidpoint),
                    circles = circles.updated(0, ArcPathCircle.Free(newNextCircle)),
                    startAngles = startAngles.updated(0, newNextStartAngle),
                    sweepAngles = sweepAngles, // sweep angle stays the same
                    vertexNumber2snappableCircles =
                        vertexNumber2snappableCircles + (vertexNumber to emptySet()),
                )
            }
        } else if (vertexNumber == vertices.size && !isClosed) { // move detached end case
            // only backward arc
            val newPoint = newVertex.point
            val vertexIndex = vertexNumber - 1
            val point = vertices[vertexIndex].point
            val previousPoint = previousVertex(vertexIndex).point
            val newMidpoint =
                updateMidpointFromMovingEnd(point, previousPoint, midpoints[vertexIndex], newPoint)
            val newPreviousCircle =
                computeCircleBy3Points(previousPoint, newMidpoint, newPoint) as? Circle
            val newPreviousStartAngle =
                if (newPreviousCircle is Circle)
                    calculateStartAngle(previousPoint, newPreviousCircle)
                else 0.0
            copy(
                startVertex = startVertex, vertices = vertices.updated(vertexIndex, newVertex),
                midpoints = midpoints.updated(vertexIndex, newMidpoint),
                circles = circles.updated(vertexIndex, ArcPathCircle.Free(newPreviousCircle)),
                startAngles = startAngles.updated(vertexIndex, newPreviousStartAngle),
                sweepAngles = sweepAngles, // sweep angle stays the same
                vertexNumber2snappableCircles =
                    vertexNumber2snappableCircles + (vertexNumber to emptySet()),
            )
        } else { // backward + forward
            val newPoint = newVertex.point
            val nextVertexIndex = vertexNumber
            val vertexIndex = vertexNumber - 1
            val point = vertices[vertexIndex].point
            val previousPoint = previousVertex(vertexIndex).point
            val nextPoint = vertices[vertexIndex + 1].point
            val newPreviousMidpoint = updateMidpointFromMovingEnd(
                point, previousPoint, midpoints[vertexIndex], newPoint
            )
            val newPreviousCircle =
                computeCircleBy3Points(previousPoint, newPreviousMidpoint, newPoint) as? Circle
            val newPreviousStartAngle =
                if (newPreviousCircle != null)
                    calculateStartAngle(previousPoint, newPreviousCircle)
                else 0.0
            val newNextMidpoint =
                updateMidpointFromMovingEnd(point, nextPoint, midpoints[nextVertexIndex], newPoint)
            val newNextCircle =
                computeCircleBy3Points(newPoint, newNextMidpoint, nextPoint) as? Circle
            val newNextStartAngle =
                if (newNextCircle is Circle)
                    calculateStartAngle(newPoint, newNextCircle)
                else 0.0
            copy(
                startVertex = startVertex,
                vertices = vertices.updated(vertexIndex, newVertex),
                midpoints = midpoints.updated(
                    vertexIndex to newPreviousMidpoint,
                    nextVertexIndex to newNextMidpoint
                ),
                circles = circles.updated(
                    vertexIndex to ArcPathCircle.Free(newPreviousCircle),
                    nextVertexIndex to ArcPathCircle.Free(newNextCircle)
                ),
                startAngles = startAngles.updated(
                    vertexIndex to newPreviousStartAngle,
                    nextVertexIndex to newNextStartAngle
                ),
                sweepAngles = sweepAngles, // sweep angle stays the same
                vertexNumber2snappableCircles =
                    vertexNumber2snappableCircles + (vertexNumber to emptySet()),
            )
        }

    fun updateMidpoint(midpointIndex: Int, newMidpoint: Point): PartialArcPath {
        val start = previousVertex(midpointIndex).point
        val end = vertices[midpointIndex].point
        val newCircle =
            computeCircleBy3Points(start, newMidpoint, end) as? Circle
        val newStartAngle =
            if (newCircle == null) 0.0
            else calculateStartAngle(start, newCircle)
        val newSweepAngle =
            if (newCircle == null) 0.0
            else calculateSweepAngle(start, newMidpoint, end, newCircle)
        return copy(
            midpoints = midpoints.updated(midpointIndex, newMidpoint),
            circles = circles.updated(midpointIndex, ArcPathCircle.Free(newCircle)),
            startAngles = startAngles.updated(midpointIndex, newStartAngle),
            sweepAngles = sweepAngles.updated(midpointIndex, newSweepAngle)
        )
    }

    // TODO: snapping to create smooth connection between arcs (their circles are to touch tangentially)
    //  also snap to existing points, intersections, existing circle=arc, incidence with existing circles
    //  + snap midpoints to straight segment
    fun moveFocused(newPoint: Point): PartialArcPath =
        when (focus) {
            Focus.StartPoint -> updateVertex(0, ArcPathPoint.Free(newPoint))
            is Focus.Point -> updateVertex(focus.vertexIndex + 1, ArcPathPoint.Free(newPoint))
            is Focus.MidPoint -> updateMidpoint(focus.midpointIndex, newPoint)
            null -> this
        }

    // call in VM.onUp when Vertex is in focus
    fun updateSnappables(vertexNumber: VertexNumber, snappables: Set<Ix>): PartialArcPath =
        copy(
            vertexNumber2snappableCircles =
                vertexNumber2snappableCircles + (vertexNumber to snappables)
        )

    fun closeLoop(): PartialArcPath =
        addNewVertex(startVertex).copy(isClosed = true)

    fun deleteVertex(vertexNumber: VertexNumber): PartialArcPath {
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