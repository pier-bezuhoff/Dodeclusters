package core.geometry

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import domain.angleRad
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// MAYBE: use Offsets and Floats atp
/** [domain.expressions.ArcPath] representation for calculation */
@Immutable
@Serializable
data class ConcreteArcPath(
    val vertices: List<Point>,
    val arcs: List<Arc>,
    val isClosed: Boolean,
) : GCircleOrConcreteAcPath, Region {
    /**
     * @param[arcIndex] index within [domain.expressions.ArcPath.arcs], `null` means
     * several arcs were fused because of `null` vertices
     * @param[circleOrLine] `null` also means line
     * @param[startAngle] clockwise from the East, in `[0; 2*PI)`
     * @param[sweepAngle] clockwise, `(-2*PI; 2*PI)`
     * @param[freeMidpoint] for sagitta-defined arcs
     */
    @Immutable
    @Serializable
    data class Arc(
        val arcIndex: Int?,
        val circleOrLine: CircleOrLine?,
        val startAngle: Double = 0.0,
        val sweepAngle: Double = 0.0,
        val freeMidpoint: Point? = null,
    )

    inline fun forEachArc(
        action: (arcIndex: Int, arc: Arc, arcStart: Point, arcEnd: Point) -> Unit,
    ) {
        for (arcIndex in arcs.indices) {
            action(
                arcIndex,
                arcs[arcIndex],
                vertices[arcIndex],
                vertices[(arcIndex + 1).mod(vertices.size)]
            )
        }
    }

    fun toRect(): Rect {
        var left = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        var top = Float.POSITIVE_INFINITY
        var bottom = Float.NEGATIVE_INFINITY
        for (point in vertices) {
            val x = point.x.toFloat()
            val y = point.y.toFloat()
            left = min(left, x)
            right = max(right, x)
            top = min(top, y)
            bottom = max(bottom, y)
        }
        forEachArc { _, arc, start, end ->
            when (val circle = arc.circleOrLine) {
                is Circle -> {
                    val circleLeft = Point(circle.x - circle.radius, circle.y)
                    val circleRight = Point(circle.x + circle.radius, circle.y)
                    val circleTop = Point(circle.x, circle.y - circle.radius)
                    val circleBottom = Point(circle.x, circle.y + circle.radius)
                    if (circle.agreesWithOrientation(start, circleLeft, end))
                        left = min(left, circleLeft.x.toFloat())
                    if (circle.agreesWithOrientation(start, circleRight, end))
                        right = max(right, circleRight.x.toFloat())
                    if (circle.agreesWithOrientation(start, circleTop, end))
                        top = min(top, circleTop.y.toFloat())
                    if (circle.agreesWithOrientation(start, circleBottom, end))
                        bottom = max(bottom, circleBottom.y.toFloat())
                }
                else -> {}
            }
        }
        return Rect(left, top, right, bottom)
    }

    override fun distanceFrom(point: Point): Double {
        var distance = vertices.minOfOrNull {
            it.distanceFrom(point)
        } ?: Double.POSITIVE_INFINITY
        forEachArc { _, arc, start, end ->
            when (val circle = arc.circleOrLine) {
                is CircleOrLine -> {
                    val closest = circle.project(point)
                    // another projection onto the circle is always farther than both of the arc ends
                    val onTheArc =
                        circle.agreesWithOrientation(start, closest, end)
                    if (onTheArc) {
                        val d = point.distanceFrom(closest)
                        distance = min(distance, d)
                    }
                }
                null -> {}
            }
        }
        return distance
    }

    data class ProjectionResult(
        val projectedPoint: Point,
        val arcIndex: Int,
        val arcPercentage: Double,
    )

    /** assumes [vertices]`.size >= 1` */
    fun project(point: Point): ProjectionResult {
        var index = 0
        var projectedPoint = vertices.first()
        var arcPercentage = 0.0
        var distance = Double.POSITIVE_INFINITY
        for (arcIndex in arcs.indices) {
            val arc = arcs[arcIndex]
            when (val circleOrLine = arc.circleOrLine) {
                is Circle -> {
                    if (arc.sweepAngle == 0.0)
                        continue
                    val onCirclePoint = circleOrLine.project(point)
                    val angle = circleOrLine.calculateStartAngle(onCirclePoint)
                    val closestOnArcAngle = Circle.coerceAngle(angle, arc.startAngle, arc.sweepAngle)
                    val onArcPoint = circleOrLine.angle2point(closestOnArcAngle)
                    val d = point.distanceFrom(onArcPoint)
                    if (d < distance) {
                        index = arcIndex
                        distance = d
                        projectedPoint = onArcPoint
                        arcPercentage = (closestOnArcAngle - arc.startAngle)/arc.sweepAngle
                    }
                }
                is Line -> {
                    val onLineOrder = circleOrLine.point2order(point)
                    val startOrder = circleOrLine.point2order(vertices[arcIndex])
                    val endOrder = circleOrLine.point2order(vertices[
                        (arcIndex + 1).mod(vertices.size)
                    ])
                    if (startOrder == endOrder)
                        continue
                    val closestOrder = Line.coerceOrder(onLineOrder, startOrder, endOrder)
                    val onArcPoint = circleOrLine.order2point(closestOrder)
                    val d = point.distanceFrom(onArcPoint)
                    if (d < distance) {
                        index = arcIndex
                        distance = d
                        projectedPoint = onArcPoint
                        arcPercentage = (closestOrder - startOrder)/(endOrder - startOrder)
                    }
                }
                null -> {}
            }
        }
        return ProjectionResult(
            projectedPoint = projectedPoint,
            arcIndex = index,
            arcPercentage = arcPercentage,
        )
    }

    fun scaled00(zoom: Double): ConcreteArcPath = copy(
        vertices = vertices.map { it.scaled00(zoom) },
        arcs = arcs.map { arc ->
            arc.copy(
                circleOrLine = arc.circleOrLine?.scaled00(zoom),
                freeMidpoint = arc.freeMidpoint?.scaled00(zoom),
            )
        }
    )

    fun translated(vector: Offset): ConcreteArcPath = copy(
        vertices = vertices.map { it.translated(vector) },
        arcs = arcs.map { arc ->
            arc.copy(
                circleOrLine = arc.circleOrLine?.translated(vector),
                freeMidpoint = arc.freeMidpoint?.translated(vector),
            )
        }
    )

    // algo based on https://web.archive.org/web/20130126163405/http://geomalgorithms.com/a03-_inclusion.html
    // and https://stackoverflow.com/a/33974251/7143065
    // more general algo: https://arxiv.org/abs/2403.17371
    /**
     * Checks scan-ray x>point.x, y=point.y intersections against every arc/segment,
     * up-crossing is +1, down-crossing is -1.
     * @return `0` if the point is outside (#up crossings = #down crossings),
     * `+1` if it's to the left side (inside CCW path),
     * `-1` if it's to the right side (inside CW path)
     */
    private fun calculateWindingNumber(point: Point): Int {
        if (!isClosed)
            return 0
        var windingNumber = 0
        val (x, y) = point
        for (arcIndex in arcs.indices) {
            val arc = arcs[arcIndex]
            val arcStart = vertices[arcIndex]
            val arcEnd = vertices[(arcIndex + 1).mod(vertices.size)]
            // we ignore a number of edge cases
            when (val circle = arc.circleOrLine) {
                is Circle -> { // there actually is a closed formula for arc winding number, tho it's not nice
                    val (cx, cy, r) = circle
                    // rect-based quick reject of eastward ray
                    if (y < cy - r || cx + r < x || cy + r < y)
                        continue
                    val y0 = y - cy
                    val discriminant = r*r - y0*y0 // eastward ray - circle intersection
                    // we don't consider y=x^3 -like ray intersection with a vertex
                    if (discriminant <= 0)
                        continue
                    val x0 = x - cx
                    /** intersection.x within the circle-centered coordinate system */
                    val ix = sqrt(discriminant)
                    if (ix < x0) // eastmost intersection should be easter than (x0,y0)
                        continue
                    val candidate1 = Point(cx + ix, y)
                    if (circle.agreesWithOrientation(
                        arcStart, candidate1, arcEnd
                    ))
                        windingNumber += if (circle.isCCW) 1 else -1
                    if (x0 < -ix) {
                        val candidate2 = Point(cx - ix, y)
                        if (circle.agreesWithOrientation(
                            arcStart, candidate2, arcEnd
                        ))
                            windingNumber -= if (circle.isCCW) 1 else -1
                    }
                }
                else -> {
                    // rect-based quick reject of eastward ray
                    if (y < arcStart.y && y < arcEnd.y ||
                        arcStart.y < y && arcEnd.y < y ||
                        arcStart.x < x && arcEnd.x < x
                    )
                        continue
                    if (arcStart.y < y) { // downward crossing
                        // quick reject implies: startVertex.y < y <= endVertex.y
                        // start->end x start->point
                        val cross = Point.cross(arcStart, arcEnd, point)
                        if (cross < 0) // right side
                            windingNumber -= 1
                    } else { // upward crossing
                        // quick reject implies: endVertex.y <= y <= startVertex.y
                        val cross = Point.cross(arcStart, arcEnd, point)
                        if (cross > 0) // left side
                            windingNumber += 1
                    }
                }
            }
        }
        return windingNumber
    }

    // discriminates CCW vs CW
    /** Cumulative vector turning CW angle in radians, as it travels along this path.
     * Aka 'turning number' `*TAU` for loops.
     *
     * For non-intersecting loops it's either `+TAU` (clockwise)
     * or `-TAU` (counterclockwise); can be 0 for self-intersectin loops (eg 8-shape).
     */
    fun calculateTurningAngle(): Double {
        var angle = 0.0
        arcs.forEachIndexed { arcIndex, arc ->
            // vertex angle + arc sweep angle
            if (isClosed || arcIndex != 0) {
                val previousArcIndex = (arcIndex - 1).mod(arcs.size)
                val vertex = vertices[arcIndex].toOffset()
                val incomingNormalVector = when (val previousCircle = arcs[previousArcIndex].circleOrLine) {
                    is Circle ->
                        circleNormal(previousCircle, vertex)
                    else -> {
                        val previousVertex = vertices[previousArcIndex].toOffset()
                        lineSegmentNormal(previousVertex, vertex)
                    }
                }
                val outgoingNormalVector = when (val circle = arc.circleOrLine) {
                    is Circle ->
                        circleNormal(circle, vertex)
                    else -> {
                        val nextVertex = vertices[(arcIndex + 1).mod(vertices.size)].toOffset()
                        lineSegmentNormal(vertex, nextVertex)
                    }
                }
                val vertexAngle = incomingNormalVector.angleRad(outgoingNormalVector)
                angle += vertexAngle
//                println("#$arcIndex: sweep angle = ${arc.sweepAngle.toDegree()}, vertex angle = ${vertexAngle.toDegree()}")
            }
            angle += arc.sweepAngle // sweep is 0 for lines
        }
        return angle
    }

    fun isClockwise(): Boolean =
        isClosed && calculateTurningAngle() > PI

    // better test: if the first scanline intersection at y=average vertex y is downward
    fun isCounterclockwise(): Boolean =
        isClosed && calculateTurningAngle() < -PI

    override fun hasInside(point: Point): Boolean =
        calculateWindingNumber(point) != 0

    override fun getPointLocation(point: Point): Region.PointLocation =
        if (distanceFrom(point) < EPSILON)
            Region.PointLocation.BORDERING
        else if (calculateWindingNumber(point) == 0)
            Region.PointLocation.OUTSIDE
        else
            Region.PointLocation.INSIDE

    override fun getRegionLocation(region: Region): Region.RegionLocation =
        when (region) {
            is Circle -> {
                if (region.isCCW)
                    TODO()
                else // cannot be contained inside
                    TODO()
            }
            is Line -> {
                // cannot be contained inside
                TODO()
            }
            is ConcreteArcPath -> {
                TODO()
            }
        }

    // reference: https://en.wikipedia.org/wiki/Shoelace_formula
    /** Area of the polygon made of vertices */
    fun calculateVertexArea(): Double {
        if (!isClosed)
            return 0.0
        var sum = 0.0
        for (i in vertices.indices) {
            val prevX = vertices[(i - 1).mod(vertices.size)].x
            val nextX = vertices[(i + 1).mod(vertices.size)].x
            sum += vertices[i].y * (prevX - nextX)
        }
        return abs(sum)/2.0
    }
}

// normal points to the left of direction
private fun circleNormal(circle: Circle, at: Offset): Offset =
    if (circle.isCCW)
        circle.center - at
    else
        at - circle.center

private fun lineSegmentNormal(start: Offset, end: Offset): Offset =
    Offset(
        end.y - start.y,
        -(end.x - start.x)
    )
