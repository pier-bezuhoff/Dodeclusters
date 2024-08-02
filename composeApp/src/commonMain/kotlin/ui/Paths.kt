package ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathOperation
import data.geometry.Circle
import data.Cluster
import data.geometry.CircleOrLine
import data.geometry.Line

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

// NOTE: to create proper reduce(xor):
// 2^(# circles) -> binary -> filter even number of 1 -> to parts
// MAYBE: move to Cluster methods
// MAYBE: add threading cuz it can be slow (esp. 100+ circles with xor)
fun part2path(
    circles: List<CircleOrLine>,
    part: Cluster.Part,
    visibleRect: Rect
): Path {
    val circleInsides = part.insides.map { circles[it] }
    val insidePath: Path? = circleInsides
        .map {
            when (it) {
                is Circle -> circle2path(it)
                is Line -> halfPlanePath(it, visibleRect)
            }
        }
        .reduceOrNull { acc: Path, anotherPath: Path ->
            acc.op(acc, anotherPath, PathOperation.Intersect)
            acc
        }
    val circleOutsides = part.outsides.map { circles[it] }
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
    } else if (part.outsides.isEmpty())
        insidePath
    else {
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