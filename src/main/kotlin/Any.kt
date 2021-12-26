import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

val Any?.recursivelyFlatMapped get(): Any? = this?.let { Any(this) }

fun Any(Any: Any) = Any.recursivelyFlatMapped ?: Any

fun <T> Any?.cast(): T =
    (recursivelyFlatMapped as? T) ?: throw ClassCastException("Casting $recursivelyFlatMapped failed")

//operator fun Any?.get(vararg route: Location): Any? = get(route.toList())
//operator fun Any?.set(vararg route: Location, newValue: Any?): Any? = set(route.toList(), newValue)
//
////TODO these needs sorting out given we have no KeyPaths
//operator fun Any?.get(route: Route): Any? = null
//operator fun Any?.set(route: Route, newPath: Any?): Any? = null
//
//operator fun Any?.get(fork: Location): Any? =
//    when (fork) {
//        is Location.Key -> this[fork.key]
//        is Location.Index -> this[fork.index]
//    }
//
//operator fun Any?.set(fork: Location, newValue: Any): Any =
//    when (fork) {
//        is Location.Key -> this[fork.key] = newValue
//        is Location.Index -> this[fork.index] = newValue
//    }
//
//operator fun Any?.get(key: String): Any? = (this as? Map<String, Any>)?.get(key)
//operator fun Any?.set(key: String, newValue: Any) {
//    val map = (this as? MutableMap<String, Any>) ?: mutableMapOf()
//    map[key] = newValue
//    this = if (map.isEmpty()) null else map
//}
//
//operator fun Any?.get(index: Int): Any? =
//    (this as? Collection<*>)?.elementAtOrNull(index)
//
//operator fun Any?.set(index: Int, newValue: Any): Any {
//// Unsure whats going on here
////    guard 0... ~= index else {
////        return
////    }
//
//    val collection = (this as? MutableList<Any?>) ?: mutableListOf()
//
//    repeat(maxOf(0, index - collection.lastIndex + 1)) {
//        collection.add(null)
//    }
//    collection[index] = newValue
//    collection.lastOrNull()?.let {
////        collection.dropLast(1).last
//    }
//
//    return collection
//}

class any : ReadWriteProperty<Any?, Any?> {
    var internal: Any? = null

    override operator fun getValue(thisRef: Any?, property: KProperty<*>) = this
    override operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Any?) {
        internal = value
    }

    operator fun get(vararg route: Location): Any? = get(route.toList())
    operator fun set(vararg route: Location, newValue: Any?): Any? = set(route.toList(), newValue)

    //TODO these needs sorting out given we have no KeyPaths
    operator fun get(route: Route): Any? = null
    operator fun set(route: Route, newPath: Any?): Any? = null

    operator fun get(fork: Location): Any? =
        when (fork) {
            is Location.Key -> this[fork.key]
            is Location.Index -> this[fork.index]
        }

    operator fun set(fork: Location, newValue: Any): Any =
        when (fork) {
            is Location.Key -> this[fork.key] = newValue
            is Location.Index -> this[fork.index] = newValue
        }

    operator fun get(key: String): Any? = (internal as? Map<String, Any>)?.get(key)
    operator fun set(key: String, newValue: Any) {
        val map = (internal as? MutableMap<String, Any>) ?: mutableMapOf()
        map[key] = newValue
        internal = if (map.isEmpty()) null else map
    }

    operator fun get(index: Int): Any? =
        (internal as? Collection<*>)?.elementAtOrNull(index)

    operator fun set(index: Int, newValue: Any): Any {
// Unsure whats going on here
//    guard 0... ~= index else {
//        return
//    }

        val collection = (internal as? MutableList<Any?>) ?: mutableListOf()

        repeat(maxOf(0, index - collection.lastIndex + 1)) {
            collection.add(null)
        }
        collection[index] = newValue
        collection.lastOrNull()?.let {
//        collection.dropLast(1).last
        }

        return collection
    }
}

operator fun Any?.invoke() = (this as? any)?.internal
operator fun Any?.invoke(any: Any?) {
    (this as? any)?.internal = any
}

operator fun Any?.get(key: String): Any? = (this() as? Map<String, Any>)?.get(key)
operator fun Any?.set(key: String, newValue: Any) {
    val map = (this as? MutableMap<String, Any>) ?: mutableMapOf()
    map.put(key, newValue)
    this(if (map.isEmpty()) null else map)
}