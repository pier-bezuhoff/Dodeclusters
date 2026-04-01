@file:Suppress("NOTHING_TO_INLINE")

package domain

inline fun <T> Iterable<T>.filterIndices(
    crossinline predicate: (T) -> Boolean
): List<Int> =
    this.withIndex()
        .filter { (_, t) -> predicate(t) }
        .map { (i, _) -> i }

inline fun <T> Iterable<T>.partitionIndices(
    crossinline predicate: (T) -> Boolean
): Pair<List<Int>, List<Int>> =
    this.withIndex()
        .partition { (_, t) -> predicate(t) }
        .let { (yes, no) ->
            Pair(
                yes.map { (i, _) -> i },
                no.map { (i, _) -> i },
            )
        }

/** equivalent to `take(index) + drop(index + 1)` */
inline fun <T> List<T>.withoutElementAt(index: Int): List<T> {
    val list = this.toMutableList()
    list.removeAt(index)
    return list
}

fun <T> List<T>.withoutElementsAt(indices: Set<Ix>): List<T> =
    filterIndexed { index, _ -> index !in indices }

/** remove [element] if it's in, add it otherwise */
fun <T> List<T>.xor(element: T): List<T> =
    if (element in this)
        this - element
    else
        this + element

/**
 * Removes elements at [indices] starting from the highest index.
 * Not atomic, so if 2+ threads are trying to write it's NG
 */
inline fun <E> MutableList<E>.removeAtIndices(indices: List<Ix>) {
    for (ix in indices.sortedDescending()) {
        removeAt(ix)
    }
}

// reference: https://docs.python.org/3/library/itertools.html#itertools.combinations
/**
 * Produces indices for non-repeating combinations out
 * of [n] objects in total, [r] at time (in lexicographic order).
 *
 * `combinations(4, 3) → 012 013 023 123`
 */
fun combinations(n: Int, r: Int): Sequence<List<Int>> = sequence {
    val indices = (0 until r).toMutableList()
    if (r > n)
        return@sequence
    yield(indices.toList())
    while (true) {
        val i = (0 until r).indexOfLast { i ->
            indices[i] != i + n - r
        }
        if (i == -1)
            return@sequence
        indices[i] += 1
        for (j in (i + 1) until r) // diagonalizing for non-repeating combinations
            indices[j] = indices[j-1] + 1
        yield(indices.toList())
    }
}

fun <T> Iterable<T>.updated(index: Int, newElement: T): List<T> =
    this.toMutableList().apply { this[index] = newElement }

inline fun <T> Iterable<T>.updated(
    index: Int,
    crossinline transformValue: (T) -> T,
): List<T> =
    this.toMutableList().apply {
        this[index] = transformValue(this[index])
    }

fun <T> Iterable<T>.updated(vararg index2element: Pair<Int, T>): List<T> {
    val list = this.toMutableList()
    for ((ix, element) in index2element) {
        list[ix] = element
    }
    return list
}

fun reindexingMap(originalIndices: Iterable<Ix>, deletedIndices: Set<Ix>): Map<Ix, Ix> {
    val re = mutableMapOf<Ix, Ix>()
    var shift = 0
    for (ix in originalIndices) {
        if (ix in deletedIndices)
            shift += 1
        else
            re[ix] = ix - shift
    }
    return re
}

/**
 * Sorts elements by their frequency
 * @return List of distinct elements from most common to most rare
 */
fun <T> Iterable<T>.sortedByFrequency(): List<T> =
    this.groupingBy { it }
        .eachCount()
        .entries
        .sortedByDescending { (_, count) -> count }
        .map { (t, _) -> t }

inline fun <T, K> Iterable<T>.mostCommonOf(
    crossinline key: (T) -> K,
): K? =
    this
        .map { key(it) }
        .groupingBy { it }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key

/** @return `null` instead of `-1` */
fun <T> List<T>.indexOfOrNull(element: T): Int? {
    val index = indexOf(element)
    return if (index == -1)
        null
    else
        index
}

fun <T> Iterable<T>.collectionSizeOrDefault(default: Int): Int =
    if (this is Collection<*>) this.size else default

inline fun <reified T> List<T>.zipForEach(
    anotherList: List<T>,
    crossinline action: (T, T) -> Unit,
) {
    require(this.size == anotherList.size) { "List sizes must be the same" }
    for (i in 0 until this.size) {
        action(this[i], anotherList[i])
    }
}

