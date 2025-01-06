package domain.cluster

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect
import data.geometry.Circle
import data.geometry.CircleLineIntersection
import data.geometry.CircleOrLine
import data.geometry.EPSILON
import data.geometry.GCircle
import data.geometry.GeneralizedCircle
import data.geometry.Line
import data.geometry.Point
import data.geometry.RegionPointLocation
import data.geometry.SegmentPoint
import data.geometry.calculateAngle
import data.geometry.calculateIntersection
import data.geometry.calculateSegmentTopLeft
import domain.ColorAsCss
import domain.Ix
import domain.TAU
import domain.degrees
import domain.filterIndices
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * @property[arcs] [List] of indices (_starting from 1!_) of circles/lines from which the
 * arcs are chosen, __prefixed__ by +/- depending on whether the direction of
 * the arc coincides with the direction of the circle/line. When not [isClosed] the first and
 * the last indices specify start/end points in addition
 * */
@Immutable
@Serializable
@SerialName("ArcPath")
sealed interface ArcPath {
    @SerialName("arcIndicesStartingFrom1WithMinusIndicatingReversedDirection")
    val arcs: List<Int>
    val borderColor: ColorAsCss?
}

/** Looping arc-path */
@Immutable
@Serializable
@SerialName("ClosedArcPath")
data class ClosedArcPath(
    // NOTE: cyclic order doesn't matter, but reversing it alternates between one of
    //  2 possible regions that arise when not considering circle order
    @SerialName("arcIndicesStartingFrom1WithMinusIndicatingReversedDirection")
    override val arcs: List<Int>,
    val fillColor: ColorAsCss? = null,
    override val borderColor: ColorAsCss? = null,
) : ArcPath {

    // previous arc: external orientation
    // next arc: internal orientation
    // TODO: when consecutive arcs dont intersect, fuse them the other way thru conformal infinity
    fun toConcrete(allObjects: List<GCircle?>): ConcreteClosedArcPath? {
        val circles = arcs.map { i ->
            val circleOrLine = allObjects[i-1] as CircleOrLine
            if (i > 0) circleOrLine
            else circleOrLine.reversed() // Q: why
        }
        val intersectionPoints: MutableList<Point> = mutableListOf()
        if (arcs.size > 1) {
            var previous: CircleOrLine = circles.last()
            var next: CircleOrLine = circles.first()
            var intersection: List<Point> = Circle.calculateIntersectionPoints(next, previous)
            val startingPoint = intersection.firstOrNull() ?: return null
            intersectionPoints.add(startingPoint)
            for (ix in arcs.indices.drop(1)) {
                previous = circles[ix - 1]
                next = circles[ix]
                intersection = Circle.calculateIntersectionPoints(next, previous)
                // order of intersection points is stable
                val point = intersection.firstOrNull() ?: return null
                intersectionPoints.add(point)
            }
        }
        return ConcreteClosedArcPath(
            circles, intersectionPoints, fillColor, borderColor
        )
    }
}

/** Non-looping arc-path
 *
 * Assumption: the point @ [startPointIndex] lies on [arcs]`.first()` and
 * the point @ [endPointIndex] lies on [arcs]`.last()`
 * */
@Immutable
@Serializable
@SerialName("OpenArcPath")
data class OpenArcPath(
    val startPointIndex: Ix,
    val endPointIndex: Ix,
    @SerialName("arcIndicesStartingFrom1WithMinusIndicatingReversedDirection")
    override val arcs: List<Int>,
    override val borderColor: ColorAsCss? = null,
) : ArcPath {

    fun toConcrete(allObjects: List<GCircle?>): ConcreteOpenArcPath? {
        val circles = arcs.map { i ->
            val circleOrLine = allObjects[i-1] as? CircleOrLine
            circleOrLine ?: return null
        }
        val startPoint = (allObjects[startPointIndex] as? Point) ?: return null
        val endPoint = (allObjects[endPointIndex] as? Point) ?: return null
        val intersectionPoints: MutableList<Point> = mutableListOf(startPoint)
        for (ix in arcs.indices.drop(1)) {
            val previous = circles[ix - 1]
            val next = circles[ix]
            val intersection = Circle.calculateIntersectionPoints(next, previous)
            // order of intersection points is stable
            val point = intersection.firstOrNull() ?: return null
            intersectionPoints.add(point)
        }
        intersectionPoints.add(endPoint)
        return ConcreteOpenArcPath(
            circles, intersectionPoints, borderColor
        )
    }
}

