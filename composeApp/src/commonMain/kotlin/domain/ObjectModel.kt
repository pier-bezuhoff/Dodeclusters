package domain

import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import data.geometry.Circle
import data.geometry.GCircle
import data.geometry.ImaginaryCircle
import data.geometry.Line
import data.geometry.Point
import data.geometry.scaled00
import domain.expressions.Expr
import domain.expressions.ExpressionForest

// MAYBE: additionally store GeneralizedCircle representations
/**
 * Purports to encapsulate & manage [objects] and object-related properties.
 * Very mutable, track [invalidationsState]/[invalidations] for changes.
 */
class ObjectModel {
    /**
     * All existing [GCircle]s; `null`s correspond either to unrealized outputs of
     * [Expr.OneToMany], or to forever deleted objects (they have `null` `VM.expressions`),
     * or (rarely) to mismatching type casts.
     *
     * NOTE: don't forget to sync changes to [objects] with [downscaledObjects]
     */
    val objects: MutableList<GCircle?> = mutableListOf()
    /**
     * Same as [objects] but additionally downscaled (optimal for calculations).
     *
     * NOTE: u are responsible for MANUALLY sync-ing them
     */
    val downscaledObjects: MutableList<GCircle?> = mutableListOf()
    val objectColors: MutableMap<Ix, Color> = mutableMapOf()
    // alt name: ghost[ed] objects
    val phantomObjectIndices: MutableSet<Int> = mutableSetOf()

    val invalidationsState: MutableIntState = mutableIntStateOf(0)
    /**
     * Monotonically increasing sequence, each update is to trigger redraw.
     * Call [invalidate]`()` to trigger update at appropriate time.
     */
    inline val invalidations: Int get() =
        invalidationsState.value

    val pathCache = PathCache()

    /**
     * Triggers redraw.
     *
     * NOTE: Do not forget to manually call this after finishing state-altering.
     */
    fun invalidate() {
        invalidationsState.value += 1
    }

    fun setObject(ix: Ix, newObject: GCircle?) {
        objects[ix] = newObject
        downscaledObjects[ix] = newObject?.downscale()
        pathCache.invalidateObjectPathAt(ix)
    }

    fun setDownscaledObject(ix: Ix, newDownscaledObject: GCircle?) {
        objects[ix] = newDownscaledObject?.upscale()
        downscaledObjects[ix] = newDownscaledObject
        pathCache.invalidateObjectPathAt(ix)
    }

    fun addObject(newObject: GCircle?) {
        objects.add(newObject)
        downscaledObjects.add(newObject?.downscale())
        pathCache.addObject()
    }

    fun addDownscaledObject(newDownscaledObject: GCircle?) {
        objects.add(newDownscaledObject?.upscale())
        downscaledObjects.add(newDownscaledObject)
        pathCache.addObject()
    }

    fun addObjects(newObjects: List<GCircle?>) {
        objects.addAll(newObjects)
        for (o in newObjects) {
            downscaledObjects.add(o?.downscale())
        }
        pathCache.addObjects(newObjects.size)
    }

    fun addDownscaledObjects(newObjects: List<GCircle?>) {
        for (o in newObjects) {
            objects.add(o?.upscale())
        }
        downscaledObjects.addAll(newObjects)
        pathCache.addObjects(newObjects.size)
    }

    fun removeObjectAt(ix: Ix) {
        objects[ix] = null
        downscaledObjects[ix] = null
        objectColors.remove(ix)
        phantomObjectIndices.remove(ix)
        pathCache.removeObjectAt(ix)
    }

    fun removeObjectsAt(ixs: List<Ix>) {
        for (ix in ixs) {
            removeObjectAt(ix)
        }
    }

    fun clearObjects() {
        objects.clear()
        downscaledObjects.clear()
        objectColors.clear()
        phantomObjectIndices.clear()
        pathCache.clear()
    }

    fun syncObjects(indices: Iterable<Ix> = downscaledObjects.indices) {
        for (ix in indices) {
            objects[ix] = downscaledObjects[ix]?.upscale()
            pathCache.invalidateObjectPathAt(ix)
        }
    }

    fun syncDownscaledObjects(indices: Iterable<Ix> = objects.indices) {
        for (ix in indices) {
            downscaledObjects[ix] = objects[ix]?.downscale()
            pathCache.invalidateObjectPathAt(ix)
        }
    }

    /**
     * Copy [objectColors] from source indices onto trajectories specified
     * by [sourceIndex2NewTrajectory]. Trajectory objects are assumed to be laid out in
     * row-column order of [sourceIndex2NewTrajectory]`.flatten` starting from [startIndex]
     * @param[sourceIndex2NewTrajectory] `[(original index ~ style source, [new trajectory of objects])]`
     */
    fun copySourceColorsOntoTrajectories(
        sourceIndex2NewTrajectory: List<Pair<Ix, List<GCircle?>>>,
        startIndex: Ix,
    ) {
        var outputIndex = startIndex
        sourceIndex2NewTrajectory.forEach { (sourceIndex, trajectory) ->
            val sourceColor = objectColors[sourceIndex]
            if (sourceColor != null) {
                trajectory.forEach { _ ->
                    objectColors[outputIndex] = sourceColor
                    outputIndex += 1
                }
            } else {
                outputIndex += trajectory.size
            }
        }
    }

