package domain

import androidx.collection.IntObjectMap
import androidx.collection.IntSet

fun IntSet.toList(): List<Int> {
    val result = mutableListOf<Int>()
    forEach { x ->
        result.add(x)
    }
    return result
}

inline fun <reified V, reified R> IntObjectMap<V>.mapNotNull(
    crossinline transform: (Int, V) -> R?
): List<R> {
    val result = mutableListOf<R>()
    forEach { key, value ->
        val r = transform(key, value)
        if (r != null)
            result.add(r)
    }
    return result
}