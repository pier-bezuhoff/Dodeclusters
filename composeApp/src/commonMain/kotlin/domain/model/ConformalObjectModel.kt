package domain.model

import androidx.compose.ui.geometry.Offset
import core.geometry.CircleOrLine
import core.geometry.ConcreteArcPath
import core.geometry.GCircle
import core.geometry.GCircleOrConcreteArcPath
import core.geometry.Line
import core.geometry.Point
import core.geometry.scaled00
import domain.Ix
import domain.cluster.Constellation
import domain.expressions.ArcPath
import domain.expressions.ArcPathIncidenceParameters
import domain.expressions.ConformalExpressions
import domain.expressions.Expr
import domain.expressions.ExprOutput
import domain.expressions.IncidenceParameters
import domain.expressions.ObjectConstruct
import domain.expressions.computeIntersection
import domain.update

// MAYBE: additionally store GeneralizedCircle representations
/**
 * Purports to encapsulate & manage all objects ([GCircle]s) and object-related properties.
 * Very mutable, track [invalidationsState]/[invalidations] for changes and use with care.
 */
class ConformalObjectModel : ObjectModel<GCircleOrConcreteArcPath, GCircleOrConcreteArcPath>() {

    override var expressions: ConformalExpressions =
        ConformalExpressions(emptyMap(), mutableListOf())

    inline val pointIndices: Set<Ix> get() = expressions.pointIndices
    inline val arcPathIndices: Set<Ix> get() = expressions.arcPathIndices
    inline val circleOrLineIndices: Set<Ix> get() = expressions.circleOrLineIndices
    inline val gCircleIndices: Set<Ix> get() = expressions.gCircleIndices

    fun getInfinityIndex(): Ix? {
        val infinityIndex = displayObjects.indexOfFirst { it == Point.CONFORMAL_INFINITY }
        return if (infinityIndex == -1) {
            null
        } else infinityIndex
    }

    inline fun getArcPath(index: Ix): ArcPath? =
        expressions.expressions[index]?.expr as? ArcPath

    /** @return all changed indices */
    fun modifyArcPath(index: Ix, arcPath: ArcPath): List<Ix> {
        val changedIndices = changeExpr(index, arcPath)
        for (ix in changedIndices) {
            objectChangedAt(ix)
        }
        return changedIndices
    }

    fun changeToIncidence(pointIndex: Ix, carrierIndex: Ix): List<Ix> {
        val point = downscaledObjects[pointIndex] as? Point ?: return emptyList()
        val carrier = downscaledObjects[carrierIndex] as? CircleOrLine ?: return emptyList()
        val order = carrier.point2order(point)
        val parameters = IncidenceParameters(order)
        val expr = Expr.Incidence(parameters, carrierIndex)
        val changes = changeExpr(pointIndex, expr)
        invalidate()
        return changes
    }

    fun changeToArcPathIncidence(pointIndex: Ix, arcPathIndex: Ix): List<Ix> {
        val point = downscaledObjects[pointIndex] as? Point ?: return emptyList()
        val arcPath = downscaledObjects[arcPathIndex] as? ConcreteArcPath ?: return emptyList()
        val (_, arcIndex, arcPercentage) = arcPath.project(point)
        val parameters = ArcPathIncidenceParameters(arcIndex, arcPercentage)
        val expr = Expr.ArcPathIncidence(parameters, arcPathIndex)
        val changes = changeExpr(pointIndex, expr)
        invalidate()
        return changes
    }

    fun changeToIntersection(pointIndex: Ix, carrier1Index: Ix, carrier2Index: Ix): List<Ix> {
        val point = downscaledObjects[pointIndex] as? Point ?: return emptyList()
        val carrier1 = downscaledObjects[carrier1Index] as? CircleOrLine ?: return emptyList()
        val carrier2 = downscaledObjects[carrier2Index] as? CircleOrLine ?: return emptyList()
        val (ip1, ip2) = computeIntersection(carrier1, carrier2)
        if (ip1 == null && ip2 == null)
            return emptyList()
        val expr = Expr.Intersection(carrier1Index, carrier2Index)
        val outputIndex = if (
            ip2 == null ||
            ip1 != null && point.distance2From(ip1) <= point.distance2From(ip2)
        ) 0
        else 1
        val changes = changeExpr(pointIndex, ExprOutput.OneOf(expr, outputIndex))
        invalidate()
        return changes
    }

    fun update(changedIndices: Set<Ix>): List<Ix> {
        val updatedIndices = expressions.update(changedIndices)
        syncDisplayObjects(updatedIndices)
        return updatedIndices
    }

    /** Call after expressions.deleteNodes
     * @return all updated indices, including [changedIndices] */
    fun forceUpdate(changedIndices: Set<Ix>): List<Ix> {
        val updatedIndices = expressions.forceUpdate(changedIndices)
        syncDisplayObjects(updatedIndices)
        return updatedIndices
    }

