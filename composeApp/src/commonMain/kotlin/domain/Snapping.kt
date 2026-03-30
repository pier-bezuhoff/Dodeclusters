package domain

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect
import core.geometry.Circle
import core.geometry.CircleOrLine
import core.geometry.CircleOrLineOrPoint
import core.geometry.EPSILON
import core.geometry.GCircle
import core.geometry.Line
import core.geometry.Point
import core.geometry.closestPerpendicularPoint
import core.geometry.perpendicularDistance
import core.geometry.translatedUntilTangency
import core.geometry.ConcreteArcPath
import core.geometry.GCircleOrConcreteAcPath
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

@Immutable
sealed interface PointSnapResult {
    val result: Point

    sealed interface PointToPoint : PointSnapResult
    sealed interface PointToCircle : PointSnapResult
    sealed interface PointToArcPath : PointSnapResult
    sealed interface PointAlignment : PointSnapResult

    data class Free(
        override val result: Point,
    ) : PointToPoint, PointToCircle, PointToArcPath, PointAlignment

        data class Eq(
        override val result: Point,
        val pointIndex: Ix,
    ) : PointToPoint

    data class Incidence(
        override val result: Point,
        val circleIndex: Ix,
    ) : PointToCircle

    data class Intersection(
        override val result: Point,
        val circle1Index: Ix,
        val circle2index: Ix,
    ) : PointToCircle {
        init {
            require(circle1Index != circle2index)
        }
    }

    data class ArcPathIncidence(
        override val result: Point,
        val arcPathIndex: Int,
        val arcIndex: Int,
    ) : PointToArcPath

    data class HorizontalAlignment(
        override val result: Point,
        val x: Double,
    ) : PointAlignment

    data class VerticalAlignment(
        override val result: Point,
        val y: Double,
    ) : PointAlignment

    data class HorizontalAndVerticalAlignment(
        override val result: Point,
        val x: Double,
        val y: Double,
    ) : PointAlignment

    val isFree: Boolean get() =
        this is Free

    fun toArgPoint(): Arg.Point = when (this) {
        is Eq -> Arg.PointIndex(this.pointIndex)
        is Free -> Arg.PointXY(this.result)
        else -> Arg.PointXY(this.result)
    }
}

// MAYBE: fuse with PointSnapResult
//  into SnapResult<O: GCircle>
@Immutable
sealed interface CircleSnapResult {
    val result: CircleOrLine
    data class Free(override val result: CircleOrLine) : CircleSnapResult
    // Equality case is practically inapplicable to circle-circle snaps
    data class Tangent(override val result: CircleOrLine, val tangentIndex: Ix) : CircleSnapResult
    data class BiTangent(
        override val result: CircleOrLine,
        val tangent1Index: Ix,
        val tangent2Index: Ix,
    ) : CircleSnapResult {
        init {
            tangent1Index != tangent2Index
        }
    }
}

object Snapping {
    /** Snaps to 45 degree marks [snapMarkDeg] when closer than 5% [angleSnapPercent] */
    fun snapAngle(
        angleDeg: Double,
        angleSnapPercent: Double = 5.0,
        snapMarkDeg: Double = 45.0
    ): Double {
        if (abs(angleDeg) <= snapMarkDeg/2)
            return angleDeg // no snapping to 0
        val mod = angleDeg.mod(snapMarkDeg) // mod is always >= 0
        val div = (angleDeg - mod)/snapMarkDeg
        val threshold = snapMarkDeg*angleSnapPercent/100
        return if (mod < threshold) {
            div * snapMarkDeg
        } else if ((snapMarkDeg - mod) < threshold) {
            (div + 1) * snapMarkDeg
        } else {
            angleDeg
        }
    }

    /** additionally chooses first closest point within epsilon-vicinity of the minimum */
    fun snapPointToPoints(
        point: Point,
        allObjects: List<*>,
        snapDistance: Double,
        excludedIndices: Set<Ix> = emptySet(),
    ): PointSnapResult.PointToPoint {
        val withinSnapDistance = allObjects.mapIndexed { ix, o ->
            if (ix in excludedIndices || o !is Point)
                ix to Double.POSITIVE_INFINITY
            else
                ix to o.distanceFrom(point)
        }.filter { (_, d) -> d <= snapDistance }
        if (withinSnapDistance.isEmpty())
            return PointSnapResult.Free(point)
        // because of triple-intersects, we want to resolve them uniformly
        // regardless of position, so the natural point order is an easy choice.
        val minDistance = withinSnapDistance.minOf { it.second }
        val oldestButCloseEnough =
            withinSnapDistance.first { it.second < minDistance + EPSILON }
        val ix = oldestButCloseEnough.first
        return PointSnapResult.Eq(allObjects[ix] as Point, pointIndex = ix)
    }

