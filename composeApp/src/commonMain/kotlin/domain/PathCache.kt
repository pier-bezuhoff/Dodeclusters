package domain

import androidx.compose.ui.graphics.Path

class PathCache {
    /** used when drawing objects (paths may be not closed) */
    val cachedObjectPaths: MutableList<Path?> = mutableListOf()
    /** use for region path calc (only closed paths) */
    val cachedClosedObjectPaths: MutableList<Path?> = mutableListOf()
    var pathCacheValidity = BooleanArray(0)

    fun addObject() {
        val previousSize = pathCacheValidity.size
        cachedObjectPaths.add(null)
        cachedClosedObjectPaths.add(null)
        pathCacheValidity = pathCacheValidity.copyOf(previousSize + 1)
    }

    fun addObjects(sizeIncrement: Int) {
        val previousSize = pathCacheValidity.size
        for (index in 0 until sizeIncrement) {
            cachedObjectPaths.add(null)
            cachedClosedObjectPaths.add(null)
        }
        pathCacheValidity = pathCacheValidity.copyOf(previousSize + sizeIncrement)
    }

    fun invalidateObjectPathAt(ix: Ix) {
        pathCacheValidity[ix] = false
        // TODO: test rewind vs reset performance
        // could be worse when circle<->cubic<->line change verb/point counts
        cachedObjectPaths[ix]?.reset()
        cachedClosedObjectPaths[ix]?.reset()
    }

    fun removeObjectAt(ix: Ix) {
        cachedObjectPaths[ix] = null
        cachedClosedObjectPaths[ix] = null
        invalidateObjectPathAt(ix)
    }

    fun clear() {
        cachedObjectPaths.clear()
        cachedClosedObjectPaths.clear()
        pathCacheValidity = BooleanArray(0)
    }

    fun cacheObjectPath(ix: Ix, path: Path) {
        cachedObjectPaths[ix] = path
        pathCacheValidity[ix] = true
    }

    fun cacheClosedObjectPath(ix: Ix, path: Path) {
        cachedClosedObjectPaths[ix] = path
        pathCacheValidity[ix] = true
    }
}