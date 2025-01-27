package domain.expressions

import data.geometry.Circle
import data.geometry.CircleOrLine
import data.geometry.GCircle
import data.geometry.GeneralizedCircle
import data.geometry.Point
import domain.never
import kotlin.math.PI

// eval for one-to-many functions

/** always returns size=2 list */
fun computeIntersection(
    obj1: CircleOrLine,
    obj2: CircleOrLine,
): List<Point?> {
    val ips = Circle.calculateIntersectionPoints(obj1, obj2)
    return when (ips.size) {
        0 -> listOf(null, null)
        1 -> ips + null
        2 -> ips
        else -> never()
    }
}

fun computeCircleInterpolation(
    params: InterpolationParameters,
    startCircle: CircleOrLine,
    endCircle: CircleOrLine,
): List<GCircle?> {
    val start = GeneralizedCircle.fromGCircle(startCircle)
    val end = GeneralizedCircle.fromGCircle(endCircle)
    val n = params.nInterjacents + 1
    val newCircles = (1 until n).map { i ->
        val interjacent =
//            start.affineCombination(end, 0.5)
//            start.bisector(end, nOfSections = n, index = i, inBetween = params.inBetween)
            start.naturalBisector(end, nOfSections = n, index = i, complementary = params.complementary)
        interjacent.toGCircle()
    }
    return newCircles
}

fun computePointInterpolation(
    params: InterpolationParameters,
    startPoint: Point,
    endPoint: Point,
): List<Point?> {
    if (startPoint == Point.CONFORMAL_INFINITY ||
        endPoint == Point.CONFORMAL_INFINITY ||
        startPoint == endPoint
    ) {
        return List(params.nInterjacents) { null }
    }
    val n = params.nInterjacents + 1
    val newPoints = (1 until n).map { i ->
        Point(
            startPoint.x*i + endPoint.x*(n - i),
            startPoint.y*i + endPoint.y*(n - i)
        )
    }
    return newPoints
}

fun computeCircleExtrapolation(
    params: ExtrapolationParameters,
    startCircle: CircleOrLine,
    endCircle: CircleOrLine,
): List<CircleOrLine?> {
    val start = GeneralizedCircle.fromGCircle(startCircle)
    val end = GeneralizedCircle.fromGCircle(endCircle)
    val newGeneralizedCircles = mutableListOf<GeneralizedCircle>()
    var a = start
    var b = end
    var c: GeneralizedCircle
    repeat(params.nLeft) { // <-c-a-b
        c = a.applyTo(b)
        newGeneralizedCircles.add(c)
        b = a
        a = c
    }
    a = start
    b = end
    repeat(params.nRight) { // a-b-c->
        c = b.applyTo(a)
        newGeneralizedCircles.add(c)
        a = b
        b = c
    }
    return newGeneralizedCircles
        .map { it.toGCircle() as? CircleOrLine }
}

fun computeLoxodromicMotion(
    params: LoxodromicMotionParameters,
    divergencePoint: Point, convergencePoint: Point,
    target: GCircle,
): List<GCircle?> {
    // NOTE: without downscaling it visibly diverges
    val start = GeneralizedCircle.fromGCircle(divergencePoint)
    val end = GeneralizedCircle.fromGCircle(convergencePoint)
    val totalAngle = params.angle * PI /180
    val totalDilation = params.dilation
    val n = params.nSteps + 1
    val stops = mutableListOf<GeneralizedCircle>()
    repeat(n) { i ->
        val progress = (i + 1).toDouble() / n
        val angle = progress * totalAngle
        val dilation = progress * totalDilation
        val targetGC = GeneralizedCircle.fromGCircle(target)
        stops.add(
            targetGC.loxodromicShift(start, end, angle, dilation)
        )
    }
    return stops.map { it.toGCircle() }
}

