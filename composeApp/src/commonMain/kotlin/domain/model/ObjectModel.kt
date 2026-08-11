package domain.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import core.geometry.GCircle
import domain.Ix
import domain.PathCache
import domain.expressions.Expr
import domain.expressions.ExprOutput
import domain.expressions.Expressions
import domain.update
import kotlin.collections.component1
import kotlin.collections.component2

/**
 * Purports to encapsulate & manage [displayObjects] and object-related properties.
 *
 * Very mutable, track [invalidationsState]/[positionInvalidations] for changes and use with care.
 * @param[R] core object type, used in calculations (downscaled, eg [GCircle])
 * @param[D] display object type
 */
sealed class ObjectModel<R : Any, D : Any> {
    abstract val expressions: Expressions<*, *, *, R>
    /**
     * All existing objects; `null`s correspond either to unrealized outputs of
     * [domain.expressions.Expr.OneToMany], or to forever deleted objects (they have `null` `VM.expressions`),
     * or (rarely) to mismatching type casts.
     *
     * NOTE: don't forget to sync changes to [displayObjects] with [downscaledObjects]
     */
    val displayObjects: MutableList<D?> = mutableListOf()
    /**
     * Same as [displayObjects] but additionally downscaled (optimal for calculations).
     *
     * NOTE: u are responsible for MANUALLY sync-ing them
     */
    val downscaledObjects: MutableList<R?> = mutableListOf()
    val indices get() = displayObjects.indices

    val styling: MutableMap<Ix, Styling> = mutableMapOf()
    val phantoms: Set<Ix> get() =
        styling.mapNotNull { (ix, style) ->
            if (style.isPhantom) ix else null
        }.toSet()

    /** layer order */
    val layering: MutableList<Ix> = mutableListOf()

    /**
     * Monotonically increasing sequence, each update is to trigger redraw.
     * Call [invalidate] or [invalidatePositions] to trigger update at appropriate time.
     * Includes both potentially continuous position changes and discrete property changes.
     *
     * See [invalidations] for properties-only changes.
     */
    var positionInvalidations: Int by mutableIntStateOf(0)
        protected set

    /**
     * Monotonically increasing sequence, slower than [positionInvalidations].
     * Call [invalidate] to trigger update at appropriate time.
     *
     * Tracks only discrete properties: color/label/phantom status.
     *
     * Does NOT track continuous expression changes.
     */
    var invalidations: Int by mutableIntStateOf(0)
        protected set

    val pathCache = PathCache()

    /**
     * Use for *continuous* changes.
     * Invalidates the position-state (objects or expressions).
     * Triggers redraw.
     *
     * NOTE: Do not forget to manually call this AFTER finishing changing the position-state.
     */
    fun invalidatePositions() {
        positionInvalidations += 1
    }

    /**
     * Use for *discrete* changes.
     * Invalidates both the position-state AND the property-state. Triggers redraw.
     *
     * NOTE: Do not forget to manually call this AFTER finishing changing the state.
     */
    fun invalidate() {
        positionInvalidations += 1
        invalidations += 1
    }

    /** called each time an object changes */
    protected open fun objectChangedAt(index: Ix) {
        pathCache.invalidateObjectPathAt(index)
    }

    /** Don't forget to [invalidatePositions] post factum */
    fun setDisplayObject(index: Ix, newObject: D?) {
        displayObjects[index] = newObject
        downscaledObjects[index] = newObject?.downscale()
        objectChangedAt(index)
    }

    /** Don't forget to [invalidatePositions] post factum */
    fun setDownscaledObject(index: Ix, newDownscaledObject: R?) {
        displayObjects[index] = newDownscaledObject?.upscale()
        downscaledObjects[index] = newDownscaledObject
        objectChangedAt(index)
    }

    /** Don't forget to [invalidate] post factum */
    fun addDisplayObject(newObject: D?): Ix {
        displayObjects.add(newObject)
        val downscaled = newObject?.downscale()
        downscaledObjects.add(downscaled)
        val ix = displayObjects.lastIndex
        pathCache.addObject()
        expressions.updateObjectTypeAt(ix, downscaled)
        return ix
    }

    /** Don't forget to [invalidate] post factum */
    fun addDownscaledObject(newDownscaledObject: R?): Ix {
        displayObjects.add(newDownscaledObject?.upscale())
        downscaledObjects.add(newDownscaledObject)
        val ix = downscaledObjects.lastIndex
        pathCache.addObject()
        expressions.updateObjectTypeAt(ix, newDownscaledObject)
        return ix
    }

    /** Don't forget to [invalidate] post factum */
    fun addDisplayObjects(newObjects: List<D?>): IntRange {
        val oldSize = displayObjects.size
        for (o in newObjects) {
            addDisplayObject(o)
        }
        return oldSize until displayObjects.size
    }

    /** Don't forget to [invalidate] post factum */
    fun addDownscaledObjects(newObjects: List<R?>): IntRange {
        val oldSize = downscaledObjects.size
        for (obj in newObjects) {
            addDownscaledObject(obj)
        }
        return oldSize until downscaledObjects.size
    }

