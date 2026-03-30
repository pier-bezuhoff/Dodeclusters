package domain

import androidx.compose.ui.graphics.Path

class PathCache {
    /** __Closed__ [Path]s used when constructing region paths (via intersections and
     * differences) OR for standalone circles/lines */
    val objectPaths: MutableList<Path?> = mutableListOf()
    var objectPathValidity = BooleanArray(0)

    fun addObject() {
        val previousSize = objectPathValidity.size
        objectPaths.add(null)
        objectPathValidity = objectPathValidity.copyOf(previousSize + 1)
    }

    fun addObjects(sizeIncrement: Int) {
        val previousSize = objectPathValidity.size
        repeat(sizeIncrement) {
            objectPaths.add(null)
        }
        objectPathValidity = objectPathValidity.copyOf(previousSize + sizeIncrement)
    }

    fun invalidateObjectPathAt(objectIndex: Ix) {
        objectPathValidity[objectIndex] = false
        // rewind could be bad when circle<->cubic<->line change verb/point counts
        // reset seems to be a bit faster than rewind (during stereographic rotation)
//        cachedObjectPaths[objectIndex]?.rewind()
        objectPaths[objectIndex]?.reset()
    }

    fun removeObjectAt(objectIndex: Ix) {
        objectPaths[objectIndex] = null
        invalidateObjectPathAt(objectIndex)
    }

    fun clear() {
        objectPaths.clear()
        objectPathValidity = BooleanArray(0)
    }

    fun invalidateAll() {
        for (ix in objectPathValidity.indices) {
            invalidateObjectPathAt(ix)
        }
    }

    fun cacheObjectPath(objectIndex: Ix, path: Path) {
        objectPaths[objectIndex] = path
        objectPathValidity[objectIndex] = true
    }
}