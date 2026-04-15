package core.geometry

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import domain.angleRad
import kotlinx.serialization.Serializable
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
) : GCircleOrConcreteAcPath {
    /**
     * @property[arcIndex] index within [domain.expressions.ArcPath.arcs], `null` means several arcs are fused
     * because of `null` vertices
     * @property[circleOrLine] `null` means line
     * @property[freeMidpoint] for sagitta-defined arcs
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
        arcs.forEachIndexed { i, arc ->
            when (val circle = arc.circleOrLine) {
                is Circle -> {
                    val start = vertices[i]
                    val end = vertices[(i + 1).mod(vertices.size)]
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

    /**
     * assumes [vertices]`.size >= 1`
     * @return (arcIndex, projectedPoint, arcPercentage)
     */
    fun project(point: Point): Triple<Int, Point, Double> {
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
        return Triple(index, projectedPoint, arcPercentage)
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
    fun contains(point: Point): Boolean {
        if (!isClosed)
            return false
        var windingNumber = 0
        val (x, y) = point
        for (arcIndex in arcs.indices) {
            val arc = arcs[arcIndex]
            val startVertex = vertices[arcIndex]
            val endVertex = vertices[(arcIndex + 1).mod(vertices.size)]
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
                        startVertex, candidate1, endVertex
                    ))
                        windingNumber += if (circle.isCCW) 1 else -1
                    if (x0 < -ix) {
                        val candidate2 = Point(cx - ix, y)
                        if (circle.agreesWithOrientation(
                            startVertex, candidate2, endVertex
                        ))
                            windingNumber -= if (circle.isCCW) 1 else -1
                    }
                }
                else -> {
                    // rect-based quick reject of eastward ray
                    if (y < startVertex.y && y < endVertex.y ||
                        startVertex.y < y && endVertex.y < y ||
                        startVertex.x < x && endVertex.x < x
                    )
                        continue
                    if (startVertex.y < y) { // downward crossing
                        // quick reject implies: startVertex.y < y <= endVertex.y
                        // start->end x start->point
                        val cross = Point.cross(startVertex, endVertex, point)
                        if (cross < 0) // right side
                            windingNumber -= 1
                    } else { // upward crossing
                        // quick reject implies: endVertex.y <= y <= startVertex.y
                        val cross = Point.cross(startVertex, endVertex, point)
                        if (cross > 0) // left side
                            windingNumber += 1
                    }
                }
            }
        }
//        println("winding number = $windingNumber")
        return windingNumber.mod(2) == 1
    }

    // discriminates CCW vs CW
    /** Cumulative vector turning angle in radians, as it's dragged along this path */
    fun calculateWinding(): Double {
        var angle = 0.0
        arcs.forEachIndexed { arcIndex, arc ->
            // arc angle + vertex angle
            angle += arc.sweepAngle // need to ensure it's in [-PI; PI]
            if (isClosed || arcIndex < arcs.lastIndex) {
                val nextArcIndex = (arcIndex + 1).mod(arcs.size)
                val vertex = vertices[arcIndex].toOffset()
                val nextVertex = vertices[nextArcIndex].toOffset()
                val incomingNormalVector = when (val circle = arc.circleOrLine) {
                    is Circle ->
                        vertex - circle.center
                    else ->
                        Offset(
                            nextVertex.y - vertex.y,
                            -(nextVertex.x - vertex.x)
                        )
                }
                val outgoingNormalVector = when (val nextCircle = arcs[nextArcIndex].circleOrLine) {
                    is Circle ->
                        nextVertex - nextCircle.center
                    else -> {
                        val nextNextVertex = vertices[(nextArcIndex + 1).mod(vertices.size)].toOffset()
                        Offset(
                            nextNextVertex.y - nextVertex.y,
                            -(nextNextVertex.x - nextVertex.x)
                        )
                    }
                }
                angle += outgoingNormalVector.angleRad(incomingNormalVector)
            }
        }
        return angle
    }
}