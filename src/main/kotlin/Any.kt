import kotlin.reflect.KProperty

val Any?.recursivelyFlatMapped get(): Any? = (this as any).invoke()?.let { Any(it) }

fun Any(Any: Any) = Any.recursivelyFlatMapped ?: Any

fun <T> Any?.cast(): T =
    (recursivelyFlatMapped as? T) ?: throw ClassCastException("Casting $recursivelyFlatMapped failed")

class any {
    private var internal: Any? = null

    override fun toString(): String = internal.toString()

    operator fun getValue(thisRef: Any?, property: KProperty<*>) = this
    operator fun setValue(thisRef: Any?, property: KProperty<*>?, value: Any?) {
        internal = if (value is any) {
            value
        } else {
            any().apply { internal = value }
        }
    }

    fun invoke(): Any? = (internal as? any)() ?: internal
}

operator fun Any?.invoke(): Any? = (this as? any)?.invoke()
fun Any?.setRoot(any: Any?) {
    (this as? any)?.setValue(null, null, any)
}

//Performance of the set/get below are questionable
operator fun Any?.set(vararg any: Any, newValue: Any?) =
    set(
        route = any.toList().map {
            when (it) {
                is String -> Location(it)
                is Int -> Location(it)
                else -> throw IllegalArgumentException("Only strings and ints are supported as keys")
            }
        },
        newValue = newValue
    )
operator fun Any?.get(vararg any: Any) =
    get(
        route = any.toList().map {
            when (it) {
                is String -> Location(it)
                is Int -> Location(it)
                else -> throw IllegalArgumentException("Only strings and ints are supported as keys")
            }
        }
    )

operator fun Any?.get(vararg route: Location): Any? = get(route = route.toList())
operator fun Any?.set(vararg route: Location, newValue: Any?) = set(route = route.toList(), newValue = newValue)

operator fun Any?.get(route: Route): Any? =
    when (route.size) {
        0 -> this
        1 -> get(route.first())
        2 -> get(route.elementAt(0))[route.elementAt(1)]
        3 -> get(route.elementAt(0))[route.elementAt(1)][route.elementAt(2)]
        4 -> get(route.elementAt(0))[route.elementAt(1)][route.elementAt(2)][route.elementAt(3)]
        5 -> get(route.elementAt(0))[route.elementAt(1)][route.elementAt(2)][route.elementAt(3)][route.elementAt(4)]
        6 -> get(route.elementAt(0))[route.elementAt(1)][route.elementAt(2)][route.elementAt(3)][route.elementAt(4)][route.elementAt(5)]
        7 -> get(route.elementAt(0))[route.elementAt(1)][route.elementAt(2)][route.elementAt(3)][route.elementAt(4)][route.elementAt(5)][route.elementAt(6)]
        8 -> get(route.elementAt(0))[route.elementAt(1)][route.elementAt(2)][route.elementAt(3)][route.elementAt(4)][route.elementAt(5)][route.elementAt(6)][route.elementAt(7)]
        9 -> get(route.elementAt(0))[route.elementAt(1)][route.elementAt(2)][route.elementAt(3)][route.elementAt(4)][route.elementAt(5)][route.elementAt(6)][route.elementAt(7)][route.elementAt(8)]
        else -> get(route.elementAt(0))[route.elementAt(1)][route.elementAt(2)][route.elementAt(3)][route.elementAt(4)][route.elementAt(5)][route.elementAt(6)][route.elementAt(7)][route.elementAt(8)][route.drop(9)]
    }

