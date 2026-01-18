package domain.expressions

import core.geometry.CircleOrLine
import core.geometry.GCircle
import core.geometry.Line
import core.geometry.Point
import domain.Ix
import kotlin.math.abs

// MAYBE: cache carrier->incident points lookup (since it's called on every VM.transform)
/**
 * Class for managing expressions (~ AST controller)
 * @param[initialExpressions] pls include all possible indices as keys
 * @param[objects] reference to shared, *downscaled* mutable mirror-list of VM.objects
 */
class ConformalExpressions(
    initialExpressions: Map<Ix, ConformalExprOutput?>,
    objects: MutableList<GCircle?>,
) : Expressions<Expr.Conformal, Expr.Conformal.OneToOne, Expr.Conformal.OneToMany, GCircle>(
    initialExpressions, objects
) {
    override fun Expr.Conformal.evaluate(
        objects: List<GCircle?>
    ): List<GCircle?> = eval(objects)

    override fun isExprPeriodic(expr: Expr.Conformal.OneToMany): Boolean =
        expr is Expr.LoxodromicMotion &&
        expr.parameters.dilation == 0.0 &&
        abs(expr.parameters.angle) == 360f ||
        expr is Expr.Rotation &&
        abs(expr.parameters.angle * expr.parameters.nSteps) == 360f

    // still unsure about potentially better ways of doing it
    // especially for incident-p on dependent objects of those transformed
    override fun adjustIncidentPointExpressions(incidentPointIndices: Collection<Ix>) {
        for (ix in incidentPointIndices) {
            val expr = expressions[ix]?.expr
            val o = objects[ix]
            if (expr is Expr.Incidence && o is Point) {
                val parent = objects[expr.carrier]
                if (parent is CircleOrLine) {
                    expressions[ix] = ExprOutput.Just(expr.copy(
                        parameters = IncidenceParameters(order = parent.point2order(o))
                    ))
                }
            }
        }
    }

    // FIX: NG, maybe bc of translation, idk
    /**
     * Adjust parameters of all points incident to lines,
     * scaling them by [zoom]. Should be used after uniformly scaling
     * all objects (e.g. when [get] scales source objects) to correctly zoom in/out
     * points on lines.
     * @return indices of changed expressions. You may want to [update]`()` them
     */
    fun scaleLineIncidenceExpressions(zoom: Double): List<Ix> {
        val changedIxs = mutableListOf<Ix>()
        for ((ix, e) in expressions) {
            val expr = e?.expr
            if (expr is Expr.Incidence && objects[expr.carrier] is Line) {
                expressions[ix] = ExprOutput.Just(
                    expr.copy(IncidenceParameters(
                        order = zoom * expr.parameters.order
                    ))
                )
                changedIxs.add(ix)
            }
        }
        return changedIxs
    }

    override fun testDependentIncidence(pointIndex: Ix, carrierIndex: Ix): Boolean {
        val pointExpr = expressions[pointIndex]?.expr
        val directIncidence =
            pointExpr is Expr.Incidence && pointExpr.carrier == carrierIndex ||
            pointExpr is Expr.Intersection && (pointExpr.circle1 == carrierIndex || pointExpr.circle2 == carrierIndex)
        val indirectIncidence = when (val carrierExpr = expressions[carrierIndex]?.expr) {
            is Expr.CircleByCenterAndRadius -> carrierExpr.radiusPoint == pointIndex
            is Expr.CircleBy3Points -> carrierExpr.object1 == pointIndex || carrierExpr.object2 == pointIndex || carrierExpr.object3 == pointIndex
            is Expr.CircleBy2PointsAndSagittaRatio -> carrierExpr.chordStartPoint == pointIndex || carrierExpr.chordEndPoint == pointIndex
            is Expr.CircleByPencilAndPoint ->
                carrierExpr.perpendicularObject == pointIndex || pointExpr is Expr.Intersection && (
                    pointExpr.circle1 == carrierExpr.pencilObject1 && pointExpr.circle2 == carrierExpr.pencilObject2 ||
                    pointExpr.circle1 == carrierExpr.pencilObject2 && pointExpr.circle2 == carrierExpr.pencilObject1
                )
            is Expr.LineBy2Points -> carrierExpr.object1 == pointIndex || carrierExpr.object2 == pointIndex
            // recursive transform check
            is Expr.TransformLike -> if (pointExpr is Expr.TransformLike) {
                val sameOutputIndex = (expressions[carrierIndex] as? ExprOutput.OneOf)?.outputIndex == (expressions[pointIndex] as? ExprOutput.OneOf)?.outputIndex
                val sameExpr = when (pointExpr) {
                    is Expr.CircleInversion -> pointExpr.copy(target = carrierIndex) == carrierExpr
                    is Expr.Rotation -> pointExpr.copy(target = carrierIndex) == carrierExpr
                    is Expr.BiInversion -> pointExpr.copy(target = carrierIndex) == carrierExpr
                    is Expr.LoxodromicMotion -> pointExpr.copy(target = carrierIndex) == carrierExpr
                }
                sameOutputIndex && sameExpr && testDependentIncidence(pointExpr.target, carrierExpr.target)
            } else false
            // NOTE: there could exist even more indirect, 2+ step incidence cases tbh
            else -> false
        }
        return directIncidence || indirectIncidence
    }
}
