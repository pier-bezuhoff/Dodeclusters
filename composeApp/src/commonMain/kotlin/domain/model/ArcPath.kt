package domain.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import core.geometry.Circle
import core.geometry.CircleOrLine
import core.geometry.GCircle
import core.geometry.Point
import domain.ColorAsCss
import domain.Ix
import domain.expressions.SagittaRatioParameters
import domain.expressions.computeCircleBy2PointsAndSagittaRatio
import domain.expressions.computeCircleBy3Points
import domain.filterIndices
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.min

@Immutable
@Serializable
sealed interface Arc {
    /**
     * @property[sagittaRatio] see computeCircleBy2PointsAndSagittaRatio
     */
    @Serializable
    @SerialName("ArcBy2Points")
    data class By2Points(
        val sagittaRatio: Double,
    ) : Arc
    @Serializable
    @SerialName("ArcBy3Points")
    data class By3Points(
        val middlePointIndex: Ix,
    ) : Arc

    companion object {
        val LineSegment = By2Points(0.0)
    }
}

/** Blueprint for a dynamic path made of indices of specified [vertices] + [arcs] */
@Immutable
@Serializable
@SerialName("ArcPath")
sealed interface ArcPath {
    val vertices: List<Ix>
    val arcs: List<Arc>
    val borderColor: ColorAsCss?

    @Stable
    val dependencies: Set<Ix> get() =
        vertices.toSet() + arcs.filterIsInstance<Arc.By3Points>().map { it.middlePointIndex }

    /** Looping arc-path */
    @Immutable
    @Serializable
    @SerialName("ClosedArcPath")
    data class Closed(
        override val vertices: List<Ix>,
        override val arcs: List<Arc>,
        override val borderColor: ColorAsCss? = null,
        val fillColor: ColorAsCss? = null,
    ) : ArcPath {

        init {
            // Vertices - Edges + Faces = 2
            require(vertices.size - arcs.size + 2 == 2)
        }
    }

    /** Non-looping arc-path */
    @Immutable
    @Serializable
    @SerialName("OpenArcPath")
    data class Open(
        override val vertices: List<Ix>,
        override val arcs: List<Arc>,
        override val borderColor: ColorAsCss? = null,
    ) : ArcPath {

        init {
            // Vertices - Edges + Faces = 2
            require(vertices.size - arcs.size + 1 == 2)
        }
    }
}

/** [ArcPath] representation for calculation */
@Immutable
data class ConcreteArcPath(
    val vertices: List<Point>,
    val arcs: List<Arc>,
    val isClosed: Boolean,
    val borderColor: ColorAsCss? = null,
    val fillColor: ColorAsCss? = null,
) {
    /**
     * @property[arcIndex] index within [ArcPath.arcs], `null` means several arcs are fused
     * because of `null` vertices
     * @property[circleOrLine] `null` means line
     * @property[freeMidpoint] for sagitta-defined arcs
     */
    @Immutable
    data class Arc(
        val arcIndex: Int?,
        val circleOrLine: CircleOrLine?,
        val startAngle: Double = 0.0,
        val sweepAngle: Double = 0.0,
        val freeMidpoint: Point? = null,
    )

    fun distanceFrom(point: Point): Double {
        var distance = vertices.minOfOrNull {
            it.distanceFrom(point)
        } ?: Double.POSITIVE_INFINITY
        arcs.forEachIndexed { i, arc ->
            val start = vertices[i]
            val end = vertices[(i + 1).mod(vertices.size)]
            when (val circle = arc.circleOrLine) {
                is CircleOrLine -> {
                    val closest = circle.project(point)
                    // another projection onto circle is always farther than arc ends
                    val onTheArc =
                        circle.pointIsInBetween(start, closest, end)
                    if (onTheArc) {
                        distance = min(distance, arc.circleOrLine.distanceFrom(point))
                    }
                }
                null -> {}
            }
        }
        return distance
    }
}

fun Arc.toCircleOrLine(
    start: Point,
    end: Point,
    objects: List<GCircle?>,
): CircleOrLine? = when(this) {
    // works for infinite points
    is Arc.By2Points -> {
        computeCircleBy2PointsAndSagittaRatio(SagittaRatioParameters(sagittaRatio), start, end)
    }
    is Arc.By3Points -> {
        val middle = objects[middlePointIndex] as? Point
        if (middle == null)
            null
        else
            computeCircleBy3Points(start, middle, end) as? CircleOrLine
    }
}

