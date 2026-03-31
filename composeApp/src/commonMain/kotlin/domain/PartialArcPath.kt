package domain

import androidx.compose.runtime.Immutable
import core.geometry.Circle
import core.geometry.EPSILON
import core.geometry.Point
import domain.expressions.SagittaRatioParameters
import domain.expressions.computeCircleBy2PointsAndSagittaRatio
import domain.expressions.computeCircleBy3Points
import domain.expressions.computeSagittaRatio
import kotlin.math.hypot

sealed interface AlignmentLine {
    data class Horizontal(val x: Double) : AlignmentLine
    data class Vertical(val y: Double) : AlignmentLine
}

@Immutable
data class PartialArcPath(
    val vertices: List<Vertex> = emptyList(),
    val arcs: List<Arc> = emptyList(),
    val isClosed: Boolean = false,
    /** Grabbed node: any vertex or midpoint */
    val focus: Focus? = null,
    val alignmentLines: List<AlignmentLine> = emptyList(),
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
     * @property[midpointSnap] snap of the arc's middle point
     */
    @Immutable
    data class Arc(
        val circle: Circle?,
        val midpointSnap: PointSnapResult,
        val startAngle: Double = 0.0,
        val sweepAngle: Double = 0.0,
    ) {
        constructor(
            circle: Circle?,
            middlePoint: Point,
            startAngle: Double = 0.0,
            sweepAngle: Double = 0.0,
        ) : this(circle, PointSnapResult.Free(middlePoint), startAngle, sweepAngle)

        val middlePoint: Point get() = midpointSnap.result
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

    fun moveFocus(
        snap: PointSnapResult,
        snapDistance: Double,
    ): PartialArcPath = when (focus) {
        is Focus.Vertex -> {
            when (snap) {
                is PointSnapResult.Free -> {
                    val point = snap.result // free
                    val vertexPoints = vertices
                        .withoutElementAt(focus.vertexIndex)
                        .map { it.point }
                    val snapToVertices = Snapping.snapPointToPointsSimple(
                        point, vertexPoints,
                        snapDistance = snapDistance,
                    )
                    when (snapToVertices) {
                        is PointSnapResult.Free if (ENABLE_ALIGNMENT_SNAPPING) -> {
                            val alignmentSnap = Snapping.snapAlignPointToPointsVerticallyOrHorizontally(
                                point, vertexPoints,
                                snapDistance = snapDistance,
                            )
                            when (alignmentSnap) {
                                is PointSnapResult.HorizontalAlignment ->
                                    updateVertex(focus.vertexIndex, alignmentSnap.toFree())
                                        .copy(alignmentLines = listOf(
                                            AlignmentLine.Horizontal(alignmentSnap.x)
                                        ))
                                is PointSnapResult.VerticalAlignment ->
                                    updateVertex(focus.vertexIndex, alignmentSnap.toFree())
                                        .copy(alignmentLines = listOf(
                                            AlignmentLine.Vertical(alignmentSnap.y)
                                        ))
                                is PointSnapResult.HorizontalAndVerticalAlignment ->
                                    updateVertex(focus.vertexIndex, alignmentSnap.toFree())
                                        .copy(alignmentLines = listOf(
                                            AlignmentLine.Horizontal(alignmentSnap.x),
                                            AlignmentLine.Vertical(alignmentSnap.y)
                                        ))
                                is PointSnapResult.Free ->
                                    updateVertex(focus.vertexIndex, snap)
                                        .copy(alignmentLines = emptyList())
                            }
                        }
                        else -> {
                            updateVertex(focus.vertexIndex, snapToVertices.toFree())
                                .copy(alignmentLines = emptyList())
                        }
                    }
                }
                else -> {
                    updateVertex(focus.vertexIndex, snap)
                        .copy(alignmentLines = emptyList())
                }
            }
        }
        is Focus.MidPoint -> {
            updateMidpoint(focus.arcIndex, snap)
                .copy(alignmentLines = emptyList())
        }
        null -> this
    }

    // TODO: snap to other arc-paths' arcs
    // MAYBE: dont move midpoint if it's Eq-snapped
    // MAYBE: snap midpoint so it can stay onto its carrier
    fun updateVertex(
        vertexIndex: Int,
        vertexSnap: PointSnapResult,
    ): PartialArcPath {
        val newVertex = Vertex(vertexSnap)
        return when {
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
                    //                    circleByChordEndsAndMidArc(newPoint, nextPoint, newMidpoint)
                    computeCircleBy3Points(newPoint, newMidpoint, nextPoint) as? Circle
                val newNextStartAngle =
                    newNextCircle?.calculateStartAngle(newPoint) ?: 0.0
                copy(
                    vertices = vertices.updated(0, newVertex),
                    arcs = arcs.updated(
                        0,
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
                    updateMidpointFromMovingEnd(
                        point,
                        previousPoint,
                        arcs[previousArcIndex].middlePoint,
                        newPoint
                    )
                val newPreviousCircle =
                    //                    circleByChordEndsAndMidArc(previousPoint, newPoint, newMidpoint)
                    computeCircleBy3Points(previousPoint, newMidpoint, newPoint) as? Circle
                val newPreviousStartAngle =
                    if (newPreviousCircle is Circle)
                        newPreviousCircle.calculateStartAngle(previousPoint)
                    else 0.0
                copy(
                    vertices = vertices.updated(vertexIndex, newVertex),
                    arcs = arcs.updated(
                        previousArcIndex, Arc(
                            circle = newPreviousCircle,
                            middlePoint = newMidpoint,
                            startAngle = newPreviousStartAngle,
                            sweepAngle = arcs[previousArcIndex].sweepAngle
                        )
                    ),
                )
            }
            else -> { // backward + forward, move within the chain
                val newPoint = newVertex.point
                val previousArcIndex =
                    (vertexIndex - 1).mod(arcs.size) // not the last arc with free end
                require(vertexIndex < arcs.size) { "this case should have been handled within detached end" }
                val nextArcIndex = vertexIndex // not the first arc with free start
                val point = vertices[vertexIndex].point
                val previousPoint = previousVertex(vertexIndex).point
                val nextPoint = nextVertex(vertexIndex).point
                val newPreviousMidpoint = updateMidpointFromMovingEnd(
                    point, previousPoint, arcs[previousArcIndex].middlePoint, newPoint
                )
                val newPreviousCircle =
                    //                    circleByChordEndsAndMidArc(previousPoint, newPoint, newPreviousMidpoint)
                    computeCircleBy3Points(previousPoint, newPreviousMidpoint, newPoint) as? Circle
                val newPreviousStartAngle =
                    newPreviousCircle?.calculateStartAngle(previousPoint) ?: 0.0
                val newNextMidpoint =
                    updateMidpointFromMovingEnd(
                        point,
                        nextPoint,
                        arcs[nextArcIndex].middlePoint,
                        newPoint
                    )
                val newNextCircle =
                    //                    circleByChordEndsAndMidArc(newPoint, nextPoint, newNextMidpoint)
                    computeCircleBy3Points(newPoint, newNextMidpoint, nextPoint) as? Circle
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
    }

    // TODO: snap to line and to smooth-start and smooth-end (tangential touch)
    fun updateMidpoint(
        arcIndex: Int,
        midpointSnap: PointSnapResult
    ): PartialArcPath {
        val newMidpoint = midpointSnap.result
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
                    midpointSnap = midpointSnap,
                    startAngle = newStartAngle,
                    sweepAngle = newSweepAngle,
                )
            ),
        )
    }

    fun realignGrabbedMidpoint(): PartialArcPath = copy(
        arcs =
            if (focus is Focus.MidPoint && arcs[focus.arcIndex].midpointSnap is PointSnapResult.Free) {
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
        alignmentLines = emptyList(),
    )

    fun unFocus(): PartialArcPath = copy(
        focus = null,
        alignmentLines = emptyList(),
    )

    /** fuse vertex `firstVertexIndex+1` into [firstVertexIndex] */
    fun fuseSubsequentVertices(firstVertexIndex: Int): PartialArcPath =
        if (!isClosed && firstVertexIndex > arcs.lastIndex)
            connectLastToFirst().collapseArc(firstVertexIndex)
        else
            collapseArc(firstVertexIndex)

    /** fuse arc-end vertex into arc-start */
    fun collapseArc(arcIndex: Int): PartialArcPath {
        val nextVertexIndex = (arcIndex + 1).mod(vertices.size)
        return if (arcIndex == arcs.lastIndex) {
            copy(
                vertices = vertices.withoutElementAt(nextVertexIndex),
                arcs = arcs.dropLast(1),
            )
        } else {
            val nextArc = arcs[arcIndex + 1]
            val nextArcStart = arcIndex2startVertex(arcIndex).point
            val oldNextArcEnd = arcIndex2startVertex(arcIndex + 1).point
            val nextArcEnd = arcIndex2endVertex(arcIndex + 1).point
            val newNextArc = when (val snap = nextArc.midpointSnap) {
                is PointSnapResult.Free -> if (nextArc.circle == null) {
                    Arc(circle = null, middlePoint = nextArcStart.middle(nextArcEnd))
                } else {
                    val sr = computeSagittaRatio(nextArc.circle, nextArcStart, oldNextArcEnd)
                    val newCircle = computeCircleBy2PointsAndSagittaRatio(
                        SagittaRatioParameters(sr),
                        nextArcStart, nextArcEnd
                    ) as? Circle
                    val newStartAngle =
                        newCircle?.calculateStartAngle(nextArcStart) ?: 0.0
                    val newSweepAngle =
                        newCircle?.calculateSweepAngle(nextArcStart, snap.result, nextArcEnd) ?: 0.0
                    Arc(
                        circle = newCircle, midpointSnap = snap,
                        startAngle = newStartAngle, sweepAngle = newSweepAngle
                    )
                }
                else -> {
                    val newCircle =
                        computeCircleBy3Points(nextArcStart, snap.result, nextArcEnd) as? Circle
                    val newStartAngle =
                        newCircle?.calculateStartAngle(nextArcStart) ?: 0.0
                    val newSweepAngle =
                        newCircle?.calculateSweepAngle(nextArcStart, snap.result, nextArcEnd) ?: 0.0
                    Arc(
                        circle = newCircle, midpointSnap = snap,
                        startAngle = newStartAngle, sweepAngle = newSweepAngle
                    )
                }
            }
            copy(
                vertices = vertices.withoutElementAt(nextVertexIndex),
                arcs = arcs.withoutElementAt(arcIndex).updated(arcIndex, newNextArc),
            )
        }
    }

    fun connectLastToFirst(): PartialArcPath = copy(
        arcs = arcs + Arc(
            circle = null,
            middlePoint = vertices.last().point.middle(vertices.first().point)
        ),
        isClosed = true,
    )

    fun removeVertex(vertexIndex: Int): PartialArcPath {
        require(vertices.size > 1) { "Cannot remove the last vertex" }
        return when (vertexIndex) {
            0 ->
                if (isClosed) copy(
                    vertices = vertices.drop(1),
                    // replace prev arc with a line segment
                    arcs = arcs.slice(1 until arcs.lastIndex) + Arc(
                        circle = null,
                        middlePoint = vertices.last().point.middle(vertices[1].point),
                    )
                )
                else copy(
                    vertices = vertices.drop(1),
                    arcs = arcs.drop(1),
                )
            vertices.lastIndex ->
                if (isClosed) copy(
                    vertices = vertices.dropLast(1),
                    arcs = listOf(Arc(
                        circle = null,
                        middlePoint = vertices.first().point.middle(
                            vertices[vertexIndex - 1].point
                        )
                    )) + arcs.slice(1 until arcs.lastIndex),
                )
                else copy( // deleting the last vertex, with no subsequent arc
                    vertices = vertices.dropLast(1),
                    arcs = arcs.dropLast(1),
                )
            else -> copy( // atp: 1 <= vertexIndex < vertices.lastIndex
                vertices = vertices.withoutElementAt(vertexIndex),
                arcs = arcs.take(vertexIndex - 1) + Arc(
                    circle = null,
                    middlePoint = vertices[vertexIndex - 1].point.middle(
                        arcIndex2endVertex(vertexIndex).point
                    )
                ) + arcs.drop(vertexIndex + 1),
            )
        }
    }

    companion object {
        const val ENABLE_ALIGNMENT_SNAPPING = true
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
    return Circle(
        ox, oy, r,
        isCCW = Point.calculateOrientation(chordStart, midArc, chordEnd),
    )
}