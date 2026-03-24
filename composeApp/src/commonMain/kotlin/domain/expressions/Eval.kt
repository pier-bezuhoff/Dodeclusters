package domain.expressions

import core.geometry.Circle
import core.geometry.CircleOrLine
import core.geometry.EPSILON
import core.geometry.GCircle
import core.geometry.conformal.GeneralizedCircle
import core.geometry.Line
import core.geometry.Point
import core.geometry.RegionPointLocation
import domain.model.ConcreteArcPath
import domain.squareSum
import kotlin.math.abs
import kotlin.math.hypot

// eval for one-to-one functions

// MAYBE: return singular Point when center == radiusPoint
fun computeCircleByCenterAndRadius(
    center: Point,
    radiusPoint: Point
): Circle? {
    val radius = hypot(radiusPoint.x - center.x, radiusPoint.y - center.y)
    return when {
        radius == 0.0 ->
            null // we could return Point but idk
        center == Point.CONFORMAL_INFINITY || radiusPoint == Point.CONFORMAL_INFINITY ->
            null // or conformal infinity ig
        else ->
            // radius point is a perfect candidate for fixedPoint
            Circle(center.x, center.y, radius)
    }
}

// NOTE: can produce non-CCW circle
// if any 2 of the 3 are incident, the orientation is undefined (e.g. a circle and a point on it)
fun computeCircleBy3Points(
    point1: GCircle,
    point2: GCircle,
    point3: GCircle,
): GCircle? =
    GeneralizedCircle.perp3(
        GeneralizedCircle.fromGCircle(point1),
        GeneralizedCircle.fromGCircle(point2),
        GeneralizedCircle.fromGCircle(point3),
    )?.times(-1)?.toGCircle() // -1 for proper direction (left-hand xOy coordinates)

fun computeCircleByPencilAndPoint(
    circle1: GCircle,
    circle2: GCircle,
    point: GCircle,
): GCircle? =
    GeneralizedCircle.parallel2perp1(
        GeneralizedCircle.fromGCircle(circle1),
        GeneralizedCircle.fromGCircle(circle2),
        GeneralizedCircle.fromGCircle(point),
    )?.toGCircle()

// for 2 intersecting lines the result is inf. point, but we return null
// theoretically if any of the 2 is a line, the orientation is undefined (inf.p. lies on it)
fun computeLineBy2Points(
    point1: GCircle,
    point2: GCircle
): Line? =
    GeneralizedCircle.perp3(
        GeneralizedCircle.fromGCircle(point1),
        GeneralizedCircle.fromGCircle(point2),
        GeneralizedCircle.fromGCircle(Point.CONFORMAL_INFINITY),
    )?.toGCircle() as? Line

fun computeCircleInversion(
    target: GCircle,
    engine: GCircle
): GCircle? {
    // idk why but the normal route returns seemingly arbitrary result for center
    if (target is Point && engine is Circle && engine.centerPoint.distanceFrom(target) < EPSILON)
        return Point.CONFORMAL_INFINITY
    val engineGC = GeneralizedCircle.fromGCircle(engine)
    val targetGC = GeneralizedCircle.fromGCircle(target)
    val result = engineGC.applyTo(targetGC)
    return result.toGCircleAs(target)
}

// incident points glued to a scaled line require additional care
fun computeIncidence(
    params: IncidenceParameters,
    carrier: CircleOrLine,
): Point =
    carrier.order2point(params.order)

/**
 * *Sagitta* of a circular arc is the distance from the
 * midpoint of the arc to the midpoint of its chord.
 *
 * @param[chordStart] and [chordEnd] are 2 points on the circle forming a directed chord.
 *
 * @param[params] is the ratio of `sagitta : chord length`, signed. Positive
 * being to the left of the directed chord (assuming right-hand xOy system).
 *
 * @return circle thru [chordStart] and [chordEnd], with radius scaling proportionally
 * to the chord length
 */
fun computeCircleBy2PointsAndSagittaRatio(
    params: SagittaRatioParameters,
    chordStart: Point,
    chordEnd: Point,
): CircleOrLine? {
    if (chordStart == chordEnd ||
        chordStart == Point.CONFORMAL_INFINITY || chordEnd == Point.CONFORMAL_INFINITY
    ) return null
    val sagittaRatio = params.sagittaRatio
    if (abs(sagittaRatio) < EPSILON)
        return Line.by2Points(chordStart, chordEnd)
    if (sagittaRatio.isInfinite())
        return Line.by2Points(chordEnd, chordStart)
    val chordX  = chordEnd.x - chordStart.x
    val chordY  = chordEnd.y - chordStart.y
    val chord = hypot(chordX, chordY)
    val sagitta = sagittaRatio * chord
    val chordMidX = (chordStart.x + chordEnd.x)/2.0
    val chordMidY = (chordStart.y + chordEnd.y)/2.0
    val radius = (4*sagitta + chord/sagittaRatio)/8.0
    val apothem = radius - sagitta
    return Circle(
        x = chordMidX + apothem*chordY/chord,
        y = chordMidY - apothem*chordX/chord,
        radius = abs(radius),
        isCCW = sagittaRatio > 0,
    )
//    val sagittaX = params.sagittaRatio*(chordStart.y - chordEnd.y)
//    val sagittaY = params.sagittaRatio*(-chordStart.x + chordEnd.x)
//    val arcPoint = Point(chordMidX + sagittaX, chordMidY + sagittaY)
//    return GeneralizedCircle.perp3(
//        GeneralizedCircle.fromGCircle(chordStart),
//        GeneralizedCircle.fromGCircle(arcPoint),
//        GeneralizedCircle.fromGCircle(chordEnd),
//    )?.toGCircle() as? CircleOrLine
}

