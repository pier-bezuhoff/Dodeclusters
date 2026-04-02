package domain.model

import androidx.compose.ui.geometry.Offset
import core.geometry.ConcreteArcPath
import core.geometry.GCircle
import core.geometry.Point
import core.geometry.scaled00
import domain.Ix
import domain.cluster.Constellation
import domain.expressions.ArcPath
import core.geometry.GCircleOrConcreteAcPath
import domain.expressions.ConformalExpressions
import domain.expressions.ObjectConstruct
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator
import kotlin.collections.set

// MAYBE: additionally store GeneralizedCircle representations
/**
 * Purports to encapsulate & manage all objects ([GCircle]s) and object-related properties.
 * Very mutable, track [invalidationsState]/[invalidations] for changes and use with care.
 */
class ConformalObjectModel : ObjectModel<GCircleOrConcreteAcPath, GCircleOrConcreteAcPath>() {

    override var expressions: ConformalExpressions =
        ConformalExpressions(emptyMap(), mutableListOf())

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

    /** Call after expressions.deleteNodes
     * @return all updated indices, including [changedIndices] */
    fun forceUpdate(changedIndices: Set<Ix>): List<Ix> {
        val updatedIndices = expressions.forceUpdate(changedIndices)
        syncDisplayObjects(updatedIndices)
        return updatedIndices
    }

    override fun removeObjectAt(index: Ix) {
        displayObjects[index] = null
        downscaledObjects[index] = null
        borderColors.remove(index)
        fillColors.remove(index)
        phantomObjectIndices.remove(index)
        pathCache.removeObjectAt(index)
        expressions.updateObjectTypeAt(index)
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
                is ObjectConstruct.ConcreteLine -> objectConstruct.line
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
                borderColors[ix] = color
            }
        }
        for (phantomIndex in constellation.phantoms) {
            if (phantomIndex < objectSize) {
                phantomObjectIndices.add(phantomIndex)
            }
        }
    }

    override fun clear() {
        super.clear()
        expressions = ConformalExpressions(mapOf(), mutableListOf())
    }

    fun loadState(state: SaveState) {
        clear()
        for (o in state.objects) {
            // cannot use generic addDisplayObjects cuz expressions were
            // not yet initialized, but each addDisplayObject calls
            // expressions.updateObjectTypeAt
            displayObjects.add(o)
            downscaledObjects.add(o?.downscale())
            pathCache.addObject()
        }
        expressions = ConformalExpressions(
            initialExpressions = state.expressions,
            objects = downscaledObjects,
        )
        val objectSize = displayObjects.size
        for ((ix, color) in state.borderColors) {
            if (ix < objectSize) {
                borderColors[ix] = color
            }
        }
        for ((ix, color) in state.fillColors) {
            if (ix < objectSize) {
                fillColors[ix] = color
            }
        }
        for (phantomIndex in state.phantoms) {
            if (phantomIndex < objectSize) {
                phantomObjectIndices.add(phantomIndex)
            }
        }
        expressions.reEval()
        syncDisplayObjects(displayObjects.indices)
    }

    override fun GCircleOrConcreteAcPath.downscale(): GCircleOrConcreteAcPath =
        when (this) {
            is GCircle ->
                this.downscale()
            is ConcreteArcPath ->
                this.downscale()
        }

    override fun GCircleOrConcreteAcPath.upscale(): GCircleOrConcreteAcPath =
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