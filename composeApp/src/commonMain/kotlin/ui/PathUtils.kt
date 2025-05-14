package ui

import MIN_CIRCLE_TO_CUBIC_APPROXIMATION_RADIUS
import MIN_CIRCLE_TO_LINE_APPROXIMATION_RADIUS
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import data.geometry.Circle
import data.geometry.CircleOrLine
import data.geometry.EPSILON2
import data.geometry.Line
import domain.PathCache
import domain.cluster.ConcreteClosedArcPath
import domain.cluster.ConcreteOpenArcPath
import domain.cluster.LogicalRegion
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sqrt

const val VISIBLE_RECT_INDENT = 100f

// NOTE: conic (rational quadratic bezier) segments are automatically chopped into
//  quadratic splines: https://github.com/google/skia/blob/0feee17aeacab6b88ac8be3d8b35ae4c940eeea4/src/core/SkGeometry.cpp#L1570
//  BUT each 90-degree segment has MAX 2^5=32 quads, which is not enough for larger circles,
//  so we do custom approx starting from certain radii
// MAYBE: separately pass visibleRect.center & maxDimension for optimization
/** NOTE: ignores [circle]'s orientation at this point */
fun circle2path(
    circle: Circle,
    visibleRect: Rect,
    path: Path = Path(),
): Path =
    if (circle.radius < MIN_CIRCLE_TO_CUBIC_APPROXIMATION_RADIUS) {
        path.apply {
            addOval(
                Rect(
                    center = circle.center,
                    radius = circle.radius.toFloat()
                )
            )
        }
    } else if (circle.radius < MIN_CIRCLE_TO_LINE_APPROXIMATION_RADIUS) {
        circle2cubicPath(circle.copy(isCCW = true), visibleRect, closed = true, path)
    } else {
        val line = circle.copy(isCCW = true)
            .approximateToLine(visibleRect.center)
        halfPlanePath(line, visibleRect, path)
    }

/** Add P1->P2 cubic bezier, closely approximating circular arc of
 * circle ([Ax], [Ay], [R]) */
@Suppress("LocalVariableName")
private fun circle2cubicArcPath(
    path: Path,
    Ax: Float, Ay: Float, R: Float,
    P1x: Float, P1y: Float,
    P2x: Float, P2y: Float,
) {
    path.moveTo(P1x, P1y)
    // dy = 4/3*h; h = R - |OM| = sagitta
    val Mx = (P1x + P2x)/2f
    val My = (P1y + P2y)/2f
    val AMx = Mx - Ax
    val AMy = My - Ay
    val AM = hypot(AMx, AMy)
    val k = 4f/3*(R/AM - 1)
    // CY = control point above-segment y-level-offset (if P1-P2 were horizontal)
    val CYx = k*AMx
    val CYy = k*AMy
    val C1x = P1x*2/3 + P2x/3 + CYx
    val C1y = P1y*2/3 + P2y/3 + CYy
    val C2x = P1x/3 + P2x*2/3 + CYx
    val C2y = P1y/3 + P2y*2/3 + CYy
    // NOTE: cubic bezier start collapsing at R=500k+
    // good approximation for small sagitta
    path.cubicTo(C1x, C1y, C2x, C2y, P2x, P2y)
}

@Suppress("LocalVariableName")
private fun wrapOutOfScreenCircleAsRectangle(
    path: Path,
    visibleRect: Rect,
    Ox: Float, Oy: Float,
    Ax: Float, Ay: Float,
    P1x: Float, P1y: Float,
    P2x: Float, P2y: Float,
    isCCW: Boolean,
) {
    val maxDim =
//        visibleRect.minDimension*0.5f
        visibleRect.maxDimension
    val OAx = Ax - Ox
    val OAy = Ay - Oy
    val orientationSign = if (isCCW) +1 else -1
    val inwardK = orientationSign*maxDim/hypot(OAx, OAy)
    val inwardX = OAx*inwardK
    val inwardY = OAy*inwardK
    // NOTE: OA is perp to P1P2
    val P1P2x = P2x - P1x
    val P1P2y = P2y - P1y
    val forwardK = maxDim/hypot(P1P2x, P1P2y)
    val forwardX = P1P2x*forwardK
    val forwardY = P1P2y*forwardK
    // assuming we done P1->P2 arc
    path.relativeLineTo(forwardX, forwardY)
    path.relativeLineTo(inwardX, inwardY)
    path.relativeLineTo(-2*forwardX - P1P2x, -2*forwardY - P1P2y)
    path.relativeLineTo(-inwardX, -inwardY)
    path.close()
}

/** Adds to [path] approximation of [circle] ∩ [visibleRect] by a cubic bezier arc inside
 * of [visibleRect] and 3 additional lines to close the contour outside of [visibleRect].
 * Orientation of the resulting contour is to agree with [circle]`.isCCW`. */
