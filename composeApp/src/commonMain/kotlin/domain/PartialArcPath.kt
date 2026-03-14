package domain

import androidx.compose.runtime.Immutable
import core.geometry.Circle
import core.geometry.EPSILON
import core.geometry.Point
import domain.expressions.computeCircleBy3Points
import kotlin.math.hypot

@Immutable
data class PartialArcPath(
    val vertices: List<Vertex> = emptyList(),
    val arcs: List<Arc> = emptyList(),
    val isClosed: Boolean = false,

    /** Grabbed node: any vertex or midpoint */
    val focus: Focus? = null,
) {
    @Immutable
    data class Vertex(val snap: PointSnapResult) {
        constructor(
            point: Point
        ) : this(PointSnapResult.Free(point))

        val point: Point get() = snap.result
    }

    /**
     * @property[circle] `null` corresponds to a [core.geometry.Line]
     */
    @Immutable
    data class Arc(
        val circle: Circle?,
        val snap: PointSnapResult,
        val startAngle: Double = 0.0,
        val sweepAngle: Double = 0.0,
    ) {
        constructor(
            circle: Circle?,
            middlePoint: Point,
            startAngle: Double = 0.0,
            sweepAngle: Double = 0.0,
        ) : this(circle, PointSnapResult.Free(middlePoint), startAngle, sweepAngle)

        val middlePoint: Point get() = snap.result
    }

    /** A type of the grabbed node */
    @Immutable
    sealed interface Focus {
        data class Vertex(val vertexIndex: Int) : Focus
        data class MidPoint(val arcIndex: Int) : Focus
    }

    init {
        require(
            vertices.isNotEmpty() &&
            (isClosed && (vertices.size - arcs.size + 2 == 2) || !isClosed && (vertices.size - arcs.size + 1 == 2))
        )
    }

    fun previousVertex(vertexIndex: Int): Vertex =
        vertices[(vertexIndex - 1).mod(vertices.size)]

    fun nextVertex(vertexIndex: Int): Vertex =
        vertices[(vertexIndex + 1).mod(vertices.size)]

    fun arcIndex2startVertex(arcIndex: Int): Vertex =
        vertices[arcIndex]

    fun arcIndex2endVertex(arcIndex: Int): Vertex =
        vertices[(arcIndex + 1).mod(vertices.size)]

    fun addNewVertexAndGrabIt(newVertex: Vertex): PartialArcPath = copy(
        vertices = vertices + newVertex,
        arcs = arcs + Arc(
            circle = null,
            middlePoint = vertices.last().point.middle(newVertex.point),
        ),
        focus = Focus.Vertex(vertices.size),
    )

    // TODO: vertex-vertex snapping
    // MAYBE: dont move midpoint if it's snapped
    fun updateVertex(vertexIndex: Int, newVertex: Vertex): PartialArcPath =
        when {
            vertices.size == 1 -> {
                require(vertexIndex == 0)
                copy(vertices = listOf(newVertex))
            }
            vertexIndex == 0 && !isClosed -> { // move detached start case, 2+ vertices
                // only forward arc
                val newPoint = newVertex.point
                val point = vertices[0].point
                val nextPoint = vertices[1].point
                val newMidpoint =
                    updateMidpointFromMovingEnd(point, nextPoint, arcs[0].middlePoint, newPoint)
                val newNextCircle =
                    circleByChordEndsAndMidArc(newPoint, nextPoint, newMidpoint)
    //                computeCircleBy3Points(newPoint, newMidpoint, nextPoint) as? Circle
                val newNextStartAngle =
                    newNextCircle?.calculateStartAngle(newPoint) ?: 0.0
                // TODO: snap to end to close loop
    //            if (newPoint.distanceFrom(vertices.last().point) < EPSILON) {}
                copy(
                    vertices = vertices.updated(0, newVertex),
                    arcs = arcs.updated(0,
                        Arc(
                            circle = newNextCircle,
                            middlePoint = newMidpoint,
                            startAngle = newNextStartAngle,
                            sweepAngle = arcs[0].sweepAngle,
                        )
                    ),
                )
            }
            vertexIndex == vertices.lastIndex && !isClosed -> { // move detached end case
                // only backward arc
                val newPoint = newVertex.point
                val point = vertices[vertexIndex].point
                val previousPoint = previousVertex(vertexIndex).point
                val previousArcIndex = arcs.lastIndex
                val newMidpoint =
                    updateMidpointFromMovingEnd(point, previousPoint, arcs[previousArcIndex].middlePoint, newPoint)
                val newPreviousCircle =
                    circleByChordEndsAndMidArc(previousPoint, newPoint, newMidpoint)
    //                computeCircleBy3Points(previousPoint, newMidpoint, newPoint) as? Circle
                val newPreviousStartAngle =
                    if (newPreviousCircle is Circle)
                        newPreviousCircle.calculateStartAngle(previousPoint)
                    else 0.0
                // TODO: snap to start to close loop
                copy(
                    vertices = vertices.updated(vertexIndex, newVertex),
                    arcs = arcs.updated(previousArcIndex, Arc(
                        circle = newPreviousCircle,
                        middlePoint = newMidpoint,
                        startAngle = newPreviousStartAngle,
                        sweepAngle = arcs[previousArcIndex].sweepAngle
                    )),
                )
            }
            else -> { // backward + forward, move within the chain
                val newPoint = newVertex.point
                val previousArcIndex = (vertexIndex - 1).mod(arcs.size) // not the last arc with free end
                require(vertexIndex < arcs.size) { "this case should have been handled within detached end" }
                val nextArcIndex = vertexIndex // not the first arc with free start
                val point = vertices[vertexIndex].point
                val previousPoint = previousVertex(vertexIndex).point
                val nextPoint = nextVertex(vertexIndex).point
                val newPreviousMidpoint = updateMidpointFromMovingEnd(
                    point, previousPoint, arcs[previousArcIndex].middlePoint, newPoint
                )
                val newPreviousCircle =
                    circleByChordEndsAndMidArc(previousPoint, newPoint, newPreviousMidpoint)
    //                computeCircleBy3Points(previousPoint, newPreviousMidpoint, newPoint) as? Circle
                val newPreviousStartAngle =
                    newPreviousCircle?.calculateStartAngle(previousPoint) ?: 0.0
                val newNextMidpoint =
                    updateMidpointFromMovingEnd(point, nextPoint, arcs[nextArcIndex].middlePoint, newPoint)
                val newNextCircle =
                    circleByChordEndsAndMidArc(newPoint, nextPoint, newNextMidpoint)
    //                computeCircleBy3Points(newPoint, newNextMidpoint, nextPoint) as? Circle
                val newNextStartAngle =
                    if (newNextCircle is Circle)
                        newNextCircle.calculateStartAngle(newPoint)
                    else 0.0
                copy(
                    vertices = vertices.updated(vertexIndex, newVertex),
                    arcs = arcs.updated(
                        previousArcIndex to Arc(
                            circle = newPreviousCircle,
                            middlePoint = newPreviousMidpoint,
                            startAngle = newPreviousStartAngle,
                            sweepAngle = arcs[previousArcIndex].sweepAngle,
                        ),
                        nextArcIndex to Arc(
                            circle = newNextCircle,
                            middlePoint = newNextMidpoint,
                            startAngle = newNextStartAngle,
                            sweepAngle = arcs[nextArcIndex].sweepAngle,
                        )
                    ),
                )
            }
        }

    fun updateMidpoint(arcIndex: Int, snap: PointSnapResult): PartialArcPath {
        val newMidpoint = snap.result
        val start = arcIndex2startVertex(arcIndex).point
        val end = arcIndex2endVertex(arcIndex).point
        val newCircle =
            computeCircleBy3Points(start, newMidpoint, end) as? Circle
        val newStartAngle =
            newCircle?.calculateStartAngle(start) ?: 0.0
        val newSweepAngle =
            newCircle?.calculateSweepAngle(start, newMidpoint, end) ?: 0.0
        return copy(
            arcs = arcs.updated(arcIndex,
                Arc(
                    circle = newCircle,
                    snap = snap,
                    startAngle = newStartAngle,
                    sweepAngle = newSweepAngle,
                )
            ),
        )
    }

    // TODO: snapping to create smooth connection between arcs (their circles are to touch tangentially)
    //  also snap to existing points, intersections, existing circle=arc, incidence with existing circles
    //  + snap midpoints to straight segment
    fun moveFocused(snap: PointSnapResult): PartialArcPath =
        when (focus) {
            is Focus.Vertex -> updateVertex(focus.vertexIndex, Vertex(snap))
            is Focus.MidPoint -> updateMidpoint(focus.arcIndex, snap)
            null -> this
        }

    fun realignGrabbedMidpoint(): PartialArcPath = copy(
        arcs =
            if (focus is Focus.MidPoint && arcs[focus.arcIndex].snap is PointSnapResult.Free) {
                val arcIndex = focus.arcIndex
                val start = arcIndex2startVertex(arcIndex).point
                val end = arcIndex2endVertex(arcIndex).point
                val circle = arcs[arcIndex].circle
                val correctedMidpoint =
                    circle?.pointInBetween(start, end) ?: start.middle(end)
                arcs.updated(arcIndex,
                    Arc(
                        circle = circle,
                        middlePoint = correctedMidpoint,
                        startAngle = arcs[arcIndex].startAngle,
                        sweepAngle = arcs[arcIndex].sweepAngle,
                    )
                )
            } else arcs
        ,
    )

    fun unFocus(): PartialArcPath = copy(
        focus = null,
    )

    fun closeLoop(): PartialArcPath = copy(
        arcs = arcs + Arc(
            circle = null,
            middlePoint = vertices.last().point.middle(vertices.first().point)
        ),
        isClosed = true,
    )

    fun deleteVertex(vertexIndex: Int): PartialArcPath {
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
    val k = h/vLength
    val newVx = end.x - newStart.x
    val newVy = end.y - newStart.y
    val newHx = -newVy*k
    val newHy = newVx*k
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
    val r = s/2 + a2/(2*s) // radius
    val k = r/s - 1.0
    val ox = mx + k*sx
    val oy = my + k*sy
    return Circle(ox, oy, r)
}