operator fun Any?.set(route: Route, newValue: Any?) {
    delegateGet((route as List).dropLast(1))[route.last()] = newValue
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

operator fun Any?.get(key: String): Any? =
    ((this as? Map<String, Any>) ?: (this() as? Map<String, Any>))?.getOrDefault(key, null).run { this() ?: this }

operator fun Any?.set(key: String, newValue: Any?) {
    val map = (this() as? MutableMap<String, Any?>) ?: mutableMapOf()
    val delegate = any().apply {
        setValue(null, null, newValue)
    }
    map.put(key, delegate)
    setRoot(if (map.isEmpty()) null else map)
}

operator fun Any?.get(index: Int): Any? =
    ((this as? Collection<*>) ?: (this() as? Collection<*>))?.elementAtOrNull(index)?.run { this() ?: this }

//TODO port the rest

//set {
//    guard 0... ~= index else {
//        return
//    }
//    var o = self as? [Any?] ?? []
//    o.append(contentsOf: repeatElement(nil, count: max(0, index - o.endIndex + 1)))
//    o[index] = newValue
//    if o.last ?? nil == nil {
//        guard let i = o.dropLast().lastIndex(where: { $0 != nil }).map({ $0 + 1 }) else {
//        self = nil
//        return
//    }
//        o.removeSubrange(i...)
//    }
//    self = o
//}
//

operator fun Any?.set(index: Int, newValue: Any?) {
// Unsure whats going on here
//    guard 0... ~= index else {
//        return
//    }

    val collection = (this() as? MutableList<Any?>) ?: mutableListOf()

    repeat(maxOf(0, index - collection.lastIndex + 1)) {
        collection.add(null)
    }
    val delegate = any().apply {
        setValue(null, null, newValue)
    }
    collection[index] = delegate
//        collection.last()?.let {
//            val lastIndex = collection.dropLast(1).indexOfLast { it != null }
//        }

    setRoot(collection)
}

//Returns delegates and not values
internal fun Any?.delegateGet(index: Int): Any? =
    (this() as? Collection<*>)?.elementAtOrNull(index)

internal fun Any?.delegateGet(fork: Location): Any? =
    when (fork) {
        is Location.Key -> this.delegateGet(fork.key)
        is Location.Index -> this.delegateGet(fork.index)
    }

internal fun Any?.delegateGet(key: String): Any? = (this() as? Map<String, Any>)?.get(key)

internal fun Any?.delegateGet(route: Route): Any? =
    when (route.size) {
        0 -> this
        1 -> delegateGet(route.first())
        2 -> delegateGet(route.elementAt(0)).delegateGet(route.elementAt(1))
        3 -> delegateGet(route.elementAt(0)).delegateGet(route.elementAt(1)).delegateGet(route.elementAt(2))
        4 -> delegateGet(route.elementAt(0)).delegateGet(route.elementAt(1)).delegateGet(route.elementAt(2)).delegateGet(route.elementAt(3))
        5 -> delegateGet(route.elementAt(0)).delegateGet(route.elementAt(1)).delegateGet(route.elementAt(2)).delegateGet(route.elementAt(3)).delegateGet(route.elementAt(4))
        6 -> delegateGet(route.elementAt(0)).delegateGet(route.elementAt(1)).delegateGet(route.elementAt(2)).delegateGet(route.elementAt(3)).delegateGet(route.elementAt(4)).delegateGet(route.elementAt(5))
        7 -> delegateGet(route.elementAt(0)).delegateGet(route.elementAt(1)).delegateGet(route.elementAt(2)).delegateGet(route.elementAt(3)).delegateGet(route.elementAt(4)).delegateGet(route.elementAt(5)).delegateGet(route.elementAt(6))
        8 -> delegateGet(route.elementAt(0)).delegateGet(route.elementAt(1)).delegateGet(route.elementAt(2)).delegateGet(route.elementAt(3)).delegateGet(route.elementAt(4)).delegateGet(route.elementAt(5)).delegateGet(route.elementAt(6)).delegateGet(route.elementAt(7))
        9 -> delegateGet(route.elementAt(0)).delegateGet(route.elementAt(1)).delegateGet(route.elementAt(2)).delegateGet(route.elementAt(3)).delegateGet(route.elementAt(4)).delegateGet(route.elementAt(5)).delegateGet(route.elementAt(6)).delegateGet(route.elementAt(7)).delegateGet(route.elementAt(8))
        else -> delegateGet(route.elementAt(0)).delegateGet(route.elementAt(1)).delegateGet(route.elementAt(2)).delegateGet(route.elementAt(3)).delegateGet(route.elementAt(4)).delegateGet(route.elementAt(5)).delegateGet(route.elementAt(6)).delegateGet(route.elementAt(7)).delegateGet(route.elementAt(8)).delegateGet(route.drop(9))
    }