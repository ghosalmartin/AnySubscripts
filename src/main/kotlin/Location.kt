sealed interface Location : Comparable<Location> {
    data class Index(val index: Int) : Location {
        override fun toString(): String = index.toString()
    }

    data class Key(val key: String) : Location {
        override fun toString(): String = key
    }

    override fun compareTo(other: Location): Int =
        when {
            this is Index && other is Key -> -1
            this is Key && other is Index -> 1
            this is Index && other is Index -> index.compareTo(other.index)
            this is Key && other is Key -> key.compareTo(other.key)
            else -> 0
        }
}

typealias Route = Collection<Location>

val Location.key: String?
    get() = when (this) {
        is Location.Index -> null
        is Location.Key -> key
    }

val Location.index: Int?
    get() = when (this) {
        is Location.Index -> index
        is Location.Key -> null
    }

val Location.description: String
    get() = when (this) {
        is Location.Key -> key
        is Location.Index -> "$index"
    }

val Location.stringValue: String get() = description
val Location.intValue: Int? get() = index

fun Location(stringValue: String): Location = Location.Key(stringValue)
fun Location(intValue: Int): Location = Location.Index(intValue)

operator fun Collection<Location>.compareTo(rhs: Collection<Location>): Int =
    zip(rhs)
        .firstOrNull { it.first != it.second }
        ?.let {
            it.first.compareTo(it.second)
        } ?: size.compareTo(rhs.size)

// fun Sequence<Any?>.joined(separator: String = ".") =
//    asSequence().map { it.description }
