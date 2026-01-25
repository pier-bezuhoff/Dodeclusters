package domain.model

import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import core.geometry.GCircle
import domain.Ix
import domain.PathCache
import domain.expressions.Expr
import domain.expressions.Expressions

// TODO: we should include Expressions as a param
/**
 * Purports to encapsulate & manage [objects] and object-related properties.
 *
 * Very mutable, track [invalidationsState]/[invalidations] for changes and use with care.
 * @param[R] object type (eg [GCircle])
 */
sealed class ObjectModel<R : Any> {
    /**
     * All existing objects; `null`s correspond either to unrealized outputs of
     * [domain.expressions.Expr.OneToMany], or to forever deleted objects (they have `null` `VM.expressions`),
     * or (rarely) to mismatching type casts.
     *
     * NOTE: don't forget to sync changes to [objects] with [downscaledObjects]
     */
    val objects: MutableList<R?> = mutableListOf()
    /**
     * Same as [objects] but additionally downscaled (optimal for calculations).
     *
     * NOTE: u are responsible for MANUALLY sync-ing them
     */
    val downscaledObjects: MutableList<R?> = mutableListOf()
    val objectColors: MutableMap<Ix, Color> = mutableMapOf()
    // alt name: ghost[ed] objects
    val phantomObjectIndices: MutableSet<Int> = mutableSetOf()

    abstract val expressions: Expressions<*, *, *, R>

    val invalidationsState: MutableIntState = mutableIntStateOf(0)
    /**
     * Monotonically increasing sequence, each update is to trigger redraw.
     * Call [invalidate] or [invalidatePositions] to trigger update at appropriate time.
     * Includes both potentially continuous position changes and discrete property changes.
     *
     * See [propertyInvalidations] for properties-only changes.
     */
    inline val invalidations: Int get() =
        invalidationsState.value

    val propertyInvalidationsState: MutableIntState = mutableIntStateOf(0)
    /**
     * Monotonically increasing sequence, slower than [invalidations].
     * Call [invalidate] to trigger update at appropriate time.
     *
     * Tracks only discrete properties: color/label/phantom status.
     *
     * Does NOT track expression changes at present (those can be continuous).
     */
    inline val propertyInvalidations: Int get() =
        propertyInvalidationsState.value

    val pathCache = PathCache()

    /**
     * Invalidates the position-state (objects or expressions), use for continuous changes.
     * Triggers redraw.
     *
     * NOTE: Do not forget to manually call this AFTER finishing changing the position-state.
     */
    fun invalidatePositions() {
        invalidationsState.value += 1
    }

    /**
     * Invalidates both the position-state AND the property-state. Triggers redraw.
     *
     * NOTE: Do not forget to manually call this AFTER finishing changing the state.
     */
    fun invalidate() {
        invalidationsState.value += 1
        propertyInvalidationsState.value += 1
    }

    /** Don't forget to [invalidatePositions] post factum */
    private fun setObject(ix: Ix, newObject: R?) {
        objects[ix] = newObject
        downscaledObjects[ix] = newObject?.downscale()
        pathCache.invalidateObjectPathAt(ix)
    }

    /** Don't forget to [invalidatePositions] post factum */
    fun setDownscaledObject(ix: Ix, newDownscaledObject: R?) {
        objects[ix] = newDownscaledObject?.upscale()
        downscaledObjects[ix] = newDownscaledObject
        pathCache.invalidateObjectPathAt(ix)
    }

    /** Don't forget to [invalidate] post factum */
    fun addObject(newObject: R?): Ix {
        objects.add(newObject)
        downscaledObjects.add(newObject?.downscale())
        pathCache.addObject()
        return objects.size - 1
    }

    /** Don't forget to [invalidate] post factum */
    fun addDownscaledObject(newDownscaledObject: R?): Ix {
        objects.add(newDownscaledObject?.upscale())
        downscaledObjects.add(newDownscaledObject)
        pathCache.addObject()
        return objects.size - 1
    }

