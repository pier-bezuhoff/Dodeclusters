package core.geometry

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import domain.angleRad
import domain.pow2
import domain.squareSum
import kotlinx.serialization.SerialName
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
@SerialName("core.geometry.ConcreteArcPath")
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

    init {
        require(
            if (isClosed)
                vertices.size == arcs.size
            else
                vertices.size == arcs.size + 1
        ) { "Incorrect combination of vertices and arcs:\nisClosed=$isClosed, #vertices=${vertices.size}, #arcs = ${arcs.size}" }
    }

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

    fun distance2From(point: Point): Double {
        var distance2 = vertices.minOfOrNull {
            it.distance2From(point)
        } ?: Double.POSITIVE_INFINITY
        forEachArc { _, arc, start, end ->
            when (val circle = arc.circleOrLine) {
                is Circle -> {
                    val closest = circle.project(point)
                    // another projection onto the circle is always farther than both of the arc ends
                    val onTheArc =
                        circle.agreesWithOrientation(start, closest, end)
                    if (onTheArc) {
                        val d2 = point.distance2From(closest)
                        distance2 = min(distance2, d2)
                    }
                }
                else -> {
                    val closest = Line.projectPointOntoSegment(point, start, end)
                    val d2 = point.distance2From(closest)
                    distance2 = min(distance2, d2)
                }
            }
        }
        return distance2
    }

    /** prefer [distance2From] */
    override fun distanceFrom(point: Point): Double =
        sqrt(distance2From(point))

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

    /** checks whether `this` contour has intersections [circle] contour */
    infix fun intersects(circle: Circle): Boolean {
        var noIntersection = true
        val (x, y, radius) = circle
        val c = circle.centerPoint
        val r2 = circle.r2
        forEachArc { _, arc, arcStart, arcEnd ->
            val side1 = c.distance2From(arcStart) >= r2
            val side2 = c.distance2From(arcEnd) >= r2
            if (side1 != side2) {
                noIntersection = false
                return@forEachArc
            }
            when (val circle = arc.circleOrLine) {
                is Circle -> {
                    val dcx = circle.x - x
                    val dcy = circle.y - y
                    val d2 = dcx * dcx + dcy * dcy
                    val circlesIntersect =
                        d2 >= (circle.radius - radius).pow2() && d2 <= (radius + circle.radius).pow2()
                    if (circlesIntersect) { // simply find & test if intersection points lie on the arc
                        val r12 = r2
                        val r22 = circle.r2
                        val dr2 = r12 - r22
                        // reference (0->1, 1->2):
                        // https://stackoverflow.com/questions/3349125/circle-circle-intersection-points#answer-3349134
                        val d = sqrt(d2)
                        val a = (d2 + dr2)/(2 * d)
                        val h = sqrt(r12 - a * a)
                        val dc0x = dcx/d
                        val dc0y = dcy/d
                        val pcx = x + a * dc0x
                        val pcy = y + a * dc0y
                        val vx = h * dc0x
                        val vy = h * dc0y
                        val p = Point(pcx + vy, pcy - vx)
                        val q = Point(pcx - vy, pcy + vx)
                        if (circle.agreesWithOrientation(arcStart, p, arcEnd) ||
                            circle.agreesWithOrientation(arcStart, q, arcEnd)
                        ) {
                            noIntersection = false
                            return@forEachArc
                        }
                    }
                }
                else -> {
                    // if both segment ends are in the circle, that's it
                    if (side1 || side2) {
                        val l2 = arcStart.distance2From(arcEnd)
                        val t = (arcStart.dot(c, arcEnd)/l2).coerceIn(0.0, 1.0)
                        /** distance^2 from the circle center to the path's line segment */
                        val d2 = c.distance2From(
                            arcStart.x + t*(arcEnd.x - arcStart.x),
                            arcStart.y + t*(arcEnd.y - arcStart.y),
                        )
                        if (d2 <= r2) { // both ends are outside, BUT the segment is closer than radius
                            noIntersection = false
                            return@forEachArc
                        }
                    }
                }
            }
        }
        return !noIntersection
    }

    /** checks whether `this` contour has intersections [line] contour */
    infix fun intersects(line: Line): Boolean {
        var noIntersection = true
        val (a, b, c) = line
        forEachArc { _, arc, arcStart, arcEnd ->
            val signedDistance1 = a*arcStart.x + b*arcStart.y + c
            val signedDistance2 = a*arcEnd.x + b*arcEnd.y + c
            if (signedDistance1*signedDistance2 < EPSILON) {
                noIntersection = false
                return@forEachArc
            }
            when (val circle = arc.circleOrLine) {
                is Circle ->
                    if (distanceFrom(circle.x, circle.y) <= circle.radius) {
                        // alt: test that arc direction vectors at start and end point towards the line
                        // P and Q are the closest and the furthest points from the line on the circle
                        val px = circle.x + circle.radius*a
                        val py = circle.y + circle.radius*b
                        val signedDistanceP = a*px + b*py + c
                        val diffSides1P = signedDistance1*signedDistanceP <= 0
                        if (diffSides1P) { // then q is on the same side, so no need to check it
                            if (circle.agreesWithOrientation(arcStart, Point(px, py), arcEnd)) {
                                noIntersection = false
                                return@forEachArc
                            }
                        } else {
                            val qx = circle.x - circle.radius*a
                            val qy = circle.y - circle.radius*b
                            val signedDistanceQ = a*qx + b*qy + c
                            val diffSides1Q = signedDistance1*signedDistanceQ <= 0
                            if (diffSides1Q &&
                                circle.agreesWithOrientation(arcStart, Point(qx, qy), arcEnd)
                            ) {
                                noIntersection = false
                                return@forEachArc
                            }
                        }
                    }
                else -> {}
            }
        }
        return !noIntersection
    }

    /** checks whether `this` contour has intersections [concreteArcPath] contour */
    infix fun intersects(concreteArcPath: ConcreteArcPath): Boolean {
        var noIntersection = true
        forEachArc { _, arc1, start1, end1 ->
            concreteArcPath.forEachArc { _, arc2, start2, end2 ->
                when (val circle1 = arc1.circleOrLine) {
                    is Circle -> when (val circle2 = arc2.circleOrLine) {
                        is Circle -> if (circle1 intersects circle2) {
                            val ips = Circle.calculate2RoughIntersections(circle1, circle2)
                            if (ips.size == 2) {
                                val (p, q) = ips
                                if (circle1.agreesWithOrientation(start1, p, end1) &&
                                    circle2.agreesWithOrientation(start2, p, end2) ||
                                    circle1.agreesWithOrientation(start1, q, end1) &&
                                    circle2.agreesWithOrientation(start2, q, end2)
                                ) {
                                    noIntersection = false
                                    return@forEachArc
                                }
                            }
                        }
                        else -> {
                            val (cx, cy, r) = circle1
                            val l2 = start2.distance2From(end2)
                            val dx = end2.x - start2.x
                            val dy = end2.y - start2.y
                            val scalar = (cx - start2.x)*dx + (cy - start2.y)*dy
                            val t = scalar/l2
                            // circle center projected onto segment
                            val px = start2.x + t*dx
                            val py = start2.y + t*dy
                            val distance2 = squareSum(px - cx, py - cy)
                            val diff = r*r - distance2
                            if (diff > 0) {
                                val k = sqrt(diff)/sqrt(l2)
                                val vx = k * dx
                                val vy = k * dy
                                val p = Point(px - vx, py - vy)
                                val q = Point(px + vx, py + vy)
                                if (circle1.agreesWithOrientation(start1, p, end1) &&
                                    start2.dot(p, end2) in 0.0..l2 ||
                                    circle1.agreesWithOrientation(start1, q, end1) &&
                                    start2.dot(q, end2) in 0.0..l2
                                ) {
                                    noIntersection = false
                                    return@forEachArc
                                }
                            }
                        }
                    }
                    else -> when (val circle2 = arc2.circleOrLine) {
                        is Circle -> {
                            val (cx, cy, r) = circle2
                            val l2 = start1.distance2From(end1)
                            val dx = end1.x - start1.x
                            val dy = end1.y - start1.y
                            val scalar = (cx - start1.x)*dx + (cy - start1.y)*dy
                            val t = scalar/l2
                            val px = start1.x + t*dx
                            val py = start1.y + t*dy
                            val distance2 = squareSum(px - cx, py - cy)
                            val diff = r*r - distance2
                            if (diff > 0) {
                                val k = sqrt(diff)/sqrt(l2)
                                val vx = k * dx
                                val vy = k * dy
                                val p = Point(px - vx, py - vy)
                                val q = Point(px + vx, py + vy)
                                if (start1.dot(p, end1) in 0.0..l2 &&
                                    circle2.agreesWithOrientation(start2, p, end2) ||
                                    start1.dot(q, end1) in 0.0..l2 &&
                                    circle2.agreesWithOrientation(start2, q, end2)
                                ) {
                                    noIntersection = false
                                    return@forEachArc
                                }
                            }
                        }
                        else -> {
                            val o1 = Point.cross(start1, end1, start2) > 0
                            val o2 = Point.cross(start1, end1, end2) > 0
                            val o3 = Point.cross(start2, end2, start1) > 0
                            val o4 = Point.cross(start2, end2, end1) > 0
                            if (o1 != o2 && o3 != o4) { // each segment must separate ends of the other one
                                noIntersection = false
                                return@forEachArc
                            }
                            // ideally we also check collinear stuff
                        }
                    }
                }
            }
            if (!noIntersection)
                return@forEachArc
        }
        return !noIntersection
    }

    override fun getRegionLocation(region: Region): Region.RegionLocation =
        when (region) {
            is Circle -> {
                if (this intersects region) {
                    Region.RegionLocation.OVERLAPS
                } else if (vertices.firstOrNull()?.liesInside(region) == true) {
                    Region.RegionLocation.IS_CONTAINED_INSIDE
                } else if (isClosed && region.isCCW && region.center liesInside this) {
                    Region.RegionLocation.CONTAINS_INSIDE
                } else {
                    Region.RegionLocation.NO_INTERSECTION
                }
            }
            is Line -> {
                if (this intersects region)
                    Region.RegionLocation.OVERLAPS
                else if (vertices.firstOrNull()?.liesInside(region) == true)
                    Region.RegionLocation.IS_CONTAINED_INSIDE
                else
                    Region.RegionLocation.NO_INTERSECTION
            }
            is ConcreteArcPath -> {
                // i think it's smart to do O(2*n) quick reject first before O(n^2) intersection tests
                if (this.toRect().overlaps(region.toRect())) {
                    if (this intersects region)
                        Region.RegionLocation.OVERLAPS
                    else if (isClosed && region.vertices.firstOrNull()?.liesInside(this) == true)
                        Region.RegionLocation.CONTAINS_INSIDE
                    else if (region.isClosed && this.vertices.firstOrNull()?.liesInside(region) == true)
                        Region.RegionLocation.IS_CONTAINED_INSIDE
                    else
                        Region.RegionLocation.NO_INTERSECTION
                } else {
                    Region.RegionLocation.NO_INTERSECTION
                }
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