fun computeSagittaRatio(
    circle: Circle,
    chordStart: Point,
    chordEnd: Point,
): Double {
    require(chordStart.isFinite && chordEnd.isFinite)
    val chordX = chordEnd.x - chordStart.x
    val chordY = chordEnd.y - chordStart.y
    val chord = hypot(chordX, chordY)
    if (chord < EPSILON)
        return Double.POSITIVE_INFINITY // infinity sign doesn't matter
    val chordMidX = (chordStart.x + chordEnd.x)/2.0
    val chordMidY = (chordStart.y + chordEnd.y)/2.0
    val apothemX = chordMidX - circle.x
    val apothemY = chordMidY - circle.y
    val apothemCrossChord = chordX*apothemY - chordY*apothemX
    val sagitta = circle.radius - hypot(apothemX, apothemY)
    val sign = if (circle.isCCW) +1 else -1
    val sagitta1 =
        if ((apothemCrossChord >= 0.0) == circle.isCCW)
            sagitta
        else
            2*circle.radius - sagitta
    return sign*sagitta1/chord
}

fun computePolarLine(
    circle: CircleOrLine,
    point: Point,
): Line? {
    if (point == Point.CONFORMAL_INFINITY)
        return null // conformal infinity isn't compatible with projective infinities
    val (x, y) = point
    return when (circle) {
        is Circle -> {
            // (px - cx)(x - cx) + (py - cy)(y - cy) = R^2
            val dx = x - circle.x
            val dy = y - circle.y
            Line(dx, dy, -(circle.x * dx + circle.y * dy + circle.r2))
        }
        is Line -> { // parallel line thru REFLECTED point
            val (a, b, c) = circle
            val eq = (a*x + b*y + c)/squareSum(a, b)
            val p = Point(
                x - 2 * a * eq,
                y - 2 * b * eq,
            ) // point reflected w.r.t. line
            circle.translatedTo(p)
        }
    }
}

fun computePole(
    circle: Circle,
    polarLine: Line,
): Point {
    val (x, y, r) = circle
    val (a, b, c) = polarLine
    if (polarLine.calculateLocationEpsilon(circle.centerPoint) == RegionPointLocation.BORDERING)
        // NOTE: projectively correct pole would be the infinite point in a direction that
        //  is perpendicular to the polar line
        return Point.CONFORMAL_INFINITY
    val eq = a*x + b*y + c
    val k = r*r/eq
    return Point(x - a*k, y - b*k)
}

fun computeTangentialCircle(
    carrier: CircleOrLine,
    pointOnCarrier: Point,
    anotherPoint: Point,
): CircleOrLine? =
    computeCircleByPencilAndPoint(carrier, pointOnCarrier, anotherPoint) as? CircleOrLine

/**
 * @return (arcIndex, arcPercentage)
 */
fun computeArcPathIncidenceOrder(
    concreteArcPath: ConcreteArcPath,
    point: Point,
): Pair<Int, Double> {
    val (arcIndex, _, arcPercentage) = concreteArcPath.project(point)
    return Pair(arcIndex, arcPercentage)
}

fun computeArcPathIncidence(
    params: ArcPathIncidenceParameters,
    concreteArcPath: ConcreteArcPath,
): Point? {
    val arc = concreteArcPath.arcs.getOrNull(params.arcIndex)
    return when (val circleOrLine = arc?.circleOrLine) {
        is Circle -> {
            val angle = arc.startAngle + arc.sweepAngle*params.arcPercenteage
            circleOrLine.angle2point(angle)
        }
        is Line -> {
            val arcStart = concreteArcPath.vertices[params.arcIndex]
            val arcEnd = concreteArcPath.vertices[
                (params.arcIndex + 1).mod(concreteArcPath.vertices.size)
            ]
            val startOrder = circleOrLine.point2order(arcStart)
            val endOrder = circleOrLine.point2order(arcEnd)
            val order = startOrder + (endOrder - startOrder)*params.arcPercenteage
            circleOrLine.order2point(order)
        }
        null -> null
    }
}