    /** Don't forget to [expressions].deleteNodes beforehand and [invalidate] post factum */
    fun removeObjectAt(index: Ix) {
        displayObjects[index] = null
        downscaledObjects[index] = null
        styling.remove(index)
        pathCache.removeObjectAt(index)
        expressions.updateObjectTypeAt(index)
    }

    /** Don't forget to [expressions].deleteNodes beforehand and [invalidate] post factum */
    fun removeObjectsAt(indices: Collection<Ix>) {
        for (ix in indices) {
            removeObjectAt(ix)
        }
    }

    /** Clears everything BUT [expressions].
     *  Don't forget to [invalidate] post factum */
    open fun clear() {
        displayObjects.clear()
        downscaledObjects.clear()
        styling.clear()
        pathCache.clear()
    }

    /** Don't forget to [invalidatePositions] post factum */
    fun syncDisplayObjects(indices: Iterable<Ix> = downscaledObjects.indices) {
        for (ix in indices) {
            displayObjects[ix] = downscaledObjects[ix]?.upscale()
            objectChangedAt(ix)
        }
    }

    /** Don't forget to [invalidatePositions] post factum */
    fun syncDownscaledObjects(indices: Iterable<Ix> = displayObjects.indices) {
        for (ix in indices) {
            downscaledObjects[ix] = displayObjects[ix]?.downscale()
            objectChangedAt(ix)
        }
    }

    /** Don't forget to [invalidatePositions] post factum */
    inline fun updateStyle(index: Ix, crossinline update: (Styling) -> Styling) {
        styling.update(index, Styling(), update)
    }

    /**
     * Copy [styling] from source indices onto trajectories specified
     * by [sourceIndex2NewTrajectory]. Trajectory objects are assumed to be laid out in
     * row-column order of [sourceIndex2NewTrajectory]`.flatten` starting from [startIndex]
     * @param[sourceIndex2NewTrajectory] `[(original index ~ style source, [new trajectory of objects])]`
     *
     * Don't forget to [invalidate] post factum
     */
    fun copySourceStylesOntoTrajectories(
        sourceIndex2NewTrajectory: List<Pair<Ix, List<GCircle?>>>,
        startIndex: Ix,
    ) {
        var outputIndex = startIndex
        sourceIndex2NewTrajectory.forEach { (sourceIndex, trajectory) ->
            val sourceStyle = styling[sourceIndex]
                ?.copy(label = null)
            if (sourceStyle != null) {
                trajectory.forEach { _ ->
                    styling[outputIndex] = sourceStyle
                    outputIndex += 1
                }
            } else {
                outputIndex += trajectory.size
            }
        }
    }

    /**
     * Changes [ExprOutput] at [index] to [newExprOutput], and recalculates all
     * children of [index].
     * Don't forget to [invalidatePositions] post factum.
     * @param[newExprOutput] its type must be compatible with
     * the second type parameter of [expressions]
     * @return indices of all updated objects, sorted by tiers (including [index]) */
    fun changeExpr(
        index: Ix,
        newExprOutput: ExprOutput,
    ): List<Ix> {
        val newObject = expressions.changeExpr(index, newExprOutput)
        setDownscaledObject(index, newObject)
        val toBeUpdated = expressions.update(setOf(index))
        val changed = listOf(index) + toBeUpdated
        syncDisplayObjects(changed)
        return changed
    }

    /**
     * Changes [ExprOutput] at [index] to Just([newExpr]), and recalculates all
     * children of [index].
     * Don't forget to [invalidatePositions] post factum.
     * @param[newExpr] its type must be compatible with
     * the second type parameter of [expressions]
     * @return indices of all updated objects, sorted by tiers (including [index]) */
    fun changeExpr(
        index: Ix,
        newExpr: Expr.OneToOne,
    ): List<Ix> =
        changeExpr(index, ExprOutput.Just(newExpr))

    /**
     * Don't forget to [invalidatePositions] post factum.
     * @return all changed indices
     */
    fun setDisplayObjectsWithConsequences(changes: Map<Ix, D?>): List<Ix> {
        for ((ix, newObject) in changes) {
            setDisplayObject(ix, newObject)
        }
        val changeIndices = changes.keys
        val updatedIndices = expressions.update(changeIndices)
        syncDisplayObjects(updatedIndices)
        return changeIndices.toList() + updatedIndices
    }

    /**
     * Don't forget to [invalidatePositions] post factum.
     * @return all indices of changed objects (including [index]) */
    fun setDisplayObjectWithConsequences(
        index: Ix,
        newObject: D?
    ): List<Ix> {
        setDisplayObject(index, newObject)
        val updatedIndices = expressions.update(setOf(index))
        syncDisplayObjects(updatedIndices)
        return updatedIndices + index
    }

    // NOTE: idk, handling of incident points is messy
    /**
     * Apply [translation];scaling;rotation to [targets] (that are all assumed to be free).
     *
     * Scaling and rotation are w.r.t. fixed [focus] by the factor of
     * [zoom] and by [rotationAngle] degrees.
     *
     * Don't forget to [invalidatePositions] post factum.
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

    abstract fun D.downscale(): R
    abstract fun R.upscale(): D
}