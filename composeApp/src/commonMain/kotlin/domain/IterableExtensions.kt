package domain

fun <T> Iterable<T>.filterIndices(
    predicate: (T) -> Boolean
): List<Int> =
    this.withIndex()
        .filter { (_, t) -> predicate(t) }
        .map { (i, _) -> i }

fun <T> Iterable<T>.partitionIndices(
    predicate: (T) -> Boolean
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
        for (j in (i + 1) until r) // diagonalization for non-repeating combinations
            indices[j] = indices[j-1] + 1
        yield(indices.toList())
    }
}

fun <T> Iterable<T>.updated(index: Int, newElement: T): List<T> =
    this.toMutableList().apply { this[index] = newElement }

fun reindexingMap(originalIndices: IntRange, deletedIndices: Set<Ix>): Map<Ix, Ix> {
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