    /**
     * Copy [objectColors] from source indices onto trajectories specified
     * by [sourceIndex2TrajectoryOfIndices].
     * @param[sourceIndex2TrajectoryOfIndices] `[(original index ~ style source, [trajectory of indices of objects])]`,
     */
    fun copySourceColorsOntoTrajectories(
        sourceIndex2TrajectoryOfIndices: List<Pair<Ix, List<Ix>>>,
    ) {
        sourceIndex2TrajectoryOfIndices.forEach { (sourceIndex, trajectory) ->
            val sourceColor = objectColors[sourceIndex]
            if (sourceColor != null) {
                trajectory.forEach { ix ->
                    objectColors[ix] = sourceColor
                }
            }
        }
    }

    // NOTE: idk, handling of incident points is messy
    /**
     * Apply [translation];scaling;rotation to [targets] (that are all assumed to be free).
     *
     * Scaling and rotation are w.r.t. fixed [focus] by the factor of
     * [zoom] and by [rotationAngle] degrees.
     *
     * NOTE: remember to record a command before
     */
    fun transform(
        expressions: ExpressionForest,
        targets: List<Ix>,
        translation: Offset = Offset.Zero,
        focus: Offset = Offset.Unspecified,
        zoom: Float = 1f,
        rotationAngle: Float = 0f,
    ) {
        if (targets.isEmpty()) {
            return
        }
        val targetsSet = targets.toSet()
        val requiresZoom = zoom != 1f
        val requiresRotation = rotationAngle != 0f
        val allIncidentPoints = mutableListOf<Ix>()
        if (!requiresZoom && !requiresRotation) {
            // we assume the transformation is not Id
            for (ix in targets) {
                val o = objects[ix]
                objects[ix] = o?.translated(translation)
                if (o is Line) {
                    expressions.getIncidentPointsTo(ix, allIncidentPoints)
                }
            }
        } else {
            for (ix in targets) {
                when (val o = objects[ix]) {
                    is Circle -> {
                        objects[ix] = o.transformed(translation, focus, zoom, rotationAngle)
                        if (requiresRotation) {
                            expressions.getIncidentPointsTo(ix, allIncidentPoints)
                        }
                    }
                    is Line -> {
                        objects[ix] = o.transformed(translation, focus, zoom, rotationAngle)
                        expressions.getIncidentPointsTo(ix, allIncidentPoints)
                    }
                    is Point -> {
                        objects[ix] = o.transformed(translation, focus, zoom, rotationAngle)
                    }
                    is ImaginaryCircle -> {
                        objects[ix] = o.transformed(translation, focus, zoom, rotationAngle)
                    }
                    null -> {}
                }
            }
        }
        allIncidentPoints -= targetsSet
        for (j in allIncidentPoints) {
            val p0 = objects[j] as? Point
            val p = p0?.transformed(translation, focus, zoom, rotationAngle)
            downscaledObjects[j] = p?.downscale() // objects[ix] will be recalculated & set during update phase
        }
        syncDownscaledObjects(targets)
        expressions.adjustIncidentPointExpressions(allIncidentPoints)
        val updatedIndices = expressions.update(targets)
        syncObjects(updatedIndices)
        invalidate()
    }


    companion object {
        const val UPSCALING_FACTOR = 2_000.0
        const val DOWNSCALING_FACTOR = 1.0/UPSCALING_FACTOR

        fun GCircle.downscale(): GCircle =
            scaled00(DOWNSCALING_FACTOR)
        fun GCircle.upscale(
//            screenCenter: Offset = Offset.Zero
        ): GCircle =
            when (this) {
                is Circle -> {
                    // this introduces visual errors
//                    val upscaledCircle =
//                        copy(x = UPSCALING_FACTOR * x, y = UPSCALING_FACTOR * y, radius = UPSCALING_FACTOR * radius)
//                    if (upscaledCircle.radius >= MIN_CIRCLE_TO_LINE_APPROXIMATION_RADIUS)
//                        upscaledCircle.approximateToLine(Offset.Zero)
//                    else upscaledCircle
                    copy(x = UPSCALING_FACTOR * x, y = UPSCALING_FACTOR * y, radius = UPSCALING_FACTOR * radius)
                }
                is Line -> copy(c = UPSCALING_FACTOR * c)
                is Point -> copy(x = UPSCALING_FACTOR * x, y = UPSCALING_FACTOR * y)
                is ImaginaryCircle -> copy(x = UPSCALING_FACTOR * x, y = UPSCALING_FACTOR * y, radius = UPSCALING_FACTOR * radius)
            }
    }
}