@Suppress("LocalVariableName")
fun circle2cubicPath(
    circle: Circle,
    visibleRect: Rect,
    closed: Boolean,
    path: Path = Path(),
): Path {
    val screenCenter = visibleRect.center
    // visible rect is contained in this circle
    val outerRadius =
//        visibleRect.minDimension*0.48f
        visibleRect.maxDimension + VISIBLE_RECT_INDENT
    val outerCircle = Circle(screenCenter, outerRadius)
    val intersectionCoordinates = Circle.calculateIntersectionCoordinates(circle, outerCircle)
    if (intersectionCoordinates.size == 4) { // all 2 intersections present
        // normal case, CCW order of points (wrt $circle)
        val Ox = screenCenter.x
        val Oy = screenCenter.y
        val Ax = circle.x.toFloat()
        val Ay = circle.y.toFloat()
        val P1x = intersectionCoordinates[0]
        val P1y = intersectionCoordinates[1]
        val P2x = intersectionCoordinates[2]
        val P2y = intersectionCoordinates[3]
        circle2cubicArcPath(
            path = path,
            Ax = Ax, Ay = Ay, R = circle.radius.toFloat(),
            P1x = P1x, P1y = P1y,
            P2x = P2x, P2y = P2y,
        )
        if (closed) {
            // FIX: orientation
            wrapOutOfScreenCircleAsRectangle(
                path = path,
                visibleRect = visibleRect,
                Ox = Ox, Oy = Oy,
                Ax = Ax, Ay = Ay,
                P1x = P1x, P1y = P1y,
                P2x = P2x, P2y = P2y,
                isCCW = circle.isCCW,
            )
        }
    } else if (closed && circle.hasInside(screenCenter)) {
        // our circle includes all visible region
        path.addRect(visibleRect.inflate(VISIBLE_RECT_INDENT))
    } // else: empty path
    return path
}

fun visibleHalfPlanePath(
    line: Line,
    visibleRect: Rect,
    path: Path = Path()
): Path {
    halfPlanePath(line, visibleRect, path)
    val visiblePath = Path().apply {
        addRect(visibleRect.inflate(VISIBLE_RECT_INDENT))
    }
    path.op(path, visiblePath, PathOperation.Intersect)
    return path
}

fun halfPlanePath(
    line: Line,
    visibleRect: Rect,
    path: Path = Path()
): Path {
    val (a, b, c) = line
    val centerX = visibleRect.left + visibleRect.width/2f
    val centerY = visibleRect.top + visibleRect.height/2f
    val maxDim = visibleRect.maxDimension
    val far = 2*maxDim
    val t = b*centerX - a*centerY
    val n2 = a*a + b*b
    val pointClosestToScreenCenterX = ((b*t - a*c)/n2).toFloat()
    val pointClosestToScreenCenterY = ((-a*t - b*c)/n2).toFloat()
    val directionX =  line.directionX.toFloat()
    val directionY =  line.directionY.toFloat()
    val forwardX = far * directionX
    val forwardY = far * directionY
    val farBackX: Float = pointClosestToScreenCenterX - directionX * maxDim
    val farBackY: Float = pointClosestToScreenCenterY - directionY * maxDim
    val farInDirectionX: Float = far * line.normalX.toFloat()
    val farInDirectionY: Float = far * line.normalY.toFloat()
    path.moveTo(farBackX, farBackY)
    path.relativeLineTo(forwardX, forwardY)
    path.relativeLineTo(farInDirectionX, farInDirectionY)
    path.relativeLineTo(-forwardX, -forwardY)
    path.close()
    return path
}

// BUG: random glitches on big clusters (e.g. D-bug-android)
fun chessboardPath(
    circles: List<CircleOrLine>,
    visibleRect: Rect,
    inverted: Boolean = false
): Path {
    val path = Path()
    circles.forEach {
        val p = when (it) {
            is Circle -> circle2path(it, visibleRect)
            is Line -> visibleHalfPlanePath(it, visibleRect)
        }
        path.op(path, p, PathOperation.Xor)
    }
    if (inverted) {
        val visiblePath = Path().apply { addRect(visibleRect.inflate(VISIBLE_RECT_INDENT)) }
        path.op(visiblePath, path, PathOperation.Difference)
    }
    return path
}

/**
 * @param[circles] all delimiters, `null`s are to be interpreted as ∅ empty sets
 */
