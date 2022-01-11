val <T> Collection<T>.unlessEmpty: Collection<T>?
    get() = ifEmpty { null }

val <T> Collection<T>.lineage: List<List<T>>
    get() =
        toList()
            .dropLast(1)
            .foldIndexed<T, MutableList<List<T>>>(mutableListOf()) { index, acc, item ->
                acc.apply { add(if (index > 0) acc[index - 1] + item else listOf(item)) }
            }.reversed()

val <T> Collection<T>.dropFirst: Collection<T>
    get() = drop(1)
