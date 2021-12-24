
val Any?.recursivelyFlatMapped get(): Any? = this?.let { Any(this) }

fun Any(any: Any) = any.recursivelyFlatMapped ?: any


// MARK Optional
operator fun Any.get(key: String): Any? = (this as? Map<String, Any>)?.get(key)
operator fun Any.set(key: String, newValue: Any): Any? {
    val map = (this as? MutableMap<String, Any>) ?: mutableMapOf()
    map[key] = newValue
    return null
}