    // NOTE: handling of incident points on non-glued dependent objects is off
    override fun transform(
        targets: List<Ix>,
        translation: Offset,
        focus: Offset,
        zoom: Float,
        rotationAngle: Float,
    ): Set<Ix> {
        if (targets.isEmpty())
            return emptySet()
        val targetsSet = targets.toSet()
        val requiresZoom = zoom != 1f
        val requiresRotation = rotationAngle != 0f
        if (requiresZoom || requiresRotation) {
            for (ix in targets) {
                val o = displayObjects[ix] as? GCircle ?: continue
                displayObjects[ix] = o.transformed(translation, focus, zoom, rotationAngle)
            }
        } else { // translation only
            // we assume the transformation is not Id
            for (ix in targets) {
                val o = displayObjects[ix] as? GCircle ?: continue
                displayObjects[ix] = o.translated(translation)
            }
        }
        val gluedIncidentPoints = expressions.getGluedIncidentPoints(targetsSet)
//        println("glued to $targets: $gluedIncidentPoints")
        for (j in gluedIncidentPoints) {
            val p0 = displayObjects[j] as? Point ?: continue
            val p = p0.transformed(translation, focus, zoom, rotationAngle)
            downscaledObjects[j] = p.downscale()
            // objects are synced later with syncObjects(updatedIndices)
        }
        syncDownscaledObjects(targets)
        val updatedIndices = gluedIncidentPoints + expressions.update(targetsSet, excluded = gluedIncidentPoints)
        expressions.adjustIncidentPointExpressions(gluedIncidentPoints)
        // MAYBE: it's better to recalc glued atp (we transformed them, then adjusted the order)
        syncDisplayObjects(updatedIndices)
        invalidatePositions()
        return targetsSet + updatedIndices
    }

    fun loadConstellation(constellation: Constellation) {
        clear()
        for (objectConstruct in constellation.objects) {
            val o = when (objectConstruct) {
                is ObjectConstruct.ConcreteCircle -> objectConstruct.circle
                is ObjectConstruct.ConcreteLine -> objectConstruct.line.normalized()
                is ObjectConstruct.ConcretePoint -> objectConstruct.point
                is ObjectConstruct.Dynamic -> null // to-be-computed during reEval()
            }
            // cannot use generic addDisplayObject cuz expressions were
            // not yet initialized, but each addDisplayObject calls
            // expressions.updateObjectTypeAt
            displayObjects.add(o)
            val downscaled = o?.downscale()
            downscaledObjects.add(downscaled)
            pathCache.addObject()
        }
        expressions = ConformalExpressions(
            initialExpressions = constellation.toExpressionMap(),
            objects = downscaledObjects,
        )
        expressions.reEval() // calculates all dependent objects
        syncDisplayObjects()
//        expressions.update(
//            expressions.scaleLineIncidenceExpressions(DOWNSCALING_FACTOR)
//        )
        val objectSize = displayObjects.size
        for ((ix, color) in constellation.objectColors) {
            if (ix < objectSize) {
                styling.update(ix, Styling()) {
                    it.copy(borderColor = color)
                }
            }
        }
        for (phantomIndex in constellation.phantoms) {
            if (phantomIndex < objectSize) {
                styling.update(phantomIndex, Styling()) {
                    it.copy(isPhantom = true)
                }
            }
        }
    }

    override fun clear() {
        expressions.clear()
        super.clear()
        expressions = ConformalExpressions(mapOf(), mutableListOf())
    }

    fun loadState(state: SaveState) {
        clear()
        for (o in state.objects) {
            // cannot use generic addDisplayObjects cuz expressions were
            // not yet initialized, but each addDisplayObject calls
            // expressions.updateObjectTypeAt
            val obj = when (o) {
                is Line -> o.normalized()
                else -> o
            }
            displayObjects.add(obj)
            downscaledObjects.add(obj?.downscale())
            pathCache.addObject()
        }
        expressions = ConformalExpressions(
            initialExpressions = state.expressions,
            objects = downscaledObjects,
        )
        val objectSize = displayObjects.size
        state.styling.filterTo(styling) { (ix, _) -> ix < objectSize }
        expressions.reEval()
        syncDisplayObjects(displayObjects.indices)
    }

    override fun GCircleOrConcreteArcPath.downscale(): GCircleOrConcreteArcPath =
        when (this) {
            is GCircle ->
                this.downscale()
            is ConcreteArcPath ->
                this.downscale()
        }

    override fun GCircleOrConcreteArcPath.upscale(): GCircleOrConcreteArcPath =
        when (this) {
            is GCircle ->
                this.upscale()
            // tbh it'd be better if concrete arc-path were constructed from upscaled points
            // to begin with
            is ConcreteArcPath ->
                this.upscale()
        }

    fun GCircle.downscale(): GCircle =
        scaled00(DOWNSCALING_FACTOR)

    fun GCircle.upscale(
//            screenCenter: Offset = Offset.Zero
    ): GCircle =
        this.scaled00(UPSCALING_FACTOR)
                // this introduces visual errors
//                    val upscaledCircle =
//                        copy(x = UPSCALING_FACTOR * x, y = UPSCALING_FACTOR * y, radius = UPSCALING_FACTOR * radius)
//                    if (upscaledCircle.radius >= MIN_CIRCLE_TO_LINE_APPROXIMATION_RADIUS)
//                        upscaledCircle.approximateToLine(Offset.Zero)
//                    else upscaledCircle

    fun ConcreteArcPath.downscale(): ConcreteArcPath =
        scaled00(DOWNSCALING_FACTOR)

    fun ConcreteArcPath.upscale(): ConcreteArcPath =
        scaled00(UPSCALING_FACTOR)

    companion object {
        const val UPSCALING_FACTOR = 2_000.0
        const val DOWNSCALING_FACTOR = 1.0/UPSCALING_FACTOR
    }
}