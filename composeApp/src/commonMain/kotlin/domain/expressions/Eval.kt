package domain.expressions

import data.geometry.Circle
import data.geometry.CircleOrLine
import data.geometry.GCircle
import data.geometry.GeneralizedCircle
import data.geometry.Line
import data.geometry.Point
import kotlin.math.hypot

// eval for one-to-one functions

fun computeCircleByCenterAndRadius(
    center: Point,
    radiusPoint: Point
): Circle =
    Circle(
        center.x,
        center.y,
        hypot(radiusPoint.x - center.x, radiusPoint.y - center.y)
    )

fun computeCircleBy3Points(
    point1: GCircle,
    point2: GCircle,
    point3: GCircle,
): CircleOrLine? =
    GeneralizedCircle.perp3(
        GeneralizedCircle.fromGCircle(point1),
        GeneralizedCircle.fromGCircle(point2),
        GeneralizedCircle.fromGCircle(point3),
    )?.toGCircle() as? CircleOrLine

fun computeLineBy2Points(
    point1: GCircle,
    point2: GCircle
): Line? =
    GeneralizedCircle.perp3(
        GeneralizedCircle.fromGCircle(Point.CONFORMAL_INFINITY),
        GeneralizedCircle.fromGCircle(point1),
        GeneralizedCircle.fromGCircle(point2),
    )?.toGCircle() as? Line

fun computeCircleInversion(
    target: GCircle,
    engine: GCircle
): GCircle {
    val engineGC = GeneralizedCircle.fromGCircle(engine)
    val targetGC = GeneralizedCircle.fromGCircle(target)
    val result = engineGC.applyTo(targetGC)
    return result.toGCircle()
}

// idk, i want it to follow translation/rotation/scaling
// MAYBE: just repeat obj transformations for incident points
fun computeIncidence(
    obj: CircleOrLine,
    //directedPerpendicular: Line
    order: Double
): Point {
    TODO()
}