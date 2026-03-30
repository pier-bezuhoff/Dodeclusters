package core.geometry

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min

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
}