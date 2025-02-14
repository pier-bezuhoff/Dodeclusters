package domain.expressions

import data.geometry.Circle
import data.geometry.CircleOrLine
import data.geometry.GCircle
import data.geometry.GeneralizedCircle
import data.geometry.Point
import data.geometry.Rotor
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
    val newCircles = mutableListOf<GCircle?>()
    for (i in 1 until n) {
        newCircles.add(
//            start.bisector(end, nOfSections = n, index = i, inBetween = params.inBetween)
            start.bisector(
                if (params.complementary) -end else end,
                nOfSections = n, index = i
            ).toGCircleAs(startCircle)
        )
    }
    return newCircles
}

// we ignore params.inBetween for points
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
    val newPoints = mutableListOf<Point>()
    for (i in 1 until n) {
        val progress = i.toDouble() / n
        newPoints.add(
            Point(
                startPoint.x*(1 - progress) + endPoint.x*progress,
                startPoint.y*(1 - progress) + endPoint.y*progress,
            )
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
    // this can be simplified with bi-inversion
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

fun computeBiInversion(
    params: BiInversionParameters,
    engine1: GCircle, // realistically speaking points dont fit here
    engine2: GCircle,
    target: GCircle,
): List<GCircle?> {
    val e1 = GeneralizedCircle.fromGCircle(engine1)
    val e2 = GeneralizedCircle.fromGCircle(engine2).let {
        if (params.reverseSecondEngine) -it else it
    }
    val t = GeneralizedCircle.fromGCircle(target)
    val inversiveAngle = e1.inversiveAngle(e2)
    val bivector0 = Rotor.fromOuterProduct(e1, e2).normalized() * (-inversiveAngle)
    val trajectory = mutableListOf<GCircle?>()
    repeat(params.nSteps) { i ->
        // inlined t.biInversion(e1, e2, params.speed)
        val bivector = bivector0 * ((i + 1) * params.speed)
        val rotor = bivector.exp() // alternatively bivector0.exp() * log(progress)
        val result = rotor.applyTo(t).toGCircleAs(target)
        trajectory.add(result)
    }
    return trajectory
}

// FIX: quite inaccurate on points (?? example)
// NOTE: without downscaling it visibly diverges
fun computeLoxodromicMotion(
    params: LoxodromicMotionParameters,
    divergencePoint: Point, convergencePoint: Point,
    target: GCircle,
): List<GCircle?> {
    val start = GeneralizedCircle.fromGCircle(divergencePoint)
    val end = GeneralizedCircle.fromGCircle(convergencePoint)
    val totalAngle = params.angle * PI /180
    val totalDilation = params.dilation
    val n = params.nSteps + 1
    val targetGC = GeneralizedCircle.fromGCircle(target)
    val pencil = Rotor.fromOuterProduct(start, end).normalized()
    val perpPencil = pencil.dual()
    val trajectory = mutableListOf<GCircle?>()
    repeat(n) { i ->
        val progress = (i + 1).toDouble() / n
        val angle = progress * totalAngle
        val logDilation = progress * totalDilation
        val rotation = (perpPencil * (-angle/2.0)).exp()
        val dilation = (pencil * (logDilation/2.0)).exp()
        // inlined t.loxodromicShift(start, end, angle, dilation)
        trajectory.add(
            dilation.applyTo(
                rotation.applyTo(targetGC)
            ).toGCircle() //As(target)
        )
    }
    return trajectory
}