    /** Don't forget to [invalidate] post factum */
    fun addObjects(newObjects: List<R?>) {
        objects.addAll(newObjects)
        for (o in newObjects) {
            downscaledObjects.add(o?.downscale())
        }
        pathCache.addObjects(newObjects.size)
    }

    /** Don't forget to [invalidate] post factum */
    fun addDownscaledObjects(newObjects: List<R?>) {
        for (o in newObjects) {
            objects.add(o?.upscale())
        }
        downscaledObjects.addAll(newObjects)
        pathCache.addObjects(newObjects.size)
    }

    /** Don't forget to [invalidate] post factum */
    fun removeObjectAt(ix: Ix) {
        objects[ix] = null
        downscaledObjects[ix] = null
        objectColors.remove(ix)
        phantomObjectIndices.remove(ix)
        pathCache.removeObjectAt(ix)
    }

    /** Don't forget to [invalidate] post factum */
    fun removeObjectsAt(ixs: List<Ix>) {
        for (ix in ixs) {
            removeObjectAt(ix)
        }
    }

    /** Don't forget to [invalidate] post factum */
    fun clearObjects() {
        objects.clear()
        downscaledObjects.clear()
        objectColors.clear()
        phantomObjectIndices.clear()
        pathCache.clear()
    }

    /** Don't forget to [invalidatePositions] post factum */
    fun syncObjects(indices: Iterable<Ix> = downscaledObjects.indices) {
        for (ix in indices) {
            objects[ix] = downscaledObjects[ix]?.upscale()
            pathCache.invalidateObjectPathAt(ix)
        }
    }

    /** Don't forget to [invalidatePositions] post factum */
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
     *
     * Don't forget to [invalidate] post factum
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
     *
     * Don't forget to [invalidate] post factum
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

    /** Already includes [invalidatePositions]. [EXPR_ONE_TO_ONE] must be compatible with
     * the second type parameter of [expressions] */
    @Suppress("UNCHECKED_CAST")
    fun <EXPR_ONE_TO_ONE : Expr.OneToOne> changeExpr(
        ix: Ix,
        newExpr: EXPR_ONE_TO_ONE,
    ) {
        val newObject = (expressions as Expressions<*, EXPR_ONE_TO_ONE, *, R>)
            .changeExpr(ix, newExpr)
        setDownscaledObject(ix, newObject)
        val toBeUpdated = expressions.update(setOf(ix))
        syncObjects(toBeUpdated)
        invalidatePositions()
    }

    /** Already includes [invalidatePositions] */
    fun setObjectsWithConsequences(changes: Map<Ix, R?>) {
        for ((ix, newObject) in changes) {
            setObject(ix, newObject)
        }
        val updatedIndices = expressions.update(changes.keys)
        syncObjects(updatedIndices)
        invalidatePositions()
    }

    /** Already includes [invalidatePositions]
     * @return all indices of changed objects (including [ix]) */
    fun setObjectWithConsequences(
        ix: Ix,
        newObject: R?
    ): List<Ix> {
        setObject(ix, newObject)
        val updatedIndices = expressions.update(setOf(ix))
        syncObjects(updatedIndices)
        invalidatePositions()
        return updatedIndices + ix
    }

    // NOTE: idk, handling of incident points is messy
    /**
     * Apply [translation];scaling;rotation to [targets] (that are all assumed to be free).
     *
     * Scaling and rotation are w.r.t. fixed [focus] by the factor of
     * [zoom] and by [rotationAngle] degrees.
     *
     * Already includes [invalidatePositions]
     *
     * @return indices of all changed objects/expressions
     */
    abstract fun transform(
        targets: List<Ix>,
        translation: Offset = Offset.Zero,
        focus: Offset = Offset.Unspecified,
        zoom: Float = 1f,
        rotationAngle: Float = 0f,
    ): Set<Ix>

    abstract fun R.downscale(): R
    abstract fun R.upscale(): R
}