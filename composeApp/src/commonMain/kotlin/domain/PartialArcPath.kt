package domain

import androidx.compose.runtime.Immutable
import core.geometry.Circle
import core.geometry.CircleOrLine
import core.geometry.EPSILON
import core.geometry.Line
import core.geometry.Point
import core.geometry.calculateAngle
import domain.expressions.computeCircleBy3Points
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
    /** `null` corresponds to a [core.geometry.Line] */
    val circle: Circle?

    data class Free(override val circle: Circle?) : ArcPathCircle
    /** The circle or line already exists @ [index] */
    data class Eq(override val circle: Circle?, val index: Ix) : ArcPathCircle
}

/** vertexNumber = vertexIndex+1
 * vertexNumber=0 is the startVertex
 * vertexNumber=(i+1) => `vertices[i]` */
internal typealias VertexNumber = Int

/**
 * NOTE: vertexIndex+1 = vertexNumber
 *  vertexIndex < vertices.size <= nArcs
 *  arcIndex < nArcs
 *
 * Example: triangle ABC:
 * ```
 * nArcs = 3
 * vertices.size = 2
 * A[vertexNumber=0, no vertexIndex] = startVertex
 *   AB[arcIndex=0] = circles[0]
 * B[vertexNumber=1, vertexIndex=0] = vertices[0]
 *   BC[arcIndex=1] = circles[1]
 * C[vertexNumber=2, vertexIndex=1] = vertices[1]
 *   CA[arcIndex=2] = circles[2]
 * ```
 */
