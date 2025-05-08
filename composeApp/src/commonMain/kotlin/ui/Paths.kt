@file:Suppress("NOTHING_TO_INLINE")

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
inline fun circle2path(circle: Circle): Path =
    Path().apply {
        addOval(
            Rect(
                center = circle.center,
                radius = circle.radius.toFloat()
            )
        )
    }

/**
 * Given a big circle, that intersects a screen-enclosing
 * circle ([Ox], [Oy], [R]) at P1 ([P1x], [P1y]) and P2 ([P2x], [P2y]),
 * approximates its visible arc as cubic bezier and its out-of-screen arc as vertical/horizontal
 * line segments along the square the screen-enclosing circle is inscribed in
 */
@Suppress("LocalVariableName")
private inline fun wrapOutOfScreenCircleArcAroundSquare(
    path: Path,
    Ox: Float,
    Oy: Float,
    R: Float,
    P1x: Float,
    P1y: Float,
    P2x: Float,
    P2y: Float,
) {
    // dy = 4/3*h; h = R - |OM| = sagitta
    val Mx = (P1x + P2x)/2f
    val My = (P1y + P2y)/2f
    val OMx = Mx - Ox
    val OMy = My - Oy
    val OM = hypot(OMx, OMy)
    val k = 4f/3*(R/OM - 1)
    // CY = control point above-segment y-level-offset (if P1-P2 were horizontal)
    val CYx = k*OMx
    val CYy = k*OMy
    val C1x = P1x*2/3 + P2x/3 + CYx
    val C1y = P1y*2/3 + P2y/3 + CYy
    val C2x = P1x/3 + P2x*2/3 + CYx
    val C2y = P1y/3 + P2y*2/3 + CYy
    path.moveTo(P1x, P1y)
    // good approximation for small sagitta
    path.cubicTo(C1x, C1y, C2x, C2y, P2x, P2y)
    // lines
    val OP1x = P1x - Ox
    val OP1y = P1y - Oy
    val OP2x = P2x - Ox
    val OP2y = P2y - Oy
    // lets go into in-square coordinates
    // u = R/sqrt(2)*(1, 1); v = R/sqrt(2)*(1, -1)
    // (u)   1/          (1  1) (x)
    // (v) =  R*sqrt(2)  (1 -1) (y)
    // (x)   R/        (1  1) (u)
    // (y) =  sqrt(2)  (1 -1) (v)
    val xy2uvScaling = 1f/(sqrt(2f)*R)
    val uv2xyScaling = R/sqrt(2f)
    val P1u = (OP1x + OP1y)*xy2uvScaling
    val P1v = (OP1x - OP1y)*xy2uvScaling
    val P2u = (OP2x + OP2y)*xy2uvScaling
    val P2v = (OP2x - OP2y)*xy2uvScaling
    // now we a working on a circle inscribed into 45 degree rotated square
    // each square segment is described by a*u + b*v = 1, where |a|=|b|=1
    val a1 = if (P1u >= 0) 1 else -1 // 2 segments, associated with P1 and P2
    val b1 = if (P1v >= 0) 1 else -1
    val a2 = if (P2u >= 0) 1 else -1
    val b2 = if (P2v >= 0) 1 else -1
    // project on-circle points P1, P2 onto the square Q1, Q2
    val q1scaling = 1f/(a1*P1u + b1*P1v)
    val Q1u = P1u*q1scaling
    val Q1v = P1v*q1scaling
    val q2scaling = 1f/(a2*P2u + b2*P2v)
    val Q2u = P2u*q2scaling
    val Q2v = P2v*q2scaling
    // now lets add all the missing lines from P2 back to P1
    // we have P1->P2 cubic
     path.lineTo(
         Ox + (Q2u + Q2v)*uv2xyScaling,
         Oy + (Q2u - Q2v)*uv2xyScaling,
     ) // P2 -> Q2
    var a = a2
    var b = b2
    var u = Q2u
    var v = Q2v
    // swap placeholder variables
    var aa: Int
    var bb: Int
    var uu: Float
    var vv: Float
    // since $circle is CCW, if it eclipses $outerCircle then P1->P2 are CCW on it
    // and P2->P1 path is a major arc
    // if P1->P2 are CW, then no eclipse and P2->P1 is a minor arc
    val isCCW = Q1u*Q2v - Q2u*Q1v >= 0 // P1->P2 wrt to the outer circle or square
    val isMajorArc = isCCW // CCW P1->P2 => major arc P2->P1
    if (isMajorArc) {
        do {
            uu = (u + v)/2
            vv = (v - u)/2
            aa = -b
            bb = a
            a = aa
            b = bb
            u = uu
            v = vv
            path.lineTo(
                Ox + (u + v)*uv2xyScaling,
                Oy + (u - v)*uv2xyScaling,
            )
        } while (!(a == a1 && b == b1))
    } else { // CW P1->P2 => minor arc P2->P1
        while (!(a == a1 && b == b1)) {
            uu = (u - v)/2
            vv = (u + u)/2
            aa = b
            bb = -a
            a = aa
            b = bb
            u = uu
            v = vv
            path.lineTo(
                Ox + (u + v)*uv2xyScaling,
                Oy + (u - v)*uv2xyScaling,
            )
        }
    }
     path.lineTo(
         Ox + (Q1u + Q1v)*uv2xyScaling,
         Oy + (Q1u - Q1v)*uv2xyScaling,
     )
    // now we are in Q1
    path.close() // Q1 -> P1
}

fun bigCircle2path(circle: Circle, visibleRect: Rect): Path {
    val circle0 = circle.copy(isCCW = true) // i think we ignore its original orientation atp?
    val screenCenter = visibleRect.center
    // visible rect is contained in this circle
    val outerRadius = visibleRect.minDimension - VISIBLE_RECT_INDENT // TMP
//        visibleRect.maxDimension + VISIBLE_RECT_INDENT
    val outerCircle = Circle(screenCenter, outerRadius)
    val intersectionCoordinates = Circle.calculateIntersectionCoordinates(circle0, outerCircle)
    val path = Path()
    if (intersectionCoordinates.size == 4) { // all 2 intersections present
        // normal case, CCW order of points (wrt $circle)
        wrapOutOfScreenCircleArcAroundSquare(
            path = path,
            Ox = screenCenter.x, Oy = screenCenter.y, R = outerRadius,
            P1x = intersectionCoordinates[0], P1y = intersectionCoordinates[1],
            P2x = intersectionCoordinates[2], P2y = intersectionCoordinates[3],
        )
    } else if (circle0.hasInside(screenCenter)) {
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

// TODO: benchmark & optimize
/**
 * @param[circles] all delimiters, `null`s are to be interpreted as ∅ empty sets
 */
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
