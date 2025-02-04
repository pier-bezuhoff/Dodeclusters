package domain.expressions

import data.geometry.Circle
import data.geometry.CircleOrLine
import data.geometry.CircleOrLineOrImaginaryCircle
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
    val newCircles = (1 until n).map { i ->
        val interjacent =
//            start.affineCombination(end, 0.5)
//            start.bisector(end, nOfSections = n, index = i, inBetween = params.inBetween)
            start.naturalBisector(
                if (params.complementary) -end else end,
                nOfSections = n, index = i
            )
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
    // this can be simplified with bi-inversion
    repeat(params.nLeft) { // <-c-a-b
        c = a.applyTo(b).normalizedPreservingDirection()
        newGeneralizedCircles.add(c)
        b = a
        a = c
    }
    a = start
    b = end
    repeat(params.nRight) { // a-b-c->
        c = b.applyTo(a).normalizedPreservingDirection()
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
    // MAYBE: check point-point and circle-circle consistence by conditional casts
    val e1 = GeneralizedCircle.fromGCircle(engine1)
    val e2 = GeneralizedCircle.fromGCircle(engine2)
    val t = GeneralizedCircle.fromGCircle(target)
    val outerProduct = Rotor.fromOuterProduct(e1, e2)
    val trajectory = mutableListOf<GCircle>()
    repeat(params.nSteps) { i ->
        // inlined t.biInversion(e1, e2, params.speed)
        val bivector = outerProduct * ((i + 1) * params.speed)
        val rotor = bivector.exp()
        trajectory.add(
            rotor.applyTo(t).toGCircle()
        )
    }
    return trajectory
}

fun _iterative_computeBiInversion(
    params: BiInversionParameters,
    engine1: CircleOrLineOrImaginaryCircle,
    engine2: CircleOrLineOrImaginaryCircle,
    target: GCircle,
): List<GCircle?> {
    // MAYBE: check point-point and circle-circle consistence by conditional casts
    val e1 = GeneralizedCircle.fromGCircle(engine1)
    val e2 = GeneralizedCircle.fromGCircle(engine2)
    var t = GeneralizedCircle.fromGCircle(target)
    val bivector = Rotor.fromOuterProduct(e1, e2) * params.speed
    val rotor = bivector.exp()
    val trajectory = mutableListOf<GCircle>()
    repeat(params.nSteps) {
        t = rotor.applyTo(t).normalizedPreservingDirection()
        // inlined t.biInversion(e1, e2, params.speed)
        trajectory.add(t.toGCircle())
    }
    return trajectory
}

fun computeLoxodromicMotion(
    params: LoxodromicMotionParameters,
    divergencePoint: Point, convergencePoint: Point,
    target: GCircle,
): List<GCircle?> {
    // NOTE: without downscaling it visibly diverges
    // MAYBE: check point-point and circle-circle consistence by conditional casts
    val start = GeneralizedCircle.fromGCircle(divergencePoint)
    val end = GeneralizedCircle.fromGCircle(convergencePoint)
    val totalAngle = params.angle * PI /180
    val totalDilation = params.dilation
    val n = params.nSteps + 1
    val targetGC = GeneralizedCircle.fromGCircle(target)
    val pencil = Rotor.fromOuterProduct(start, end).normalized()
    val perpPencil = pencil.dual()
    val trajectory = mutableListOf<GCircle>()
    repeat(n) { i ->
        val progress = (i + 1).toDouble() / n
        val angle = progress * totalAngle
        val logDilation = progress * totalDilation
        val rotation = (perpPencil * (-angle/2.0)).exp()
        val dilation = (pencil * (logDilation/2.0)).exp()
        // inlined t.loxodromicShift(start, end, angle, dilation)
        trajectory.add(
            dilation.applyTo(
                rotation.applyTo(targetGC).normalizedPreservingDirection()
            ).toGCircle()
        )
    }
    return trajectory
}

// this one might be faster but potentially less accurate (cumulative error)
fun _iterative_computeLoxodromicMotion(
    params: LoxodromicMotionParameters,
    divergencePoint: Point, convergencePoint: Point,
    target: GCircle,
): List<GCircle?> {
    // NOTE: without downscaling it visibly diverges
    // MAYBE: check point-point and circle-circle consistence by conditional casts
    val start = GeneralizedCircle.fromGCircle(divergencePoint)
    val end = GeneralizedCircle.fromGCircle(convergencePoint)
    val totalAngle = params.angle * PI /180
    val totalDilation = params.dilation
    val n = params.nSteps + 1
    var t = GeneralizedCircle.fromGCircle(target)
    val pencil = Rotor.fromOuterProduct(start, end).normalized()
    val perpPencil = pencil.dual()
    val rotation = (perpPencil * (-totalAngle/2.0/n)).exp()
    val dilation = (pencil * (totalDilation/2.0/n)).exp()
    val trajectory = mutableListOf<GCircle>()
    repeat(n) {
        // inlined t.loxodromicShift(start, end, angle, dilation)
        t = dilation.applyTo(
            rotation.applyTo(t).normalizedPreservingDirection()
        ).normalizedPreservingDirection()
        trajectory.add(t.toGCircle())
    }
    return trajectory
}

