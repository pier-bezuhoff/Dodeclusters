package domain.expressions

import core.geometry.Circle
import core.geometry.CircleOrLine
import core.geometry.ConcreteArcPath
import core.geometry.GCircle
import core.geometry.GCircleOrConcreteAcPath
import core.geometry.ImaginaryCircle
import core.geometry.Line
import core.geometry.Point
import domain.Ix
import kotlin.math.abs

// MAYBE: cache carrier->incident points lookup (since it's called on every transform)
/**
 * Class for managing expressions (~ AST controller)
 * @param[initialExpressions] pls include all possible indices as keys
 * @param[objects] reference to shared, *downscaled* mutable mirror-list of displayObjects
 */
class ConformalExpressions(
    initialExpressions: Map<Ix, ConformalExprOutput?>,
    objects: MutableList<GCircleOrConcreteAcPath?>,
) : Expressions<Expr.Conformal, Expr.Conformal.OneToOne, Expr.Conformal.OneToMany, GCircleOrConcreteAcPath>(
    initialExpressions, objects
) {
    val circleIndices = mutableSetOf<Ix>()
    val lineIndices = mutableSetOf<Ix>()
    val imaginaryCircleIndices = mutableSetOf<Ix>()
    val pointIndices = mutableSetOf<Ix>()
    val gCircleIndices = mutableSetOf<Ix>()
    val arcPathIndices = mutableSetOf<Ix>()

    init {
        for (ix in objects.indices)
            updateObjectTypeAt(ix)
    }

    fun clear() {
        circleIndices.clear()
        lineIndices.clear()
        imaginaryCircleIndices.clear()
        pointIndices.clear()
        gCircleIndices.clear()
        arcPathIndices.clear()
    }

    override fun updateObjectTypeAt(index: Ix, obj: GCircleOrConcreteAcPath?) {
        gCircleIndices.remove(index)
        circleIndices.remove(index)
        lineIndices.remove(index)
        imaginaryCircleIndices.remove(index)
        pointIndices.remove(index)
        arcPathIndices.remove(index)
        val expression = expressions[index]
        if (expression == null) {
            val obj = obj ?: objects[index]
            when (obj) {
                is Circle -> circleIndices.add(index)
                is Line -> lineIndices.add(index)
                is ImaginaryCircle -> imaginaryCircleIndices.add(index)
                is Point -> pointIndices.add(index)
                is ConcreteArcPath -> arcPathIndices.add(index)
                null -> {}
            }
            if (obj is GCircle) {
                gCircleIndices.add(index)
            }
        } else {
            for (resultType in expression.expr.resultTypes) {
                when (resultType) {
                    Expr.ResultType.CIRCLE -> {
                        circleIndices.add(index)
                        gCircleIndices.add(index)
                    }
                    Expr.ResultType.LINE -> {
                        lineIndices.add(index)
                        gCircleIndices.add(index)
                    }
                    Expr.ResultType.IMAGINARY_CIRCLE -> {
                        imaginaryCircleIndices.add(index)
                        gCircleIndices.add(index)
                    }
                    Expr.ResultType.POINT -> {
                        pointIndices.add(index)
                        gCircleIndices.add(index)
                    }
                    Expr.ResultType.ARC_PATH -> {
                        arcPathIndices.add(index)
                    }
                }
            }
        }
    }

    override fun objectDeletedAt(index: Ix) {
        gCircleIndices.remove(index)
        circleIndices.remove(index)
        lineIndices.remove(index)
        imaginaryCircleIndices.remove(index)
        pointIndices.remove(index)
        arcPathIndices.remove(index)
    }

    override fun Expr.evaluate(
        objects: List<GCircleOrConcreteAcPath?>
    ): List<GCircleOrConcreteAcPath?> = (this as Expr.Conformal).eval(objects)

    private fun Expr.Conformal.evaluate(
        objects: List<GCircleOrConcreteAcPath?>
    ): List<GCircleOrConcreteAcPath?> = eval(objects)

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

    /**
     * Reallocate arc-path trajectory
     * NOTE: requires that [newArcPaths] trajectory dependencies are already added
     * @param[newArcPaths] new arc-path trajectory, possibly larger or smaller than [occupiedIndices]
     * @param[occupiedIndices] previously occupied indices of the old arc-path trajectory
     * @param[reservedIndices] all indices that were ever used to hold results of the trajectory. They
     * must start with [occupiedIndices], and then potentially contain `null`-ed indices.
     * @return updated ([occupiedIndices], [reservedIndices], updated concrete arc-paths
     * at new occupied indices (to be set))
     * */
    fun adjustArcPathBlueprint(
        newArcPaths: List<ArcPath>,
        occupiedIndices: List<Ix>,
        reservedIndices: List<Ix>,
    ): ExprAdjustmentResult<ConcreteArcPath> {
        var newReservedIndices = reservedIndices
        val trajectoryOfConcreteArcPaths = newArcPaths.map { arcPath ->
            arcPath.eval(objects).firstOrNull() as? ConcreteArcPath
        }
        val newSize = newArcPaths.size
        val sizeIncrease = newSize - occupiedIndices.size
        var accidentallyDeleted = emptySet<Ix>()
        var accidentallyChanged = emptySet<Ix>()
        val newOccupiedIndices: List<Ix>
        if (sizeIncrease > 0) {
            val sizeOverflow = newSize - reservedIndices.size
            if (sizeOverflow > 0) {
                newReservedIndices = newReservedIndices +
                    (expressions.size until (expressions.size + sizeOverflow))
            }
            val addedIndices = newReservedIndices.take(newSize).drop(occupiedIndices.size)
            newArcPaths
                .drop(occupiedIndices.size)
                .zip(addedIndices) { arcPath, newIndex ->
                    expressions[newIndex] = ExprOutput.Just(arcPath)
                    setChildren(newIndex, arcPath)
                    setTier(newIndex, computeTier(newIndex, arcPath))
                    updateObjectTypeAt(newIndex)
                }
            newOccupiedIndices = occupiedIndices + addedIndices
        } else if (sizeIncrease == 0) {
            newOccupiedIndices = occupiedIndices
        } else {
            val excessIndices = occupiedIndices.drop(newSize)
            val accidents = deleteNodes(excessIndices)
            accidentallyDeleted = accidents.allDeletedIndices - excessIndices.toSet()
            accidentallyChanged = accidents.changedIndices
            newOccupiedIndices = occupiedIndices.take(newSize)
        }
        parents2gluedIncidentPoints.clear()
        return ExprAdjustmentResult(
            newOccupiedIndices, newReservedIndices, trajectoryOfConcreteArcPaths,
            accidentallyDeletedIndices = accidentallyDeleted,
            accidentallyChangedIndices = accidentallyChanged,
        )
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
                    expr.copy(
                        parameters = IncidenceParameters(order = zoom * expr.parameters.order)
                    )
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
        if (directIncidence)
            return true
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
        return indirectIncidence
//        return directIncidence || indirectIncidence // short-circuit returns instead
    }
}