@Immutable
sealed interface ConcreteArcPath

// to distinguish in/out, connect any point to-the-left to the infinity and
// count how many arcs the line intersects, or the winding algorithm
@Immutable
data class ConcreteClosedArcPath(
    val circles: List<CircleOrLine>,
    val intersectionPoints: List<Point>,
    val fillColor: ColorAsCss?,
    val borderColor: ColorAsCss?,
) : ConcreteArcPath {
    val size: Int = circles.size
    val indices: IntRange = circles.indices
    val isCCW: Boolean = calculateOrientation()
    /** Whether it contains inside the CONFORMAL_INFINITY point */
    val isBounded: Boolean =
        isCCW && !intersectionPoints.contains(Point.CONFORMAL_INFINITY)
    val rects: List<Rect> = circles.map { circle ->
        if (circle is Circle)
            Rect(circle.center, circle.radius.toFloat())
        else
            Rect.Zero
    }
    /** clockwise, [0°; 360°)
     *
     * [reference](https://developer.android.com/reference/android/graphics/Path#arcTo(android.graphics.RectF,%20float,%20float))
     * */
    val startAngles: List<Float> = circles.zip(intersectionPoints) { circle, startPoint ->
        if (circle is Circle)
            (360f - circle.point2angle(startPoint)) % 360
        else
            0f
    }
    /**
     * clockwise, [0°; 360°)
     *
     * [reference](https://developer.android.com/reference/android/graphics/Path#arcTo(android.graphics.RectF,%20float,%20float))
     */
    val sweepAngles: List<Float> = circles.mapIndexed { ix, circle ->
        val nextIndex = (ix + 1) % size
        val nextPoint = intersectionPoints[nextIndex]
        if (circle is Circle) {
            if (size == 1) { // singular full circle case
                return@mapIndexed 360f
            }
            val startAngle = startAngles[ix]
            val endAngle = circle.point2angle(nextPoint)
            if (circle.isCCW)
                (360f + startAngle - endAngle) % 360f
            else
                (360f + endAngle - startAngle) % 360f
        } else 0f
    }

    init {
        require(circles.size == intersectionPoints.size)
    }

    fun hasBorderingEpsilon(point: Point): Boolean =
        if (size == 1) {
            circles[0].hasBorderingEpsilon(point)
        } else if (point == Point.CONFORMAL_INFINITY) {
            if (isBounded) {
                false
            } else {
                intersectionPoints.contains(Point.CONFORMAL_INFINITY)
                // bordering infinity test
//                circles.filterIndices { it is Line }
//                    .any { ix ->
//                        val line = circles[ix] as Line
//                        val start = intersectionPoints[ix]
//                        val end = intersectionPoints[(ix + 1) % size]
//                        line.pointIsInBetween(start, point, end)
//                    }
            }
        } else {
            indices.any { ix ->
                val nextIx = (ix + 1) % size
                val circle = circles[ix]
                circle.hasBorderingEpsilon(point) && circle.pointIsInBetween(
                    intersectionPoints[ix], point, intersectionPoints[nextIx]
                )
            }
        }

    /** non-strict inside test */
    private fun hasInsideRaycast(point: Point): Boolean {
        // cast y=point.y line
        // intersect with all arcs
        // sort intersections by distance from point
        // choose the closest
        // ensure it is not close to any arc intersection point ip: abs(it - ip) >= epsilon for every ip
        // check if tangent at it goes up => in, down => out
        // if close to an arc intersection, rotate line by irrational * PI
        // and try again
        if (point == Point.CONFORMAL_INFINITY)
            return !isBounded
        var line = Line(0.0, 1.0, -point.y)
        while (true) {
            var broken = false
            val intersections = mutableListOf<Pair<Ix, Point>>()
            for (ix in indices) {
                val circle = circles[ix]
                when (val intersection = calculateIntersection(line, circle)) {
                    is CircleLineIntersection.Double -> {
                        val currentPoint = intersectionPoints[ix]
                        val nextPoint = intersectionPoints[(ix + 1) % size]
                        if (circle.pointIsInBetween(currentPoint, intersection.point1, nextPoint)) {
                            if (listOf(currentPoint, nextPoint).any { intersection.point1.distanceFrom(it) < EPSILON }) {
                                broken = true
                                break
                            }
                            intersections.add(ix to intersection.point1)
                        }
                        if (circle.pointIsInBetween(currentPoint, intersection.point2, nextPoint)) {
                            if (listOf(currentPoint, nextPoint).any { intersection.point2.distanceFrom(it) < EPSILON }) {
                                broken = true
                                break
                            }
                            intersections.add(ix to intersection.point2)
                        }
                    }
                    CircleLineIntersection.None -> {}
                    is CircleLineIntersection.Tangent -> {
                        if (listOf((ix - 1) % size, ix, (ix + 1) % size).any { j ->
                                intersection.tangentPoint.distanceFrom(intersectionPoints[j]) < EPSILON
                            }
                        ) {
                            broken = true
                            break
                        }
                    }
                    CircleLineIntersection.Eq -> break
                }
            }
            if (broken) {
                println("bad raycast line $line, retrying slightly rotated")
                line = line.rotated(point.toOffset(), 10f*sqrt(2f))
            } else {
                val closestCollision = intersections.minByOrNull { point.distanceFrom(it.second) }
                if (closestCollision == null)
                    return false
                val tangent = circles[closestCollision.first].tangentAt(closestCollision.second)
                val clockWiseness = line.directionX*tangent.a + line.directionY*tangent.b
                // clockwise-ness = 0 corresponds to parallels, but we ignore tangential touch, so it should not happen
                return clockWiseness <= 0 // CCW => to the left => inside
            }
        }
    }

    fun calculateLocationEpsilon(point: Point): RegionPointLocation {
        val bordering = hasBorderingEpsilon(point)
        if (bordering)
            return RegionPointLocation.BORDERING
        val inside = hasInsideRaycast(point)
        return if (inside) RegionPointLocation.IN
        else RegionPointLocation.OUT
    }

    // good reference algorithms: https://en.wikipedia.org/wiki/Point_in_polygon
    // NOTE: winding angle algorithm is not good with unbounded shapes
    fun _calculateLocationEpsilon(point: Point): RegionPointLocation {
        if (point == Point.CONFORMAL_INFINITY) {
            return if (isBounded) RegionPointLocation.OUT
            else if ( // bordering infinity test
                circles.filterIndices { it is Line }
                    .any { ix ->
                        val line = circles[ix] as Line
                        val start = intersectionPoints[ix]
                        val end = intersectionPoints[(ix + 1) % size]
                        line.pointIsInBetween(start, point, end)
                    }
            ) RegionPointLocation.BORDERING
            else RegionPointLocation.IN
        }
        // another algo to test if the point lies on any of the arcs:
        // construct straight line through the point (prob horizontal, eastward)
        // check how it intersects the arcs, and order those intersections along the line
        // if unresolvable, choose another straight line
        if (size == 1) { // single full circle case, or half-plane case
            val circle = circles[0]
            return circle.calculateLocationEpsilon(point)
        }
        // cumulative winding angle == 0 => the point is inside
        var windingAngle = 0.0
        for (i in indices) {
            val arcStart = intersectionPoints[i] // closed arcpath => all intersections are present
            val arcEnd = intersectionPoints[(i + 1) % size]
            val circle = circles[i]
            val location = circle.calculateLocationEpsilon(point)
            if (location == RegionPointLocation.BORDERING) {
                if (circle.pointIsInBetween(arcStart, point, arcEnd))
                    // alternative condition: (angle >= 0) != circle.isCCW
                    return RegionPointLocation.BORDERING
            }
            val angle = calculateAngle(point, arcStart, arcEnd) // positive => we are 'in'
            // helpful pic: https://photos.app.goo.gl/4Ac99BKa16PHLQ9aA
            // alt hosting: https://imgur.com/a/UbZZgAo
            if (circle is Circle) { // circular arcs require a patch
                val angleIsCCW = angle >= 0.0
                val dAngle = if (angleIsCCW) { // positive, CCW angle
                    if (circle.isCCW) { // extruding arc doesn't matter
                        angle
                    } else { // intruding arc
                        if (location == RegionPointLocation.IN)
                            angle
                        // if location == bordering we would have returned already, so no worries
                        else
                            angle - TAU
                    }
                } else { // negative, CW angle
                    if (circle.isCCW) { // intruding arc
                        if (location == RegionPointLocation.OUT)
                            angle
                        // if location == bordering we would have returned already, so no worries
                        else angle + TAU
                    } else { // extruding arc doesn't matter
                        angle
                    }
                }
                println("winding angle += ${dAngle.degrees} (angle = ${angle.degrees})")
                windingAngle += dAngle
            } else {
                println("winding angle += ${angle.degrees}")
                windingAngle += angle
            }
        }
        println("calculateLocationEpsilon($point): windingAngle = ${windingAngle.degrees}")
        val threshold = 0.1 // small threshold just in case
        val soThePointIsOutside = abs(windingAngle) > 0.1
        return if (soThePointIsOutside == isBounded) // not that simple sadly, consider ∠-shape
            RegionPointLocation.OUT
        else
            RegionPointLocation.IN
    }

    fun findPointInside(): Point { // a bit monstrous...
        for (ix in indices) {
            val previousIx = (ix - 1) % size
            val previousPoint = intersectionPoints[previousIx]
            val point = intersectionPoints[ix]
            val nextIx = (ix + 1) % size
            val nextPoint = intersectionPoints[nextIx]
            val minSideLength = min(point.distanceFrom(previousPoint), point.distanceFrom(nextPoint))
            val smallStep = min(minSideLength/10.0, 10.0)
            val previousTangent = circles[previousIx].tangentAt(point)
            val currentTangent = circles[ix].tangentAt(point)
            val bisector = GeneralizedCircle.fromGCircle(previousTangent)
                .bisector(GeneralizedCircle.fromGCircle(currentTangent))
                .toGCircle() as Line
            val testPoint1 = Point(
                point.x + smallStep*bisector.directionX,
                point.y + smallStep*bisector.directionY
            )
            if (calculateLocationEpsilon(testPoint1) == RegionPointLocation.IN)
                return testPoint1
            val testPoint2 = Point(
                point.x - smallStep*bisector.directionX,
                point.y - smallStep*bisector.directionY
            )
            if (calculateLocationEpsilon(testPoint2) == RegionPointLocation.IN)
                return testPoint2
        }
        throw IllegalStateException("ConcreteClosedArcPath.findPointInside could not find any points")
    }

    /** `true` => CCW */
    private fun calculateOrientation(): Boolean {
        var topLeft = Point(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY) // stub
        var orientationIsCCW = true // stub
        // we look for topLeft thru all vertices and interior arc north points
        // orientation around topLeft = orientation of convex hull = orientation of the arc path
        for (ix in indices) {
            val vertex = intersectionPoints[ix]
            val nextVertex = intersectionPoints[(ix + 1) % size]
            val circle = circles[ix]
            if (abs(vertex.y - topLeft.y) < EPSILON && vertex.x < topLeft.x ||
                vertex.y < topLeft.y
            ) {
                topLeft = vertex
                val previousCircle = circles[(ix - 1) % size]
                val previousTangent = previousCircle.tangentAt(vertex)
                val tangent = circle.tangentAt(vertex)
                val crossProduct = previousTangent.a*tangent.b - previousTangent.b*tangent.a
                orientationIsCCW = crossProduct >= 0
            }
            val topLeftSegmentPoint = circle.calculateSegmentTopLeft(vertex, nextVertex)
            val anotherTopLeft = topLeftSegmentPoint.point
            if (topLeftSegmentPoint is SegmentPoint.Interior &&
                (abs(anotherTopLeft.y - topLeft.y) < EPSILON && anotherTopLeft.x < topLeft.x ||
                anotherTopLeft.y < topLeft.y)
            ) {
                topLeft = anotherTopLeft
                orientationIsCCW =
                    if (circle is Circle) circle.isCCW
                    else -(circle as Line).b <= 0 // normal points south => CCW (btw this case should not be achievable)
            }
        }
        return orientationIsCCW
    }
}

