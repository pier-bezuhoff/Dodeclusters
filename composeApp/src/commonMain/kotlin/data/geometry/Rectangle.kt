package data.geometry

import androidx.compose.ui.geometry.Rect
import domain.filterIndices
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sqrt

fun selectWithRectangle(objects: List<GCircle?>, rect: Rect): List<Int> =
    objects.filterIndices { o ->
        if (o == null)
            false
        else
            testIfObjectIsInRectangle(o, rect)
    }

fun testIfObjectIsInRectangle(obj: GCircle, rect: Rect): Boolean =
    when (obj) {
        is Circle -> {
            val diagonal = hypot(rect.width, rect.height)
            testHorizontalSegmentCircleIntersections(rect.top, rect.left, rect.right, obj) ||
            testHorizontalSegmentCircleIntersections(rect.bottom, rect.left, rect.right, obj) ||
            testVerticalSegmentCircleIntersections(rect.left, rect.top, rect.bottom, obj) ||
            testVerticalSegmentCircleIntersections(rect.right, rect.top, rect.bottom, obj) ||
            // we know there is no intersections at this point
            rect.contains(obj.center) && 2*obj.radius <= diagonal
        }
        is Line -> {
            testHorizontalSegmentLineIntersection(rect.top, rect.left, rect.right, obj) ||
            testHorizontalSegmentLineIntersection(rect.bottom, rect.left, rect.right, obj) ||
            testVerticalSegmentLineIntersection(rect.left, rect.top, rect.bottom, obj) ||
            testVerticalSegmentLineIntersection(rect.right, rect.top, rect.bottom, obj)
        }
        is Point ->
            rect.left <= obj.x && obj.x <= rect.right &&
            rect.top <= obj.y && obj.y <= rect.bottom
        is ImaginaryCircle -> false
    }

@Suppress("NOTHING_TO_INLINE")
inline fun testHorizontalSegmentCircleIntersections(
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

@Suppress("NOTHING_TO_INLINE")
inline fun testVerticalSegmentCircleIntersections(
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

@Suppress("NOTHING_TO_INLINE")
inline fun testHorizontalSegmentLineIntersection(
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

@Suppress("NOTHING_TO_INLINE")
inline fun testVerticalSegmentLineIntersection(
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
