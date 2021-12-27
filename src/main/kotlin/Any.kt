import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

val Any?.recursivelyFlatMapped get(): Any? = this?.let { Any(this) }

fun Any(Any: Any) = Any.recursivelyFlatMapped ?: Any

fun <T> Any?.cast(): T =
    (recursivelyFlatMapped as? T) ?: throw ClassCastException("Casting $recursivelyFlatMapped failed")

class any : ReadWriteProperty<Any?, Any?> {
    internal var internal: Any? = null

    override fun toString(): String = internal.toString()

    override operator fun getValue(thisRef: Any?, property: KProperty<*>) = this
    override operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Any?) {
        internal = value
    }
}

operator fun Any?.invoke() = (this as? any)?.internal
fun Any?.setRoot(any: Any?) {
    (this as? any)?.internal = any
}

//Invokes
operator fun Any?.invoke(key: String) = (this() as? Map<String, Any>)?.get(key)

operator fun Any?.invoke(key: String? = null, to: Any) {
    key?.let {
        val map = (this as? MutableMap<String, Any>) ?: mutableMapOf()
        map.put(key, to)
        this.setRoot(if (map.isEmpty()) null else map)
    } ?: this.setRoot(to)
}

//Operator

operator fun Any?.get(vararg route: Location): Any? = get(route.toList())
operator fun Any?.set(vararg route: Location, newValue: Any?) = set(route.toList(), newValue)

//TODO these needs sorting out given we have no KeyPaths
operator fun Any?.get(route: Route): Any? =
    when (route.size) {
        1 -> get(route.first())
        else -> get(route.drop(1))
    }

//set route with value
//get that route

operator fun Any?.set(route: Route, newValue: Any?) {
    get((route as List).dropLast(1))[route.last()] = newValue
}

operator fun Any?.get(fork: Location): Any? =
    when (fork) {
        is Location.Key -> this[fork.key]
        is Location.Index -> this[fork.index]
    }

operator fun Any?.set(fork: Location, newValue: Any?) =
    when (fork) {
        is Location.Key -> this[fork.key] = newValue
        is Location.Index -> this[fork.index] = newValue
    }

operator fun Any?.get(key: String): Any? = (this() as? Map<String, Any>)?.get(key)
operator fun Any?.set(key: String, newValue: Any?) {
    val map = (this() as? MutableMap<String, Any?>) ?: mutableMapOf()
    map.put(key, newValue)
    setRoot(if (map.isEmpty()) null else map)
}

operator fun Any?.get(index: Int): Any? =
    (this() as? Collection<*>)?.elementAtOrNull(index)

operator fun Any?.set(index: Int, newValue: Any?) {
// Unsure whats going on here
//    guard 0... ~= index else {
//        return
//    }

    val collection = (this() as? MutableList<Any?>) ?: mutableListOf()

    repeat(maxOf(0, index - collection.lastIndex + 1)) {
        collection.add(null)
    }
    collection[index] = newValue
//        collection.last()?.let {
//            val lastIndex = collection.dropLast(1).indexOfLast { it != null }
//        }

    setRoot(collection)
}