/**
 * @param[intersectionPoints] include `startPoint` and `endPoint`
 * */
@Immutable
data class ConcreteOpenArcPath(
    val circles: List<CircleOrLine>,
    val intersectionPoints: List<Point>,
    val borderColor: ColorAsCss?,
) : ConcreteArcPath {
    val startPoint: Point = intersectionPoints.first()
    val endPoint: Point = intersectionPoints.last()
    /** number of arcs in the path, aka [circles]`.size` */
    val size: Int = circles.size
    val rects: List<Rect> = circles.map { circle ->
        if (circle is Circle)
            Rect(circle.center, circle.radius.toFloat())
        else
            Rect.Zero
    }

    /** clockwise, [0°; 360°)
     *
     * [reference](https://developer.android.com/reference/android/graphics/Path#arcTo(android.graphics.RectF,%20float,%20float))
     * */
    val startAngles: List<Float> =
        circles.zip(intersectionPoints.dropLast(1)) { circle, startPoint ->
            if (circle is Circle)
                (360f - circle.point2angle(startPoint)) % 360f
            else
                0f
        }

    /**
     * clockwise, [0°; 360°)
     *
     * [reference](https://developer.android.com/reference/android/graphics/Path#arcTo(android.graphics.RectF,%20float,%20float))
     */
    val sweepAngles: List<Float> = circles.mapIndexed { ix, circle ->
        val nextIndex = (ix + 1) % size
        val nextPoint = intersectionPoints[nextIndex]
        if (circle is Circle) {
            val startAngle = startAngles[ix]
            val endAngle = circle.point2angle(nextPoint) // [-180; +180]
            if (circle.isCCW)
                (360f + startAngle - endAngle) % 360f
            else
                (360f + endAngle - startAngle) % 360f
        } else 0f
    }

    init {
        require(size >= 1)
        require(intersectionPoints.size == size + 1)
        require(circles.first().hasBorderingEpsilon(startPoint))
        require(circles.last().hasBorderingEpsilon(endPoint))
    }
}

