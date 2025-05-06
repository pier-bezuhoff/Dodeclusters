package ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import data.geometry.Circle
import data.geometry.CircleOrLine
import data.geometry.EPSILON2
import data.geometry.Line
import domain.cluster.LogicalRegion
import domain.cluster.ConcreteClosedArcPath
import domain.cluster.ConcreteOpenArcPath
import getPlatform
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sqrt

private const val VISIBLE_RECT_INDENT = 100f

// NOTE: conic (rational quadratic bezier) segments are automatically approximated by
//  cubic bezier (afaik circle is split into 4 90-degrees arcs)
/** NOTE: ignores [circle]'s orientation at this point */
fun circle2path(circle: Circle): Path =
    Path().apply {
        addOval(
            Rect(
                center = circle.center,
                radius = circle.radius.toFloat()
            )
        )
    }

@Suppress("LocalVariableName")
fun bigCircle2path(circle: Circle, visibleRect: Rect): Path {
    val circle0 = circle.copy(isCCW = true)
    val screenCenter = visibleRect.center
    val isEclipsing = circle0.hasInside(screenCenter)
    // visible rect is contained in it
    val outerCircle = Circle(screenCenter, visibleRect.maxDimension/2f)
    val intersections = Circle.calculateIntersectionPoints(
        circle0, // i think we ignore its original orientation atp?
        outerCircle
    )
    val path = Path()
    if (intersections.size == 2) {
        // normal case, CCW order of points
        val Ox = circle.x.toFloat()
        val Oy = circle.y.toFloat()
        val P1x = intersections[0].x.toFloat()
        val P1y = intersections[0].y.toFloat()
        val P2x = intersections[1].x.toFloat()
        val P2y = intersections[1].y.toFloat()
        // arc middle
        val Mx = (P1x + P2x)/2f
        val My = (P1y + P2y)/2f
        val OMx = Mx - Ox
        val OMy = My - Oy
        val OM = hypot(OMx, OMy)
        val k = (2*circle.radius.toFloat() - OM)/OM
        // quad bezier control point
        val Cx = Ox + k*OMx
        val Cy = Oy + k*OMy
        path.moveTo(P1x, P1y)
        path.quadraticTo(Cx, Cy, P2x, P2y)
        val left1 = P1x <= Ox
        val left2 = P2x <= Ox
        val right1 = Ox < P1x
        val right2 = Ox < P2x
        val top1 = P1y <= Oy
        val top2 = P2y <= Oy
        val bottom1 = Oy < P1y
        val bottom2 = Oy < P2y
        val bothLeft = left1 && left2
        val bothRight = right1 && right2
        val bothTop = top1 && top2
        val bothBottom = bottom1 && bottom2
        // FIX: wrong: does not account for intersection orientation
        //  e.g. same 2 intersection points but in opposite direction
        //  which cannot be reduced to 2 lines
        when {
            // 4x4 = 16 cases
            // 4 same-corner cases
            (bothLeft && bothTop) || (bothRight && bothBottom) -> {
                path.lineTo(P2x, P1y)
            }
            (bothLeft && bothBottom) || (bothRight && bothTop) -> {
                path.lineTo(P1x, P2y)
            }
            // 8 same-row-or-column cases
            // TODO: wrong in 4 cases cuz of direction
            bothLeft -> { // P1y <= Oy < P2y || P2y <= Oy < P1y
                val left = visibleRect.left - VISIBLE_RECT_INDENT
                path.lineTo(left, P2y)
                path.lineTo(left, P1y)
            }
            bothRight -> { // P1y <= Oy < P2y || P2y <= Oy < P1y
                val right = visibleRect.right + VISIBLE_RECT_INDENT
                path.lineTo(right, P2y)
                path.lineTo(right, P1y)
            }
            bothTop -> { // P1x <= Ox < P2x || P2x <= Ox < P1x
                val top = visibleRect.top - VISIBLE_RECT_INDENT
                path.lineTo(P2x, top)
                path.lineTo(P1x, top)
            }
            bothBottom -> { // P1x <= Ox < P2x || P2x <= Ox < P1x
                val bottom = visibleRect.bottom + VISIBLE_RECT_INDENT
                path.lineTo(P2x, bottom)
                path.lineTo(P1x, bottom)
            }
            // 4 opposite-corner cases
            else -> {
                val P1P2x = P2x - P1x
                val P1P2y = P2y - P1y
                val vx = P1P2x/100f
                val vy = P1P2y/100f
                val P3x = P2x + vx
                val P3y = P2y + vy
                path.lineTo(P3x, P3y)
                // ?
                // ?
                path.lineTo(P1x - vx, P1y - vy)
            }
        }
        path.close()
    } else if (isEclipsing) {
        // our circle includes all visible region
        path.addOval(visibleRect.inflate(VISIBLE_RECT_INDENT))
    } else {
        // empty path
    }
    return path
}

fun visibleHalfPlanePath(line: Line, visibleRect: Rect): Path {
    val maxDim = visibleRect.maxDimension
    val pointClosestToScreenCenter = line.project(visibleRect.center)
    val direction =  line.directionVector
    val farBack = pointClosestToScreenCenter - direction * maxDim
    val farForward = pointClosestToScreenCenter + direction * maxDim
    val farInDirection = line.normalVector * (2 * maxDim)
    val farInBack = farBack + farInDirection
    val farInForward = farForward + farInDirection
    val visiblePath = Path().apply { addRect(visibleRect.inflate(VISIBLE_RECT_INDENT)) }
    val path = Path().apply {
        moveTo(farBack.x, farBack.y)
        lineTo(farForward.x, farForward.y)
        lineTo(farInForward.x, farInForward.y)
        lineTo(farInBack.x, farInBack.y)
        close()
    }
    path.op(path, visiblePath, PathOperation.Intersect)
    return path
}

fun halfPlanePath(line: Line, visibleRect: Rect): Path {
    val maxDim = visibleRect.maxDimension
    val pointClosestToScreenCenter = line.project(visibleRect.center)
    val direction =  line.directionVector
    val farBack = pointClosestToScreenCenter - direction * maxDim
    val farForward = pointClosestToScreenCenter + direction * maxDim
    val farInDirection = line.normalVector * (2 * maxDim)
    val farInBack = farBack + farInDirection
    val farInForward = farForward + farInDirection
    val path = Path().apply {
        moveTo(farBack.x, farBack.y)
        lineTo(farForward.x, farForward.y)
        lineTo(farInForward.x, farInForward.y)
        lineTo(farInBack.x, farInBack.y)
        close()
    }
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
            is Circle -> circle2path(it)
            is Line -> visibleHalfPlanePath(it, visibleRect)
        }
        path.op(path, p, PathOperation.Xor)
    }
    if (inverted) {
        val visiblePath = Path().apply { addRect(visibleRect.inflate(100f)) }
        path.op(visiblePath, path, PathOperation.Difference)
    }
    return path
}

private val maxRadius = getPlatform().maxCircleRadius
/**
 * @param[circles] all delimiters, `null`s are to be interpreted as ∅ empty sets
 * */
fun region2path(
    circles: List<CircleOrLine?>,
    region: LogicalRegion,
    visibleRect: Rect
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
                is Circle -> {
                    if (it.radius <= maxRadius) {
                        circle2path(it)
                    } else { // TODO: instead calc arc from rect-circle intersection
                        val line = it.approximateToLine(visibleRect.center)
                        halfPlanePath(line, visibleRect)
                    }
                }
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
                is Circle -> circle2path(it)
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
                is Circle -> circle2path(circleOutside)
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
