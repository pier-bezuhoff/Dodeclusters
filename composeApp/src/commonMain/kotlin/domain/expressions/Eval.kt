package domain.expressions

import data.geometry.Circle
import data.geometry.CircleOrLine
import data.geometry.EPSILON
import data.geometry.GCircle
import data.geometry.GeneralizedCircle
import data.geometry.Line
import data.geometry.Point
import kotlin.math.hypot
import kotlin.math.sign

// eval for one-to-one functions

// MAYBE: return singular Point when center == radiusPoint
fun computeCircleByCenterAndRadius(
    center: Point,
    radiusPoint: Point
): Circle? {
    val radius = hypot(radiusPoint.x - center.x, radiusPoint.y - center.y)
    return if (radius == 0.0 || !radius.isFinite()) null
    else Circle(center.x, center.y, radius)
}

// NOTE: can produce non-CCW circle
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
    // idk why but normal route returns seemingly arbitrary result for center
    if (target is Point && engine is Circle && engine.centerPoint.distanceFrom(target) < EPSILON)
        return Point.CONFORMAL_INFINITY
    val engineGC = GeneralizedCircle.fromGCircle(engine)
    val targetGC = GeneralizedCircle.fromGCircle(target)
    val result = engineGC.applyTo(targetGC)
    return result.toGCircleAs(target)
}

// NOTE: not good since scaling line doesn't scale points incident to it
// MAYBE: just repeat obj transformations for tier=0 carrier incident points
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
 * */
fun computeCircleBy2PointsAndSagittaRatio(
    params: SagittaRatioParameters,
    chordStart: Point,
    chordEnd: Point,
): CircleOrLine? {
    if (chordStart == chordEnd || chordStart == Point.CONFORMAL_INFINITY || chordEnd == Point.CONFORMAL_INFINITY)
        return null
    val sagittaRatio = params.sagittaRatio
    val chordX  = chordEnd.x - chordStart.x
    val chordY  = chordEnd.y - chordStart.y
    val chordLength = hypot(chordX, chordY)
    val sagitta = sagittaRatio * chordLength
    if (sagitta < EPSILON)
        return Line.by2Points(chordStart, chordEnd)
    val chordMidX = (chordStart.x + chordEnd.x)/2.0
    val chordMidY = (chordStart.y + chordEnd.y)/2.0
    val radius = (4*sagitta + chordLength/sagittaRatio)/8.0
    val apothem = radius - sagitta
    return Circle(
        x = chordMidX + apothem*chordY/chordLength,
        y = chordMidY - apothem*chordX/chordLength,
        radius = radius
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
    val chordMidX = (chordStart.x + chordEnd.x)/2.0
    val chordMidY = (chordStart.y + chordEnd.y)/2.0
    val chordX = chordEnd.x - chordStart.x
    val chordY = chordEnd.y - chordStart.y
    val apothemX = chordMidX - circle.x
    val apothemY = chordMidY - circle.y
    val sign = sign(chordX*apothemY - chordY*apothemX)
    val sagitta = circle.radius - hypot(apothemX, apothemY)
    return sign*sagitta/hypot(chordX, chordY)
}

fun computePolarLine(
    circle: CircleOrLine,
    point: Point,
): Line =
    when (circle) {
        is Circle -> {
            // (px - cx)(x - cx) + (py - cy)(y - cy) = R^2
            val dx = point.x - circle.x
            val dy = point.y - circle.y
            Line(dx, dy, -(circle.x*dx + circle.y*dy + circle.r2))
        }
        is Line -> { // parallel line case
            circle.translatedTo(point)
        }
    }

fun computeTangentialCircle(
    carrier: CircleOrLine,
    pointOnCarrier: Point,
    anotherPoint: Point,
): CircleOrLine? =
    computeCircleByPencilAndPoint(carrier, pointOnCarrier, anotherPoint) as? CircleOrLine