/**
 * Returns a list of values built from the elements of `this` collection,
 * the [other] and [another] collections with the same index
 * using the provided [transform] function applied to each triple of elements.
 * The returned list has length of the shortest collection.
 */
inline fun <reified A, reified B, reified C, reified R> Iterable<A>.zip3(
    other: Iterable<B>,
    another: Iterable<C>,
    crossinline transform: (a: A, b: B, c: C) -> R
): List<R> {
    val first = iterator()
    val second = other.iterator()
    val third = another.iterator()
    val list = ArrayList<R>(minOf(
        collectionSizeOrDefault(10), // why 10?
        other.collectionSizeOrDefault(10),
        another.collectionSizeOrDefault(10),
    ))
    while (first.hasNext() && second.hasNext() && third.hasNext()) {
        list.add(transform(first.next(), second.next(), third.next()))
    }
    return list
}

/**
 * aka zipN (non-truncating), when some lists are shorter than required they are
 * filled with [filler]
 */
@Suppress("NOTHING_TO_INLINE")
inline fun <T> List<List<T>>.transpose(filler: T? = null): List<List<T?>> {
    val maxSize = this.maxOfOrNull { it.size } ?: return emptyList()
    return (0 until maxSize).map { i ->
        this.map { row -> row.getOrElse(i) { filler } }
    }
}

inline fun <reified T> List<T>.topIndexBy(
    crossinline measurer: (element: T) -> Double,
    crossinline measureFilter: (measure: Double) -> Boolean = { true },
): Int? {
    var top: Double = Double.NEGATIVE_INFINITY
    var topIndex: Int? = null
    for (i in this.indices) {
        val element = this[i]
        val measure = measurer(element)
        if (measureFilter(measure)) {
            if (topIndex == null || measure > top) {
                topIndex = i
                top = measure
            }
        }
    }
    return topIndex
}

inline fun <reified T> List<T>.bottomIndexBy(
    crossinline measurer: (element: T) -> Double,
    crossinline measureFilter: (measure: Double) -> Boolean = { true },
): Int? {
    var bottom: Double = Double.POSITIVE_INFINITY
    var bottomIndex: Int? = null
    for (i in this.indices) {
        val element = this[i]
        val measure = measurer(element)
        if (measureFilter(measure)) {
            if (bottomIndex == null || measure < bottom) {
                bottomIndex = i
                bottom = measure
            }
        }
    }
    return bottomIndex
}

// around 1x-1.5x times faster than built-in chain calls mapIndexed, sortedBy, etc on asSequence()
inline fun <reified T> List<T>.bottom2IndicesBy(
    crossinline measurer: (T) -> Double,
    crossinline indexFilter: (index: Int) -> Boolean = { true },
    crossinline elementFilter: (element: T) -> Boolean = { true },
    crossinline measureFilter: (measure: Double) -> Boolean = { true },
): List<Int> {
    var bottom1: Double = Double.POSITIVE_INFINITY
    var bottom2: Double = Double.POSITIVE_INFINITY
    var bottom1Index: Int? = null
    var bottom2Index: Int? = null
    for (i in this.indices) {
        if (!indexFilter(i))
            continue
        val element = this[i]
        if (!elementFilter(element))
            continue
        val measure = measurer(element)
        if (!measureFilter(measure))
            continue
        if (bottom1Index == null) {
            bottom1Index = i
            bottom1 = measure
        } else if (bottom2Index == null || measure < bottom2) {
            if (measure < bottom1) {
                bottom2Index = bottom1Index
                bottom2 = bottom1
                bottom1Index = i
                bottom1 = measure
            } else {
                bottom2Index = i
                bottom2 = measure
            }
        }
    }
    return when {
        bottom1Index == null -> emptyList()
        bottom2Index == null -> listOf(bottom1Index)
        else -> listOf(bottom1Index, bottom2Index)
    }
}

inline fun <reified T> List<T>.indicesSortedBy(
    indices: List<Int> = this.indices.toList(),
    crossinline measurer: (element: T) -> Double,
    crossinline condition: (index: Int, measure: Double) -> Boolean = { _, _ -> true },
    crossinline sortingPriority: (index: Int, measure: Double) -> Double = { _, m -> m },
): List<Int> = indices
    .asSequence()
    .map { index -> index to measurer(this[index]) }
    .filter { (index, m) -> condition(index, m) }
    .sortedBy { (index, m) -> sortingPriority(index, m) }
    .map { (index, _) -> index }
    .toList()
