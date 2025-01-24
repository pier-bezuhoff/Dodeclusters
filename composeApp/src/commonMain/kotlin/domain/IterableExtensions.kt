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

// reference: https://docs.python.org/3/library/itertools.html#itertools.combinations
/** Produces indices for non-repeating combinations out
 * of [n] objects in total, [r] at time (in lexicographic order).
 *
 * `combinations(4, 3) → 012 013 023 123`
 * */
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
 * */
fun <T> Iterable<T>.sortedByFrequency(): List<T> =
    this.groupingBy { it }
        .eachCount()
        .entries
        .sortedByDescending { (_, count) -> count }
        .map { (t, _) -> t }

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

/**
 * Returns a list of values built from the elements of `this` collection,
 * the [other] and [another] collections with the same index
 * using the provided [transform] function applied to each triple of elements.
 * The returned list has length of the shortest collection.
 */
inline fun <A, B, C, R> Iterable<A>.zip3(
    other: Iterable<B>,
    another: Iterable<C>,
    crossinline transform: (a: A, b: B, c: C) -> R
): List<R> {
    val first = iterator()
    val second = other.iterator()
    val third = another.iterator()
    val list = ArrayList<R>(minOf(
        collectionSizeOrDefault(10),
        other.collectionSizeOrDefault(10),
        another.collectionSizeOrDefault(10),
    ))
    while (first.hasNext() && second.hasNext() && third.hasNext()) {
        list.add(transform(first.next(), second.next(), third.next()))
    }
    return list
}

