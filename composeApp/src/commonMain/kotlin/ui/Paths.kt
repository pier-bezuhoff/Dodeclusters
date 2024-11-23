package ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import data.geometry.Circle
import data.geometry.CircleOrLine
import data.geometry.EPSILON2
import data.geometry.Line
import domain.cluster.ClusterPart
import domain.cluster.ConcreteArcBoundRegion
import getPlatform
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

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

fun visibleHalfPlanePath(line: Line, visibleRect: Rect): Path {
    val maxDim = visibleRect.maxDimension
    val pointClosestToScreenCenter = line.project(visibleRect.center)
    val direction =  line.directionVector
    val farBack = pointClosestToScreenCenter - direction * maxDim
    val farForward = pointClosestToScreenCenter + direction * maxDim
    val farInDirection = line.normalVector * (2 * maxDim)
    val farInBack = farBack + farInDirection
    val farInForward = farForward + farInDirection
    val visiblePath = Path().apply { addRect(visibleRect.inflate(100f)) }
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
fun part2path(
    circles: List<CircleOrLine?>,
    part: ClusterPart,
    visibleRect: Rect
): Path {
    val ins = part.insides.mapNotNull { circles[it] }
    val outs = part.outsides.mapNotNull { circles[it] }
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
        path.addRect(visibleRect.inflate(100f))
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

// Q: does it track inside-vs-outside?
fun arcs2path(
    arcs: ConcreteArcBoundRegion,
): Path {
    val path = Path()
    if (arcs.isContinuous) { // idk about drawing non-continuous ones
        arcs.intersectionPoints.firstOrNull()?.let { startPoint ->
            path.moveTo(startPoint.x.toFloat(), startPoint.y.toFloat())
        }
        for (i in arcs.indices) {
            when (val circle = arcs.circles[i]) {
                is Line -> {
                    val nextPoint = arcs.intersectionPoints[(i + 1) % arcs.size]!!
                    path.lineTo(nextPoint.x.toFloat(), nextPoint.y.toFloat())
                }
                is Circle -> {
                    path.arcTo(
                        Rect(circle.center, circle.radius.toFloat()),
                        startAngleDegrees = arcs.startAngles[i],
                        sweepAngleDegrees = arcs.sweepAngles[i],
                        forceMoveTo = false
                    )
                }
            }
        }
        path.close() // just in case
    }
    return path
}