fun ArcPath.toConcrete(objects: List<GCircle?>): ConcreteArcPath {
    val realVertices = mutableListOf<Point>()
    val realArcs = mutableListOf<ConcreteArcPath.Arc>()
    when (this) {
        is ArcPath.Closed -> {
            var arcStart: Point? = null
            for (i in vertices.indices) {
                val vertex = objects[vertices[i]] as? Point
                val nextVertex = objects[vertices[(i + 1).mod(vertices.size)]] as? Point
                when (vertex) {
                    null -> {
                        if (arcStart != null && nextVertex != null) {
                            realArcs.add(ConcreteArcPath.Arc(null, null))
                        }
                    }
                    else -> {
                        realVertices.add(vertex)
                        when (nextVertex) {
                            null -> {
                                arcStart = vertex
                            }
                            else -> {
                                val arc = arcs[i]
                                val circleOrLine = arc.toCircleOrLine(vertex, nextVertex, objects)
                                val circle = circleOrLine as? Circle
                                realArcs.add(ConcreteArcPath.Arc(
                                    arcIndex = i,
                                    circleOrLine = circleOrLine,
                                    startAngle = circle?.calculateStartAngle(vertex) ?: 0.0,
                                    sweepAngle = circle?.calculateSweepAngle(vertex, nextVertex) ?: 0.0,
                                    freeMidpoint =
                                        if (arc is Arc.By2Points && circleOrLine is CircleOrLine)
                                            circleOrLine.pointInBetween(vertex, nextVertex)
                                        else null,
                                ))
                            }
                        }
                    }
                }
            }
            return ConcreteArcPath(
                vertices = realVertices,
                arcs = realArcs,
                isClosed = true,
                borderColor = borderColor,
                fillColor = fillColor,
            )
        }
        is ArcPath.Open -> {
            var arcStart: Point? = null
            for (i in vertices.indices) {
                val vertex = objects[vertices[i]] as? Point
                val nextVertex =
                    if (i == vertices.lastIndex)
                        null
                    else
                        objects[vertices[i + 1]] as? Point
                when (vertex) {
                    null -> {
                        if (arcStart != null && nextVertex != null) {
                            realArcs.add(ConcreteArcPath.Arc(null, null))
                        }
                    }
                    else -> {
                        realVertices.add(vertex)
                        when (nextVertex) {
                            null -> {
                                arcStart = vertex
                            }
                            else -> {
                                val arc = arcs[i]
                                val circleOrLine = arc.toCircleOrLine(vertex, nextVertex, objects)
                                val circle = circleOrLine as? Circle
                                realArcs.add(ConcreteArcPath.Arc(
                                    arcIndex = i,
                                    circleOrLine = circleOrLine,
                                    startAngle = circle?.calculateStartAngle(vertex) ?: 0.0,
                                    sweepAngle = circle?.calculateSweepAngle(vertex, nextVertex) ?: 0.0,
                                    freeMidpoint =
                                        if (arc is Arc.By2Points && circleOrLine is CircleOrLine)
                                            circleOrLine.pointInBetween(vertex, nextVertex)
                                        else null,
                                ))
                            }
                        }
                    }
                }
            }
            return ConcreteArcPath(
                vertices = realVertices,
                arcs = realArcs,
                isClosed = false,
                borderColor = borderColor,
            )
        }
    }
}

inline fun ArcPath.reIndex(
    crossinline reIndexer: (Ix) -> Ix,
): ArcPath = when (this) {
    is ArcPath.Closed -> copy(
        vertices = vertices.map { reIndexer(it) },
        arcs = arcs.map { arc ->
            when (arc) {
                is Arc.By2Points -> arc
                is Arc.By3Points -> arc.copy(
                    middlePointIndex = reIndexer(arc.middlePointIndex)
                )
            }
        }
    )
    is ArcPath.Open -> copy(
        vertices = vertices.map { reIndexer(it) },
        arcs = arcs.map { arc ->
            when (arc) {
                is Arc.By2Points -> arc
                is Arc.By3Points -> arc.copy(
                    middlePointIndex = reIndexer(arc.middlePointIndex)
                )
            }
        }
    )
}

fun ArcPath.withoutPointsAt(indices: Set<Ix>): ArcPath? {
    if (indices.containsAll(vertices))
        return null
    return when (this) {
        is ArcPath.Closed -> {
            val deletedArcIndices = vertices.filterIndices { it in indices }.toSet()
            val newArcs: List<Arc> = arcs.mapIndexedNotNull { arcIndex, arc ->
                when {
                    // start is deleted -> delete arc
                    arcIndex in deletedArcIndices -> null
                    // end is deleted -> straighten
                    (arcIndex + 1).mod(vertices.size) in deletedArcIndices -> Arc.LineSegment
                    else -> arc
                }
            }
            copy(
                vertices = vertices - indices,
                arcs = newArcs,
            )
        }
        is ArcPath.Open -> {
            val deletedArcIndices = vertices.filterIndices { it in indices }.toSet()
            val remainingArcIndices = vertices.filterIndices { it !in indices }
            val newArcs: List<Arc> = arcs.mapIndexedNotNull { arcIndex, arc ->
                when {
                    // start is deleted -> delete arc
                    arcIndex in deletedArcIndices -> null
                    // end is deleted -> if new start exists straighten else delete
                    (arcIndex + 1).mod(vertices.size) in deletedArcIndices ->
                        if (remainingArcIndices.findLast { it < arcIndex } != null)
                            Arc.LineSegment
                        else null
                    else -> arc
                }
            }
            copy(
                vertices = vertices - indices,
                arcs = newArcs,
            )
        }
    }
}