
interface Optional {
    var internal: Any?
}

val Any?.recursivelyFlatMapped get(): Any? = this?.let { Any(this) }

fun Any(any: Any) = any.recursivelyFlatMapped ?: any

fun <T> Any?.cast(): T = (recursivelyFlatMapped as? T) ?: throw ClassCastException("Casting $recursivelyFlatMapped failed")

operator fun Any?.get(vararg route: Location): Any? = get(route.toList())
operator fun Any?.set(vararg route: Location, newValue: Any?): Any? = set(route.toList(), newValue)

//TODO these needs sorting out given we have no KeyPaths
operator fun Any?.get(route: Route): Any? = null
operator fun Any?.set(route: Route, newPath: Any?): Any? = null

operator fun Any?.get(fork: Location): Any? =
    when(fork){
        is Location.Key -> this[fork.key]
        is Location.Index -> this[fork.index]
    }

operator fun Any?.set(fork: Location, newValue: Any): Any =
    when(fork){
        is Location.Key -> this[fork.key] = newValue
        is Location.Index -> this[fork.index] = newValue
    }

fun Any?.get(): Any? = (this as? Map<String, Any>)
operator fun Any?.get(key: String): Any? = (this as? Map<String, Any>)?.get(key)
operator fun Any?.set(key: String, newValue: Any): Any? {
    val map = (this as? MutableMap<String, Any>) ?: mutableMapOf()
    map.put(key, newValue)
    return if (map.isEmpty()) null else map
}

operator fun Any?.get(index: Int): Any? =
    (this as? Collection<*>)?.elementAtOrNull(index)

operator fun Any?.set(index: Int, newValue: Any): Any {
// Unsure whats going on here
//    guard 0... ~= index else {
//        return
//    }

    val collection = (this as? MutableList<Any?>) ?: mutableListOf()

    repeat(maxOf(0, index - collection.lastIndex + 1)) {
        collection.add(null)
    }
    collection[index] = newValue
    collection.lastOrNull()?.let {
//        collection.dropLast(1).last
    }

    return collection
}