    fun snapPointToPointsSimple(
        point: Point,
        points: List<Point>,
        snapDistance: Double,
    ): PointSnapResult.PointToPoint {
        val closestPointIndex = points.bottomIndexBy(
            measurer = { point.distanceFrom(it) },
            measureFilter = { it <= snapDistance },
        )
        return if (closestPointIndex == null)
            PointSnapResult.Free(point)
        else
            PointSnapResult.Eq(points[closestPointIndex], closestPointIndex)
    }

    /** Project [point] onto the closest circle/line among [allObjects] that
     * are closer than [snapDistance] from it.
     *
     * @param[intersectionTolerance] how much easier it is to snap to an intersection than
     * to an individual circle
     * */
    fun snapPointToCircles(
        point: Point,
        allObjects: List<*>,
        snapDistance: Double,
        intersectionTolerance: Double = 1.5,
        excludedIndices: Set<Ix> = emptySet(),
    ): PointSnapResult.PointToCircle {
        val closestCircles: List<Ix> = allObjects.bottom2IndicesBy(
            measurer = { o ->
                if (o is CircleOrLine)
                    o.distanceFrom(point)
                else
                    Double.POSITIVE_INFINITY
            },
            indexFilter = { it !in excludedIndices },
            measureFilter = { it <= snapDistance },
        ) // get 2 closest circles
        return if (closestCircles.isEmpty()) {
            PointSnapResult.Free(point)
        } else if (closestCircles.size == 1) {
            val ix = closestCircles.first()
            val circle = allObjects[ix] as CircleOrLine
            PointSnapResult.Incidence(
                circle.project(point),
                circleIndex = ix
            )
        } else {
            // NOTE: triple intersections introduce chaos
            val (ix1, ix2) = closestCircles
            val c1 = allObjects[ix1] as CircleOrLine
            val c2 = allObjects[ix2] as CircleOrLine
            val intersections = Circle.calculateIntersectionPoints(c1, c2)
            if (intersections.isEmpty()) {
                PointSnapResult.Incidence(
                    c1.project(point),
                    circleIndex = ix1
                )
            } else {
                val (closestIntersection, distance) = intersections
                    .map { it to point.distanceFrom(it) }
                    .minByOrNull { (_, d) -> d }!!
                if (distance <= intersectionTolerance * snapDistance) {
                    val sortedIxs = listOf(ix1, ix2).sorted()
                    PointSnapResult.Intersection(
                        closestIntersection,
                        circle1Index = sortedIxs[0],
                        circle2index = sortedIxs[1]
                    )
                } else {
                    PointSnapResult.Incidence(
                        c1.project(point),
                        circleIndex = ix1
                    )
                }
            }
        }
    }

    fun snapPointToArcPaths(
        point: Point,
        allObjects: List<*>,
        snapDistance: Double,
        excludedIndices: Set<Int> = emptySet(),
    ): PointSnapResult.PointToArcPath {
        var arcPathIndex: Int? = null
        var distance: Double = Double.POSITIVE_INFINITY
        var snappedPoint = point
        var snappedArcIndex = 0
        for (i in allObjects.indices) {
            if (i in excludedIndices)
                continue
            val concreteArcPath = allObjects[i] as? ConcreteArcPath ?: continue
            val (arcIndex, projectedPoint, _) = concreteArcPath.project(point)
            val d = point.distanceFrom(projectedPoint)
            if (d < snapDistance) {
                if (arcPathIndex == null || d < distance) {
                    arcPathIndex = i
                    distance = d
                    snappedPoint = projectedPoint
                    snappedArcIndex = arcIndex
                }
            }
        }
        return if (arcPathIndex == null)
            PointSnapResult.Free(point)
        else
            PointSnapResult.ArcPathIncidence(
                result = snappedPoint,
                arcPathIndex = arcPathIndex,
                arcIndex = snappedArcIndex
            )
    }

    fun snapAlignPointToPointsVerticallyOrHorizontally(
        point: Point,
        points: List<Point>,
        snapDistance: Double,
    ): PointSnapResult.PointAlignment {
        val closestHorizontalPointIndex = points.bottomIndexBy(
            measurer = { p -> abs(point.x - p.x) },
            measureFilter = { it <= snapDistance }
        )
        val closestVerticalPointIndex = points.bottomIndexBy(
            measurer = { p -> abs(point.y - p.y) },
            measureFilter = { it <= snapDistance }
        )
        val x1 = closestHorizontalPointIndex?.let { points[it].x }
        val y1 = closestVerticalPointIndex?.let { points[it].y }
        return when (x1) {
            null ->
                if (y1 == null)
                    PointSnapResult.Free(point)
                else
                    PointSnapResult.VerticalAlignment(point.copy(y = y1), y1)
            else ->
                if (y1 == null)
                    PointSnapResult.HorizontalAlignment(point.copy(x = x1), x1)
                else
                    PointSnapResult.HorizontalAndVerticalAlignment(
                        point.copy(x = x1, y = y1),
                        x = x1, y = y1,
                    )
        }
    }

