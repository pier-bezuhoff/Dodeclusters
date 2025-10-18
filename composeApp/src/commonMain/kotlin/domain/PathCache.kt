package domain

import androidx.compose.ui.graphics.Path

class PathCache {
    /** __Closed__ [Path]s used when constructing region paths (via intersections and
     * differences) OR when display standalone circles/lines */
    val cachedObjectPaths: MutableList<Path?> = mutableListOf()
    var pathCacheValidity = BooleanArray(0)

    // object index => index of composite paths that depend on it
//    val object2dependents: MutableMap<Ix, Ix> = mutableMapOf()
    // composite paths that depend on several objects
//    var compositePathCacheValidity = BooleanArray(0)

    fun addObject() {
        val previousSize = pathCacheValidity.size
        cachedObjectPaths.add(null)
        pathCacheValidity = pathCacheValidity.copyOf(previousSize + 1)
    }

    fun addObjects(sizeIncrement: Int) {
        val previousSize = pathCacheValidity.size
        for (index in 0 until sizeIncrement) {
            cachedObjectPaths.add(null)
        }
        pathCacheValidity = pathCacheValidity.copyOf(previousSize + sizeIncrement)
    }

    fun invalidateObjectPathAt(objectIndex: Ix) {
        pathCacheValidity[objectIndex] = false
        // rewind could be bad when circle<->cubic<->line change verb/point counts
        // reset seems to be a bit faster than rewind (during stereographic rotation)
//        cachedObjectPaths[objectIndex]?.rewind()
        cachedObjectPaths[objectIndex]?.reset()
    }

    fun removeObjectAt(objectIndex: Ix) {
        cachedObjectPaths[objectIndex] = null
        invalidateObjectPathAt(objectIndex)
    }

    fun clear() {
        cachedObjectPaths.clear()
        pathCacheValidity = BooleanArray(0)
    }

    fun invalidateAll() {
        for (ix in pathCacheValidity.indices) {
            invalidateObjectPathAt(ix)
        }
    }

    fun cacheObjectPath(objectIndex: Ix, path: Path) {
        cachedObjectPaths[objectIndex] = path
        pathCacheValidity[objectIndex] = true
    }
}