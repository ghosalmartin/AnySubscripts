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

    fun invoke(): Any? = (internal as? any)() ?: internal
}

operator fun Any?.invoke(): Any? = (this as? any)?.invoke()
fun Any?.setThis(any: Any?): Any? = apply { (this as? any)?.setValue(this, null, any) }

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
    accumulateRoute(route) { item, location ->
        item[location]
    }

operator fun Any?.set(route: Route, newValue: Any?) {
    // This entire set is a massive mess
    val parent = getOrCreate((route as List).dropLast(1))
    parent[route.last()] = newValue
    if ((parent as? any)() == null) {
        val grandParent = get(route.dropLast(2))
        grandParent[route[route.lastIndex - 1]] = null
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
    ((this() ?: this) as? Map<String, Any>)?.get(key).run {
        (this() ?: this).run {
            when (this) {
                is List<*> -> map { it() }
                else -> this
            }
        }
    }
operator fun Any?.set(key: String, newValue: Any?) {
    val map = (this() as? MutableMap<String, Any>) ?: mutableMapOf()
    val delegate = newValue.anyOrNull

    if (delegate != null) {
        map.put(key, delegate)
    } else {
        map.remove(key)
    }

    setThis(if (map.isEmpty()) null else map)
}

operator fun Any?.get(index: Int): Any? =
    ((this() ?: this) as? Collection<Any>)?.elementAtOrNull(index)?.run { this() ?: this }

operator fun Any?.set(index: Int, newValue: Any?) {
    val collection = (this() as? MutableList<Any?>) ?: mutableListOf()

    repeat(maxOf(0, index - collection.size + 1)) {
        collection.add(null)
    }
    collection[index] = newValue.anyOrNull

    if (collection[index] == null) {
        val i: Int = collection.dropLast(1).indexOfLast { it != null } + 1
        if (i <= 0) {
            setThis(null)
            return
        }

        setThis(collection.subList(0, i))
    } else {
        setThis(collection)
    }
}

private fun Any?.getOrCreate(index: Int): Any? {
    val root = this() as? Collection<Any>
    return if (root?.indices?.contains(index) == true) {
        root.elementAtOrNull(index)
    } else {
        val thatLevel = any()
        this[index] = thatLevel
        thatLevel
    }
}

private fun Any?.getOrCreate(fork: Location): Any? =
    when (fork) {
        is Location.Key -> getOrCreate(fork.key)
        is Location.Index -> getOrCreate(fork.index)
    }

private fun Any?.getOrCreate(key: String): Any? {
    val root = this() as? Map<String, Any>
    return if (root?.containsKey(key) == true) {
        root[key]
    } else {
        val thatLevel = any()
        this[key] = thatLevel
        thatLevel
    }
}

private fun Any?.getOrCreate(route: Route): Any? =
    accumulateRoute(route) { item, location ->
        item.getOrCreate(location)
    }

private fun Any?.accumulateRoute(route: Route, get: (Any?, Location) -> Any?) =
    if (route.isEmpty()) {
        this
    } else {
        route.fold(null as Any?) { accumulator, location ->
            get(accumulator ?: this, location)
        }
    }

private val Any?.anyOrNull: Any?
    get() =
        this?.run {
            any().apply {
                setValue(this, null, this@run)
            }
        }
