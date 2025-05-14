@file:Suppress("NOTHING_TO_INLINE")

package data.geometry

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.util.fastCoerceIn
import domain.filterIndices
import domain.squareSum
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** Unlike the default [Rect] constructor, works correctly for any configuration of corners */
fun Rect.Companion.fromCorners(corner1: Offset, corner2: Offset): Rect {
    val topLeft = Offset(min(corner1.x, corner2.x), min(corner1.y, corner2.y))
    val bottomRight = Offset(max(corner1.x, corner2.x), max(corner1.y, corner2.y))
    return Rect(topLeft, bottomRight)
}

fun selectWithRectangle(objects: List<GCircle?>, rect: Rect): List<Int> =
    objects.filterIndices { o ->
        if (o == null)
            false
        else
            objectRectangleCollisionTest(o, rect)
    }

/** Rectangle collider.
 * @return `true` if intersection of [obj]'s border and [rect] is
 * non-empty (including [rect]'s interior), otherwise `false` */
fun objectRectangleCollisionTest(obj: GCircle, rect: Rect): Boolean =
    when (obj) {
        is Circle -> {
            circleRectCollisionTest(obj, rect)
//            circleRectCollisionTestEveryEdge(obj, rect)
        }
        is Line -> {
            testHorizontalSegmentLineIntersection(rect.top, rect.left, rect.right, obj) ||
            testHorizontalSegmentLineIntersection(rect.bottom, rect.left, rect.right, obj) ||
            testVerticalSegmentLineIntersection(rect.left, rect.top, rect.bottom, obj) ||
            testVerticalSegmentLineIntersection(rect.right, rect.top, rect.bottom, obj)
        }
        is Point ->
            obj.x in rect.left .. rect.right &&
            obj.y in rect.top .. rect.bottom
        is ImaginaryCircle -> false
    }

fun calculateRectangleCircleIntersectionContour(rect: Rect, circle: Circle): List<Offset> {
    // segment between 1st 2 points is line segment of the rect, then circle, then rect,
    // ..., and lastly circle; going CCW along the rect
    // for tangential contact, 2 points of segment should coincide
    val (left, top, right, bottom) = rect
    val (x, y, r) = circle
    if (x + r < left || right < x - r || y + r < top || bottom < y - r)
        return emptyList()
    TODO()
}

// reference: https://developer.mozilla.org/en-US/docs/Games/Techniques/3D_collision_detection
/** Simpler interior intersection test that ignores rect-inside-circle & circle-inside-rect cases */
fun simpleCircleRectCollisionTest(circle: Circle, rect: Rect): Boolean {
    val cx = circle.x.toFloat()
    val cy = circle.y.toFloat()
    // get rect's closest point to circle center by clamping
    val xClosestToCircleCenter = cx.coerceIn(rect.left, rect.right) // max(rect.left, min(cx, rect.right))
    val yClosestToCircleCenter = cy.coerceIn(rect.top, rect.bottom) // max(rect.top, min(cy, rect.bottom))
    val distance2 = squareSum(xClosestToCircleCenter - cx, yClosestToCircleCenter - cy)
    return distance2 < circle.r2
}

private fun circleRectCollisionTestEveryEdge(circle: Circle, rect: Rect): Boolean =
    testHorizontalSegmentCircleIntersections(rect.top, rect.left, rect.right, circle) ||
    testHorizontalSegmentCircleIntersections(rect.bottom, rect.left, rect.right, circle) ||
    testVerticalSegmentCircleIntersections(rect.left, rect.top, rect.bottom, circle) ||
    testVerticalSegmentCircleIntersections(rect.right, rect.top, rect.bottom, circle) ||
    // we know there is no intersections at this point
    rect.contains(circle.center) && 2*circle.radius <= rect.minDimension

// wrong farthest calc (?)
/** Only counts contour intersections OR if the [circle] is fully inside the [rect] */
private fun circleRectCollisionTest(circle: Circle, rect: Rect): Boolean {
    val cx = circle.x.toFloat()
    val cy = circle.y.toFloat()
    val r2 = circle.r2.toFloat()
    val (left, top, right, bottom) = rect
    // get rect's closest point to the circle center by clamping
    val closestX = cx.fastCoerceIn(left, right) // max(left, min(cx, right))
    val closestY =  cy.fastCoerceIn(top, bottom) // max(top, min(cy, bottom))
    val distance2ToClosest = squareSum(closestX - cx, closestY - cy)
    val closestRectPointIsCloserThanRadius = distance2ToClosest <= r2
    val farthestX = if (cx - left > right - cx) left else right
    val farthestY = if (cy - top > bottom - cy) top else bottom
    val distance2ToFarthest = squareSum(farthestX - cx, farthestY - cy)
    val farthestRectPointIsFartherThanRadius = distance2ToFarthest >= r2
    // farthest >= r && (closest <= r || center in rect)
    return farthestRectPointIsFartherThanRadius && (
            closestRectPointIsCloserThanRadius ||
            cx in left..right && cy in top .. bottom // circle center is inside rect
        )
}

private fun testHorizontalSegmentCircleIntersections(
    y: Float,
    startX: Float, endX: Float,
    circle: Circle
): Boolean {
    val range = startX .. endX
    val discriminant = circle.r2 - (y - circle.y).pow(2)
    if (discriminant < 0)
        return false
    val sqrtD = sqrt(discriminant)
    val x1 = circle.x + sqrtD
    val x2 = circle.x - sqrtD
    return x1 in range || x2 in range
}

private fun testVerticalSegmentCircleIntersections(
    x: Float,
    startY: Float, endY: Float,
    circle: Circle
): Boolean {
    val range = startY .. endY
    val discriminant = circle.r2 - (x - circle.x).pow(2)
    if (discriminant < 0)
        return false
    val sqrtD = sqrt(discriminant)
    val y1 = circle.y + sqrtD
    val y2 = circle.y - sqrtD
    return y1 in range || y2 in range
}

private fun testHorizontalSegmentLineIntersection(
    y: Float,
    startX: Float, endX: Float,
    line: Line
): Boolean {
    val range = startX .. endX
    // 0*x + 1*y - y0 = 0
    val w = -line.a
    if (w == 0.0)
        return false
    val wx = line.c + line.b*y // det in homogenous coordinates
    val px = wx/w
    return px in range
}

private fun testVerticalSegmentLineIntersection(
    x: Float,
    startY: Float, endY: Float,
    line: Line
): Boolean {
    val range = startY .. endY
    // 1*x + 0*y - x0 = 0
    val w = line.b
    if (w == 0.0)
        return false
    val wy = -line.a*x - line.c
    val py = wy/w
    return py in range
}