fun region2pathWithCache(
    circles: List<CircleOrLine?>,
    region: LogicalRegion,
    pathCache: PathCache,
    visibleRect: Rect,
): Path {
    val path = Path()
    if (region.insides.isEmpty() && region.outsides.isEmpty()) {
        return path
    }
    path.addRect(visibleRect.inflate(VISIBLE_RECT_INDENT))
    for (ix in region.insides) {
        when (val circle = circles[ix]) {
            is Circle -> {
                var p: Path? = pathCache.cachedObjectPaths[ix]
                if (p == null || !pathCache.pathCacheValidity[ix]) {
                    p = circle2path(circle, visibleRect, p ?: Path())
                    pathCache.cacheObjectPath(ix, p)
                }
                path.op(path, p,
                    if (circle.isCCW) PathOperation.Intersect
                    else PathOperation.Difference
                )
            }
            is Line -> {
                var p: Path? = pathCache.cachedObjectPaths[ix]
                if (p == null || !pathCache.pathCacheValidity[ix]) {
                    p = halfPlanePath(circle, visibleRect, p ?: Path())
                    pathCache.cacheObjectPath(ix, p)
                }
                path.op(path, p, PathOperation.Intersect)
            }
            null -> {}
        }
    }
    for (ix in region.outsides) {
        when (val circle = circles[ix]) {
            is Circle -> {
                var p: Path? = pathCache.cachedObjectPaths[ix]
                if (p == null || !pathCache.pathCacheValidity[ix]) {
                    p = circle2path(circle, visibleRect, p ?: Path())
                    pathCache.cacheObjectPath(ix, p)
                }
                path.op(path, p,
                    if (circle.isCCW) PathOperation.Difference
                    else PathOperation.Intersect
                )
            }
            is Line -> {
                var p: Path? = pathCache.cachedObjectPaths[ix]
                if (p == null || !pathCache.pathCacheValidity[ix]) {
                    p = halfPlanePath(circle, visibleRect, p ?: Path())
                    pathCache.cacheObjectPath(ix, p)
                }
                path.op(path, p, PathOperation.Difference)
            }
            null -> {}
        }
    }
    return path
}

/**
 * @param[circles] all delimiters, `null`s are to be interpreted as ∅ empty sets
 */
fun region2path(
    circles: List<CircleOrLine?>,
    region: LogicalRegion,
    visibleRect: Rect,
): Path {
    val path = Path()
    if (region.insides.isEmpty() && region.outsides.isEmpty()) {
        return path
    }
    path.addRect(visibleRect.inflate(VISIBLE_RECT_INDENT))
    for (ix in region.insides) {
        when (val circle = circles[ix]) {
            is Circle -> {
                val p = circle2path(circle, visibleRect)
                path.op(path, p,
                    if (circle.isCCW) PathOperation.Intersect
                    else PathOperation.Difference
                )
            }
            is Line -> {
                val p = halfPlanePath(circle, visibleRect)
                path.op(path, p, PathOperation.Intersect)
            }
            null -> {}
        }
    }
    for (ix in region.outsides) {
        when (val circle = circles[ix]) {
            is Circle -> {
                val p = circle2path(circle, visibleRect)
                path.op(path, p,
                    if (circle.isCCW) PathOperation.Difference
                    else PathOperation.Intersect
                )
            }
            is Line -> {
                val p = halfPlanePath(circle, visibleRect)
                path.op(path, p, PathOperation.Difference)
            }
            null -> {}
        }
    }
    return path
}

/**
 * @param[circles] all delimiters, `null`s are to be interpreted as ∅ empty sets
 */
fun _region2path(
    circles: List<CircleOrLine?>,
    region: LogicalRegion,
    visibleRect: Rect,
): Path {
    val ins = region.insides.mapNotNull { circles[it] }
    if (ins.size < region.insides.size) { // null encountered
        return Path() // intersection with empty set
    }
    val outs = region.outsides.mapNotNull { circles[it] }
    val circleInsides =
        ins.filter { it is Line || it is Circle && it.isCCW } +
                outs.filter { it is Circle && !it.isCCW }
    val circleOutsides =
        ins.filter { it is Circle && !it.isCCW } +
                outs.filter { it is Line || it is Circle && it.isCCW }
    val insidePath: Path? = circleInsides
        .map {
            when (it) {
                is Circle -> circle2path(it, visibleRect)
                is Line -> halfPlanePath(it, visibleRect)
            }
        }
        .reduceOrNull { acc: Path, anotherPath: Path ->
            acc.op(acc, anotherPath, PathOperation.Intersect)
            acc
        }
    return if (insidePath == null) {
        val invertedPath = circleOutsides.map {
            when (it) {
                is Circle -> circle2path(it, visibleRect)
                is Line -> halfPlanePath(it, visibleRect)
            }
        }.fold(Path()) { acc: Path, anotherPath: Path ->
            acc.op(acc, anotherPath, PathOperation.Union)
            acc
        }
        val path = Path()
        // slightly bigger than the screen so that the borders are invisible
        path.addRect(visibleRect.inflate(VISIBLE_RECT_INDENT))
        path.op(path, invertedPath, PathOperation.Difference)
        path
    } else if (circleOutsides.isEmpty()) {
        insidePath
    } else {
        circleOutsides.fold(insidePath) { acc: Path, circleOutside: CircleOrLine ->
            val path = when (circleOutside) {
                is Circle -> circle2path(circleOutside, visibleRect)
                is Line -> halfPlanePath(circleOutside, visibleRect)
            }
            acc.op(acc, path, PathOperation.Difference)
            acc
        }
    }
}

