import any.get
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import utils.RandomRoutes
import utils.not
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

internal class StoreTest {

    @Test
    fun stream() {
        assertThrows<CancellationException> {
            runBlocking {
                val store = Store()
                val route = !listOf("way", "to", "my", "heart")

                store.set(route, "?")

                store.stream(route)
                    .collectLatest {
                        when (it) {
                            "?" -> store.set(route, "❤️")
                            "❤️" -> store.set(route, "💛")
                            "💛" -> store.set(route, "💚")
                            "💚" -> cancel("Never Be Game Over!")
                            else -> throw IllegalArgumentException("What else is in there? $it")
                        }
                    }
            }
        }
    }

    @Test
    fun `update upstream`() {
        assertThrows<CancellationException> {
            runBlocking {
                val store = Store()

                val listOfKeys = listOf("a", 2, "c")
                val route = !listOfKeys
                val routeMinusLast = !listOfKeys.dropLast(1)

                store.set(route, "?")

                store.stream(route)
                    .collectLatest {
                        when (it) {
                            "?" -> store.set(routeMinusLast, mutableMapOf("c" to "❤️"))
                            "❤️" -> store.set(routeMinusLast, mutableMapOf("c" to "💛"))
                            "💛" -> store.set(routeMinusLast, mutableMapOf("c" to "💚"))
                            "💚" -> cancel("Never Be Game Over!")
                            else -> throw IllegalArgumentException("What else is in there? $it")
                        }
                    }
            }
        }
    }

    @Test
    fun `update downstream`() {
        assertThrows<CancellationException> {
            runBlocking {
                val store = Store()

                val listOfKeys = listOf("a", 2, "c")
                val route = !listOfKeys
                val routeMinusLast = !listOfKeys.dropLast(1)

                store.set(route, "?")

                store.stream(routeMinusLast)
                    .map { mapOf(listOfKeys.last() to it["c"]) }
                    .collectLatest {
                        when (it) {
                            mapOf("c" to "?") -> store.set(route, "❤️")
                            mapOf("c" to "❤️") -> store.set(route, "💛")
                            mapOf("c" to "💛") -> store.set(route, "💚")
                            mapOf("c" to "💚") -> cancel("Never Be Game Over!")
                            else -> throw IllegalArgumentException("What else is in there? $it")
                        }
                    }
            }
        }
    }

    @Test
    fun batch() = runBlocking {
        val routes = RandomRoutes(
            keys = "abc".map { it.toString() },
            indices = listOf(1, 2),
            keyBias = 0.8f,
            length = 3..12,
            seed = 4
        ).generate(10000)

        val o = Store()
        val o2 = Store()

        val route = !listOf("b", "b")

        var countO = 0
        var countO2 = 0

        var result: Any? = null

        val latch = CountDownLatch(1)

        val job1 = launch(Dispatchers.IO) {
            o.stream(route)
                .collectLatest {
                    countO++
                }
        }

        val job2 = launch(Dispatchers.IO) {
            o2.stream(route)
                .collectLatest {
                    countO2++
                    result = it
                    it?.let {
                        latch.countDown()
                    }
                }
        }

        val updates: BatchUpdates = routes.map {
            o.set(it, "✅")
            it to "✅"
        }

        o2.batch(updates)

        latch.await()

        val original = o.get()
        val copy = o2.get()

        assertEquals(original, copy)

        val originalRouted = o.get(route)
        val copyRouted = result

        assertEquals(originalRouted, copyRouted)

        assertEquals(countO, 723)
        assertEquals(countO2, 2)

        job1.cancelAndJoin()
        job2.cancelAndJoin()
    }
}