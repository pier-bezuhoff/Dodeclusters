package domain

/** `this[key] = upd(this[key] ?: default)` */
inline fun <K, V> MutableMap<K, V>.update(
    key: K,
    default: V,
    crossinline upd: (V) -> V,
) {
    val newValue = get(key) ?: default
    put(key, upd(newValue))
}

inline fun <K, V : Any> MutableMap<K, V>.setOrRemove(key: K, value: V?) {
    if (value == null)
        remove(key)
    else
        put(key, value)
}