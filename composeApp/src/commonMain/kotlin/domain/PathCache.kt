package domain

import androidx.compose.ui.graphics.Path

class PathCache {
    /** __Closed__ [Path]s used when constructing region paths (via intersections and
     * differences) OR for standalone circles/lines */
    val objectPaths: MutableList<Path?> = mutableListOf()
    var objectPathValidity = BooleanArray(0)
    /** Arc-paths that depend on a subset [objectPaths] */
    val dependentPaths: MutableList<Path?> = mutableListOf()
    var dependentPathValidity = BooleanArray(0)
    /** object index -> dependent path indices */
    val dependencies: MutableMap<Ix, Set<Int>> = mutableMapOf()

    // object index => index of composite paths that depend on it
//    val object2dependents: MutableMap<Ix, Ix> = mutableMapOf()
    // composite paths that depend on several objects
//    var compositePathCacheValidity = BooleanArray(0)

    fun addObject() {
        val previousSize = objectPathValidity.size
        objectPaths.add(null)
        objectPathValidity = objectPathValidity.copyOf(previousSize + 1)
    }

    fun addObjects(sizeIncrement: Int) {
        val previousSize = objectPathValidity.size
        for (index in 0 until sizeIncrement) {
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
        for (dependentIndex in dependencies[objectIndex].orEmpty()) {
            dependentPathValidity[dependentIndex] = false
            dependentPaths[dependentIndex]?.reset()
        }
    }

    fun removeObjectAt(objectIndex: Ix) {
        objectPaths[objectIndex] = null
        invalidateObjectPathAt(objectIndex)
        dependencies.remove(objectIndex)
    }

    fun clear() {
        objectPaths.clear()
        objectPathValidity = BooleanArray(0)
        dependentPaths.clear()
        dependencies.clear()
        dependentPathValidity = BooleanArray(0)
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

    fun addDependent(deps: Set<Ix>) {
        val dependentIndex = dependentPaths.size
        dependentPaths.add(null)
        dependentPathValidity = dependentPathValidity.copyOf(dependentPathValidity.size + 1)
        for (dependencyIndex in deps) {
            dependencies[dependencyIndex] =
                dependencies[dependencyIndex]?.plus(dependentIndex) ?: setOf(dependentIndex)
        }
    }

    fun updateDependent(dependentIndex: Int, deps: Set<Ix>) {
        for (objectIndex in dependencies.keys) {
            val dependents = dependencies[objectIndex]
            if (objectIndex in deps) {
                if (dependents == null)
                    dependencies[objectIndex] = setOf(dependentIndex)
                else if (dependentIndex !in dependents)
                    dependencies[objectIndex] = dependents + dependentIndex
            } else if (dependents != null) {
                dependencies[objectIndex] = dependents - dependentIndex
            }
        }
        dependentPathValidity[dependentIndex] = false
    }

    fun removeDependent(dependentIndex: Int) {
        dependentPaths.removeAt(dependentIndex)
        dependentPathValidity =
            dependentPathValidity.slice(0 until dependentIndex)
                .plus(dependentPathValidity.drop(dependentIndex + 1))
                .toBooleanArray()
        for (objectIndex in dependencies.keys) {
            val dependents = dependencies[objectIndex]
            if (dependents != null) {
                dependencies[objectIndex] = dependents - dependentIndex
            }
        }
    }

    fun cacheDependentPath(dependentIndex: Int, path: Path) {
        dependentPaths[dependentIndex] = path
        dependentPathValidity[dependentIndex] = true
    }
}