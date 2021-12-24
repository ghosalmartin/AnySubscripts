package utils

import Location
import Route
import kotlin.random.Random

class RandomRoutes(
    private val keys: List<String>,
    private val indices: List<Int>,
    private val length: IntRange,
    private val keyBias: Float = 0.5f,
    private val seed: Int = 0
) {

    fun generate(count: Int): List<Route> =
        Random(seed = seed).run {
            (0 until maxOf(0, count))
                .map { generate(this) }
        }

    private fun generate(random: Random): Route {
        val lower = maxOf(0, length.first)
        val upper = maxOf(lower, length.last)
        val count = random.nextInt(from = 0, until = upper - lower + 1) + lower
        return (0 until count).mapNotNull {
            if (random.nextFloat() < keyBias) {
                random.randomElement(keys)?.run { Location.Key(this) }
            } else {
                random.randomElement(indices)?.run { Location.Index(it) }
            }
        }
    }

    private inline fun <reified T> Random.randomElement(collection: List<T>): T? {
        if (collection.isEmpty()) return null
        return collection[nextInt(until = collection.size)]
    }
}