fun circleRectIntersection(
    bigCircle: Circle,
    visibleRect: Rect,
) {
    val o = bigCircle.center
    val r = bigCircle.radius
    val left = visibleRect.left
    val right = visibleRect.right
    val top = visibleRect.top
    val bottom = visibleRect.bottom
    val tl = (visibleRect.topLeft - o).getDistance()
    val tr = (visibleRect.topRight - o).getDistance()
    val bl = (visibleRect.bottomLeft - o).getDistance()
    val br = (visibleRect.bottomRight - o).getDistance()
    val isLeft = o.x < left
    val isInH = o.x in left..right
    val isRight = right < o.x
    val isTop = o.y < top
    val isInV = o.y in top..bottom
    val isBottom = bottom < o.y
    if (isInH && isInV)
        return // draw nothing

    val topPoints = horizontalSegmentCircleIntersection(top, bigCircle)
        .filter { it.x in left..right }
    val bottomPoints = horizontalSegmentCircleIntersection(bottom, bigCircle)
        .filter { it.x in left..right }
    val leftPoints = verticalSegmentCircleIntersection(left, bigCircle)
        .filter { it.y in top..bottom }
    val rightPoints = verticalSegmentCircleIntersection(right, bigCircle)
        .filter { it.y in top..bottom }
    val points = topPoints + rightPoints + bottomPoints + leftPoints // naturally ordered
    // 0..4 points in total
    // add arcs, _, |, L segments
}

fun horizontalSegmentCircleIntersection(y: Float, circle: Circle): List<Offset> {
    val dx2 = circle.r2 - (y - circle.y).pow(2)
    return when {
        abs(dx2) < EPSILON2 -> listOf(Offset(circle.x.toFloat(), y))
        dx2 < 0 -> emptyList()
        else -> {
            val dx = sqrt(dx2)
            listOf( // ordered left->right
                Offset((circle.x - dx).toFloat(), y),
                Offset((circle.x + dx).toFloat(), y),
            )
        }
    }
}

fun verticalSegmentCircleIntersection(x: Float, circle: Circle): List<Offset> {
    val dy2 = circle.r2 - (x - circle.x).pow(2)
    return when {
        abs(dy2) < EPSILON2 -> listOf(Offset(x, circle.y.toFloat()))
        dy2 < 0 -> emptyList()
        else -> {
            val dy = sqrt(dy2)
            listOf( // ordered top->bottom
                Offset(x, (circle.y - dy).toFloat()),
                Offset(x, (circle.y + dy).toFloat()),
            )
        }
    }
}

fun ConcreteClosedArcPath.toPath(): Path {
    val path = Path()
    intersectionPoints.firstOrNull()?.let { startPoint ->
        path.moveTo(startPoint.x.toFloat(), startPoint.y.toFloat())
    }
    for (i in indices) {
        when (circles[i]) {
            is Line -> {
                val cyclicNextIndex = (i + 1) % size
                val nextPoint = intersectionPoints[cyclicNextIndex]
                path.lineTo(nextPoint.x.toFloat(), nextPoint.y.toFloat())
            }
            is Circle -> {
                path.arcTo(
                    rect = rects[i],
                    startAngleDegrees = startAngles[i],
                    sweepAngleDegrees = sweepAngles[i],
                    forceMoveTo = false
                )
            }
        }
    }
    path.close() // just in case
    return path
}

fun ConcreteOpenArcPath.toPath(): Path {
    val path = Path()
    path.moveTo(startPoint.x.toFloat(), startPoint.y.toFloat())
    for ((i, circle) in circles.withIndex()) {
        when (circle) {
            is Line -> {
                // there are 1 more intersection points than circles in open arc path
                val nextPoint = intersectionPoints[i + 1]
                path.lineTo(nextPoint.x.toFloat(), nextPoint.y.toFloat())
            }
            is Circle -> {
                path.arcTo(
                    rect = rects[i],
                    startAngleDegrees = startAngles[i],
                    sweepAngleDegrees = sweepAngles[i],
                    forceMoveTo = false
                )
            }
        }
    }
    return path
}