@Suppress("NOTHING_TO_INLINE")
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
    val vertexNumber2snappableCircles: Map<VertexNumber, Set<Ix>> = emptyMap(),
) {
    val lastVertex: ArcPathPoint.Vertex get() =
        vertices.lastOrNull() ?: startVertex

    inline val nArcs: Int get() =
        if (isClosed)
            vertices.size + 1
        else
            vertices.size

    /** A type of the grabbed node */
    @Immutable
    sealed interface Focus {
        data object StartPoint : Focus
        data class Point(val vertexIndex: Int) : Focus
        data class MidPoint(val arcIndex: Int) : Focus
    }

    init {
        require(
            midpoints.size == nArcs && circles.size == nArcs &&
            startAngles.size == nArcs && sweepAngles.size == nArcs
        )
    }

    inline fun vertexAt(vertexNumber: VertexNumber): ArcPathPoint.Vertex =
        if (vertexNumber == 0)
            startVertex
        else
            vertices[vertexNumber - 1]

    inline fun previousVertex(vertexNumber: VertexNumber): ArcPathPoint.Vertex =
        if (vertexNumber == 0)
            vertices.last()
        else if (vertexNumber == 1)
            startVertex
        else vertices[vertexNumber - 2]

    inline fun nextVertex(vertexNumber: VertexNumber): ArcPathPoint.Vertex =
        if (vertexNumber == vertices.size)
            startVertex
        else vertices[vertexNumber]

    inline fun arcIndex2startVertexNumber(arcIndex: Int): VertexNumber =
        arcIndex

    inline fun arcIndex2endVertexNumber(arcIndex: Int): VertexNumber =
        (arcIndex + 1).mod(vertices.size + 1)

    fun arcIndex2CircleOrLine(arcIndex: Int): CircleOrLine =
        when (val c = circles[arcIndex].circle) {
            is Circle -> c
            null -> Line.by2Points(
                vertexAt(arcIndex2startVertexNumber(arcIndex)).point,
                vertexAt(arcIndex2endVertexNumber(arcIndex)).point
            )
//            else -> never()
        }

    fun addNewVertexAndGrabIt(newVertex: ArcPathPoint.Vertex): PartialArcPath = copy(
        startVertex = startVertex,
        vertices = vertices + newVertex,
        midpoints = midpoints + lastVertex.point.middle(newVertex.point),
        circles = circles + ArcPathCircle.Free(null),
        startAngles = startAngles + 0.0, sweepAngles = sweepAngles + 0.0,
        focus = Focus.Point(vertices.size),
    )

    // TODO: vertex-vertex snapping
    /** [vertexNumber] = vertexIndex+1; [vertexNumber]=0 is the [startVertex] */
    fun updateVertex(vertexNumber: VertexNumber, newVertex: ArcPathPoint.Vertex): PartialArcPath =
        if (vertices.isEmpty()) {
            copy(startVertex = newVertex)
        } else if (vertexNumber == 0 && !isClosed) { // move detached start case
            // only forward arc
            val newPoint = newVertex.point
            val point = startVertex.point
            val nextPoint = vertices[0].point
            val newMidpoint =
                updateMidpointFromMovingEnd(point, nextPoint, midpoints[0], newPoint)
            val newNextCircle =
                circleByChordEndsAndMidArc(newPoint, nextPoint, newMidpoint)
//                computeCircleBy3Points(newPoint, newMidpoint, nextPoint) as? Circle
            val newNextStartAngle =
                if (newNextCircle != null)
                    calculateStartAngle(newPoint, newNextCircle)
                else 0.0
            // TODO: snap to end to close loop
//            if (newPoint.distanceFrom(vertices.last().point) < EPSILON) {}
            copy(
                startVertex = newVertex, vertices = vertices,
                midpoints = midpoints.updated(0, newMidpoint),
                circles = circles.updated(0, ArcPathCircle.Free(newNextCircle)),
                startAngles = startAngles.updated(0, newNextStartAngle),
                sweepAngles = sweepAngles,
                vertexNumber2snappableCircles =
                    vertexNumber2snappableCircles + (vertexNumber to emptySet()),
            )
        } else if (vertexNumber == vertices.size && !isClosed) { // move detached end case
            // only backward arc
            val newPoint = newVertex.point
            val vertexIndex = vertexNumber - 1
            val point = vertexAt(vertexNumber).point
            val previousPoint = previousVertex(vertexNumber).point
            val newMidpoint =
                updateMidpointFromMovingEnd(point, previousPoint, midpoints[vertexIndex], newPoint)
            val newPreviousCircle =
                circleByChordEndsAndMidArc(previousPoint, newPoint, newMidpoint)
//                computeCircleBy3Points(previousPoint, newMidpoint, newPoint) as? Circle
            val newPreviousStartAngle =
                if (newPreviousCircle is Circle)
                    calculateStartAngle(previousPoint, newPreviousCircle)
                else 0.0
            // TODO: snap to start to close loop
            copy(
                startVertex = startVertex, vertices = vertices.updated(vertexIndex, newVertex),
                midpoints = midpoints.updated(vertexIndex, newMidpoint),
                circles = circles.updated(vertexIndex, ArcPathCircle.Free(newPreviousCircle)),
                startAngles = startAngles.updated(vertexIndex, newPreviousStartAngle),
                sweepAngles = sweepAngles,
                vertexNumber2snappableCircles =
                    vertexNumber2snappableCircles + (vertexNumber to emptySet()),
            )
        } else { // backward + forward
            val newPoint = newVertex.point
            val arcIndex = (vertexNumber - 1).mod(nArcs)
            val nextArcIndex = vertexNumber.mod(nArcs)
            val point = vertexAt(vertexNumber).point
            val previousPoint = previousVertex(vertexNumber).point
            val nextPoint = nextVertex(vertexNumber).point
            val newPreviousMidpoint = updateMidpointFromMovingEnd(
                point, previousPoint, midpoints[arcIndex], newPoint
            )
            val newPreviousCircle =
                circleByChordEndsAndMidArc(previousPoint, newPoint, newPreviousMidpoint)
//                computeCircleBy3Points(previousPoint, newPreviousMidpoint, newPoint) as? Circle
            val newPreviousStartAngle =
                if (newPreviousCircle != null)
                    calculateStartAngle(previousPoint, newPreviousCircle)
                else 0.0
            val newNextMidpoint =
                updateMidpointFromMovingEnd(point, nextPoint, midpoints[nextArcIndex], newPoint)
            val newNextCircle =
                circleByChordEndsAndMidArc(newPoint, nextPoint, newNextMidpoint)
//                computeCircleBy3Points(newPoint, newNextMidpoint, nextPoint) as? Circle
            val newNextStartAngle =
                if (newNextCircle is Circle)
                    calculateStartAngle(newPoint, newNextCircle)
                else 0.0
            copy(
                startVertex =
                    if (vertexNumber == 0)
                        newVertex
                    else
                        startVertex
                ,
                vertices =
                    if (vertexNumber == 0)
                        vertices
                    else
                        vertices.updated(arcIndex, newVertex)
                ,
                midpoints = midpoints.updated(
                    arcIndex to newPreviousMidpoint,
                    nextArcIndex to newNextMidpoint
                ),
                circles = circles.updated(
                    arcIndex to ArcPathCircle.Free(newPreviousCircle),
                    nextArcIndex to ArcPathCircle.Free(newNextCircle)
                ),
                startAngles = startAngles.updated(
                    arcIndex to newPreviousStartAngle,
                    nextArcIndex to newNextStartAngle
                ),
                sweepAngles = sweepAngles,
                vertexNumber2snappableCircles =
                    vertexNumber2snappableCircles + (vertexNumber to emptySet()),
            )
        }

    fun updateMidpoint(arcIndex: Int, newMidpoint: Point): PartialArcPath {
        val startVertexNumber = arcIndex2startVertexNumber(arcIndex)
        val endVertexNumber = arcIndex2endVertexNumber(arcIndex)
        val start = vertexAt(startVertexNumber).point
        val end = vertexAt(endVertexNumber).point
        val newCircle =
            computeCircleBy3Points(start, newMidpoint, end) as? Circle
        val newStartAngle =
            if (newCircle == null) 0.0
            else calculateStartAngle(start, newCircle)
        val newSweepAngle =
            if (newCircle == null) 0.0
            else calculateSweepAngle(start, newMidpoint, end, newCircle)
        return copy(
            midpoints = midpoints.updated(arcIndex, newMidpoint),
            circles = circles.updated(arcIndex, ArcPathCircle.Free(newCircle)),
            startAngles = startAngles.updated(arcIndex, newStartAngle),
            sweepAngles = sweepAngles.updated(arcIndex, newSweepAngle)
        )
    }

    // TODO: snapping to create smooth connection between arcs (their circles are to touch tangentially)
    //  also snap to existing points, intersections, existing circle=arc, incidence with existing circles
    //  + snap midpoints to straight segment
    fun moveFocused(newPoint: Point): PartialArcPath =
        when (focus) {
            Focus.StartPoint -> updateVertex(0, ArcPathPoint.Free(newPoint))
            is Focus.Point -> updateVertex(focus.vertexIndex + 1, ArcPathPoint.Free(newPoint))
            is Focus.MidPoint -> updateMidpoint(focus.arcIndex, newPoint)
            null -> this
        }

    fun unFocus(): PartialArcPath = copy(
        midpoints =
            if (focus is Focus.MidPoint) {
                val arcIndex = focus.arcIndex
                val startVertexNumber = arcIndex2startVertexNumber(arcIndex)
                val endVertexNumber = arcIndex2endVertexNumber(arcIndex)
                val circle = circles[arcIndex].circle
                val start = vertexAt(startVertexNumber).point
                val end = vertexAt(endVertexNumber).point
                val correctedMidpoint =
                    circle?.pointInBetween(start, end) ?: start.middle(end)
                midpoints.updated(arcIndex, correctedMidpoint)
            } else midpoints
        ,
        focus = null,
    )

    // call in VM.onUp when Vertex is in focus
    fun updateSnappables(vertexNumber: VertexNumber, snappables: Set<Ix>): PartialArcPath =
        copy(
            vertexNumber2snappableCircles =
                vertexNumber2snappableCircles + (vertexNumber to snappables)
        )

    fun closeLoop(): PartialArcPath = copy(
        startVertex = startVertex,
        vertices = vertices,
        midpoints = midpoints + lastVertex.point.middle(startVertex.point),
        circles = circles + ArcPathCircle.Free(null),
        startAngles = startAngles + 0.0,
        sweepAngles = sweepAngles + 0.0,
        isClosed = true,
    )

    fun deleteVertex(vertexNumber: VertexNumber): PartialArcPath {
        // rm point[i]
        // and flatten 2 adjacent arcs
        // or mk the deleted point into a new midpoint for its 2 neighbors
        TODO()
    }
}

/**
 * when [start] -> [newStart],
 * how does the arc's [midpoint] move? (assuming constant sagittal ratio)
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
        (newStart.x + end.x) / 2 + newHx,
        (newStart.y + end.y) / 2 + newHy,
    )
}

private fun circleByChordEndsAndMidArc(chordStart: Point, chordEnd: Point, midArc: Point): Circle? {
    val mx = (chordStart.x + chordEnd.x)/2.0
    val my = (chordStart.y + chordEnd.y)/2.0
    val sx = mx - midArc.x
    val sy = my - midArc.y
    val s = hypot(sx, sy) // sagitta
    if (s < EPSILON)
        return null
    val a2 = squareSum(mx - chordStart.x, my - chordStart.y) // half-chord squared
    val r = (s*s + a2)/(2*s) // radius
    val k = (r - s)/s
    val ox = mx + k*sx
    val oy = my + k*sy
    return Circle(ox, oy, r)
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