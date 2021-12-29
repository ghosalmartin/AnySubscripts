import kotlin.reflect.KProperty

val Any?.recursivelyFlatMapped get(): Any? = (this as any).invoke()?.let { Any(it) }

fun Any(Any: Any) = Any.recursivelyFlatMapped ?: Any

fun <T> Any?.cast(): T =
    (recursivelyFlatMapped as? T) ?: throw ClassCastException("Casting $recursivelyFlatMapped failed")

data class any(private var internal: Any? = null) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): any = this
    operator fun setValue(thisRef: Any?, property: KProperty<*>?, value: Any?) {
        internal = when (value) {
            null -> null
            is any -> value
            else -> {
                any().apply { internal = value }
            }
        }
    }

    fun invoke(): Any? {
        return (internal as? any)() ?: internal
    }
}

operator fun Any?.invoke(): Any? = (this as? any)?.invoke()
fun Any?.setRoot(any: Any?): Any? = apply { (this as? any)?.setValue(this, null, any) }

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
    //This entire set is a massive mess
    val parent = delegateGet((route as List).dropLast(1))
    parent[route.last()] = newValue
    if ((parent as? any)() == null) {
        val grandParent = delegateGet(route.dropLast(2))
        grandParent[route[route.size - 1]] = null
    }
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
    ((this() ?: this) as? Map<String, Any>)?.getOrDefault(key, null).run { this() ?: this }

operator fun Any?.set(key: String, newValue: Any?) {
    val map = ((this() ?: this) as? MutableMap<String, Any?>) ?: mutableMapOf()
    val delegate = any().apply {
        setValue(this, null, newValue)
    }
    map.put(key, delegate)
    setRoot(if (map.isEmpty()) null else map)
}

operator fun Any?.get(index: Int): Any? =
    ((this() ?: this) as? Collection<Any>)?.elementAtOrNull(index)?.run { this() ?: this }

operator fun Any?.set(index: Int, newValue: Any?) {
// Unsure whats going on here
//    guard 0... ~= index else {
//        return
//    }

    val collection = (this() as? MutableList<Any?>) ?: mutableListOf()

    repeat(maxOf(0, index - collection.size + 1)) {
        collection.add(null)
    }
    val delegate = if (newValue == null) {
        null
    } else {
        any().apply {
            setValue(this, null, newValue)
        }
    }
    collection[index] = delegate

    if (collection[index] == null) {
        val i: Int = collection.dropLast(1).indexOfLast { it != null } + 1
        if (i <= 0) {
            setRoot(null)
            return
        }

        setRoot(collection.subList(0, i))
    } else {
        setRoot(collection)
    }
}

//Returns delegates and builds path out if no element, this seems to be faster than building paths out upfront
internal fun Any?.delegateGet(index: Int): Any? {
    val root = (this() ?: this) as? Collection<Any>
    return if (root?.indices?.contains(index) == true) {
        root.elementAtOrNull(index)
    } else {
        val thatLevel = any()
        this[index] = thatLevel
        thatLevel
    }
}

internal fun Any?.delegateGet(fork: Location): Any? =
    when (fork) {
        is Location.Key -> this.delegateGet(fork.key)
        is Location.Index -> this.delegateGet(fork.index)
    }

internal fun Any?.delegateGet(key: String): Any? {
    val root = (this() ?: this) as? Map<String, Any>
    return if (root?.containsKey(key) == true) {
        root[key]
    } else {
        val thatLevel = any()
        this[key] = thatLevel
        thatLevel
    }
}

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