// not really a good fit for unbounded shapes
/** CCW angle in degrees */
private fun calculateWindingAngle(
    circles: List<CircleOrLine>,
    intersectionPoints: List<Point>,
    point: Point
): Float {
    val size = circles.size
    if (point == Point.CONFORMAL_INFINITY) {
        return 0.0f // no clue, but it can be calculated properly i think
    }
    if (size == 1) { // single full circle case, or half-plane case
        val circle = circles[0]
        return when (circle.calculateLocationEpsilon(point)) {
            RegionPointLocation.IN -> {
                when (circle) {
                    is Circle ->
                        if (circle.isCCW) 360f else -360f
                    is Line -> 180f
                }
            }
            RegionPointLocation.BORDERING -> 0f
            RegionPointLocation.OUT -> {
                when (circle) {
                    is Circle -> 0f
                    is Line -> -180f
                }
            }
        }
    }
    // cumulative winding angle == 0 => the point is inside
    var windingAngle = 0.0
    for (i in circles.indices) {
        val arcStart = intersectionPoints[i] // closed arcpath => all intersections are present
        val arcEnd = intersectionPoints[(i + 1) % size]
        val circle = circles[i]
        val location = circle.calculateLocationEpsilon(point)
        if (location == RegionPointLocation.BORDERING) {
            if (circle.pointIsInBetween(arcStart, point, arcEnd))
            // alternative condition: (angle >= 0) != circle.isCCW
                return 0f
        }
        val angle = calculateAngle(point, arcStart, arcEnd) // positive => we are 'in'
        // helpful pic: https://photos.app.goo.gl/4Ac99BKa16PHLQ9aA
        // alt hosting: https://imgur.com/a/UbZZgAo
        if (circle is Circle) { // circular arcs require a patch
            val angleIsCCW = angle >= 0.0
            val dAngle = if (angleIsCCW) { // positive, CCW angle
                if (circle.isCCW) { // extruding arc doesn't matter
                    angle
                } else { // intruding arc
                    if (location == RegionPointLocation.IN)
                        angle
                    // if location == bordering we would have returned already, so no worries
                    else
                        angle - TAU
                }
            } else { // negative, CW angle
                if (circle.isCCW) { // intruding arc
                    if (location == RegionPointLocation.OUT)
                        angle
                    // if location == bordering we would have returned already, so no worries
                    else angle + TAU
                } else { // extruding arc doesn't matter
                    angle
                }
            }
            windingAngle += dAngle
        } else {
            windingAngle += angle
        }
    }
    return windingAngle.degrees
}