    // NOTE: dont forget to exclude [circle], its immediate parents and all children from snappanbles
    fun snapCircleToCircles(
        circle: CircleOrLine,
        allObjects: List<*>,
        snapDistance: Double,
        bitangentTolerance: Double = 1.2,
        visibleRect: Rect? = null,
        excludedIndices: Set<Int> = emptySet(),
    ): CircleSnapResult {
        val closestCircles: List<Ix> = allObjects.bottom2IndicesBy(
            measurer = { o ->
                if (o is CircleOrLineOrPoint)
                    abs(o.perpendicularDistance(circle))
                else
                    Double.POSITIVE_INFINITY
            },
            indexFilter = { it !in excludedIndices },
            elementFilter = { o ->
                o is CircleOrLineOrPoint &&
                    (visibleRect == null || visibleRect.contains(
                        circle.closestPerpendicularPoint(o).toOffset()
                    ))
            },
            measureFilter = { it <= snapDistance },
        ) // get 2 closest circles
        return when {
            closestCircles.isEmpty() -> {
                CircleSnapResult.Free(circle)
            }
            circle is Line || closestCircles.size == 1  -> {
                // line cannot snap to 2 objects (without rotation)
                val ix1 = closestCircles.first()
                val c1 = allObjects[ix1] as CircleOrLineOrPoint
                val newCircle = circle.translatedUntilTangency(c1)
                CircleSnapResult.Tangent(newCircle, ix1)
            }
            else -> { // 2 tangents
                val (ix1, ix2) = closestCircles
                val c = circle as Circle
                val c1 = allObjects[ix1] as CircleOrLineOrPoint
                val c2 = allObjects[ix2] as CircleOrLineOrPoint
                val newCircle = c.translatedUntilBiTangency(c1, c2)
                if (newCircle == null ||
                    newCircle.distanceBetweenCenters(c) >= bitangentTolerance * snapDistance
                ) {
                    return CircleSnapResult.Tangent(c.translatedUntilTangency(c1), ix1)
                }
                CircleSnapResult.BiTangent(newCircle, ix1, ix2)
            }
        }
    }

// from pencil thru p1^p2 choose the one that touches/snaps to the closest of [circles]
    /** Construct a circle thru [p1] and [p2] that snaps to [circles] from
     * the initial circle `p1^p2^freePoint` */
    fun snapCircleThru3PointsToCircles(p1: Point, p2: Point, freePoint: Point, circles: List<CircleOrLine>): CircleOrLine {
        // circle -> circle
        // line -> circle
        // circle -> line
        // line to line: no snapping
        // 2 -> snap to intersection
        // NOTE: involves Apollonian problem with 2 cycles degenerated to points
        // MAYBE: create separate instrument for Apollonian triad via Lie cycles
        TODO()
    }

}

// MAYBE: use -1 for intersections
private fun distance(c1: CircleOrLine, c2: CircleOrLine): Double =
    when {
        c1 is Circle && c2 is Circle -> {
            val d = hypot(c2.x - c1.x, c2.y - c1.y)
            if (d >= c1.radius + c2.radius) { // side-by-side case o-o
                d - c1.radius - c2.radius
            } else { // one inside the other case (o)
                val minR = min(c1.radius, c2.radius)
                val maxR = max(c1.radius, c2.radius)
                val distance = maxR - (d + minR)
                if (distance >= 0.0)
                    distance
                else
                    0.0 // intersections
            }
        }
        c1 is Line && c2 is Line -> {
            val l1 = c1.normalizedNoDirection()
            val l2 = c2.normalizedNoDirection()
            if (l1 isCollinearTo l2)
                abs(l1.c - l2.c)
            else
                0.0
        }
        c1 is Line && c2 is Circle -> {
            val d = c1.distanceFrom(c2.centerPoint)
            if (d > c2.radius)
                d - c2.radius
            else
                0.0
        }
        c1 is Circle && c2 is Line -> {
            val d = c2.distanceFrom(c1.centerPoint)
            if (d > c1.radius)
                d - c1.radius
            else
                0.0
        }
        else -> never("$c1, $c2")
    }

