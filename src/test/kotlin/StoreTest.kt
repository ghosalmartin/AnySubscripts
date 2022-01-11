import any.get
import any.invoke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import utils.RandomRoutes
import utils.not
import java.util.concurrent.CountDownLatch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

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
        val jobs = mutableListOf<Job>()

        jobs += launch(Dispatchers.Default) {
            o.stream(route)
                .collectLatest {
                    countO += 1
                }
        }

        jobs += launch(Dispatchers.Default) {
            o2.stream(route)
                .collectLatest {
                    countO2 += 1
                    result = it
                    it?.let {
                        latch.countDown()
                    }
                }
        }

        val expectedNumberOfRoutes = routes
            .filter {
                it.elementAtOrNull(0)?.stringValue == "b" &&
                    it.elementAtOrNull(1)?.stringValue == "b"
            }

        val updates: BatchUpdates = routes.map {
            o.set(it, "✅")
            it to "✅"
        }.toMutableList()

        o2.batch(updates)

        latch.await()
        jobs.map { it.cancel() }

        val original = o.get()
        val copy = o2.get()

        assertEquals(original, copy)

        val originalRouted = o.get(route)
        val copyRouted = result

        assertEquals(originalRouted, copyRouted)

        assertEquals(2, countO2)
        assertEquals(expectedNumberOfRoutes.size, countO - 1)
    }

    @Test
    fun transactions() = runBlocking {
        val o = Store()
        val latch = CountDownLatch(1)

        val routeToX = listOf(!"x")
        val routeToY = listOf(!"y")
        val routeToZ = listOf(!"z")

        val job = launch(Dispatchers.Default) {
            o.stream(routeToX).collectIndexed { index, value ->
                when (index) {
                    0 -> assertNull(value)
                    1 -> assertEquals(3, value).also { latch.countDown() }
                    else -> fail()
                }
            }
        }

        (1..2).apply {
        }
        o.transaction {
            set(routeToX, 1)
            set(routeToY, 1)

            transaction {
                set(routeToX, 2)
                set(routeToY, 2)

                try {
                    transaction {
                        set(routeToZ, 3)
                        throw IllegalStateException("Someones feelings have to get hurt")
                    }
                } catch (exception: IllegalStateException) {
                }
                transaction {
                    set(routeToX, 3)
                    set(routeToY, 3)
                }
            }
        }

        latch.await()
        job.cancelAndJoin()

        val data = o.get()

        assertEquals(mapOf("x" to 3, "y" to 3), data())
    }

    @Test
    fun `transaction level`() = runBlocking {
        val o = Store()
        o.transaction {
            assertEquals(1, transactionalLevel())

            transaction {
                assertEquals(2, transactionalLevel())

                try {
                    transaction {
                        assertEquals(3, transactionalLevel())
                        throw IllegalStateException("Someones feelings have to get hurt")
                    }
                } catch (exception: Exception) {
                }

                transaction {
                    assertEquals(3, transactionalLevel())
                }
            }
        }
    }

    @Test
    fun `1000 subscriptions`() = runBlocking {
        val routes = RandomRoutes(
            keys = "abcde".map { it.toString() },
            indices = listOf(1, 2, 3),
            keyBias = 0.8f,
            length = 5..20,
            seed = 4
        ).generate(1000)

        val o = Store()
        val o2 = Store()

        val jobs = mutableListOf<Job>()
        val latch = CountDownLatch(1)
        var count = 0

        routes.forEach { route ->
            jobs += launch(Dispatchers.Default) {
                o.stream(route)
                    .collectLatest {
                        o2.set(route, it)
                        count += 1
                        if (count == routes.size) {
                            latch.countDown()
                        }
                    }
            }
        }

        routes.forEach { route ->
            o.set(route, "✅")
        }

        latch.await()

        jobs.map { it.cancelAndJoin() }

        val original = o.get()
        val copy = o2.get()

        assertEquals(original, copy)
    }

    @Test
    fun `thread safety`() = runBlocking {
        val f: (String) -> List<Route> = { alphabet ->
            RandomRoutes(
                keys = alphabet.map { it.toString() },
                indices = listOf(),
                keyBias = 1f,
                length = 2..7,
                seed = 7
            ).generate(1000)
        }

        val high = f("AB")
        val low = f("ab")

        val o = Store()

        val results = mutableListOf<Any?>()

        val jobs = mutableListOf<Job>()

        (0..10).forEach {
            val latch = CountDownLatch(2)

            jobs += launch(Dispatchers.Default) {
                high.forEach { o.set(it, "✅") }
                latch.countDown()
            }

            jobs += launch(Dispatchers.Default) {
                low.forEach { o.set(it, "✅") }
                latch.countDown()
            }

            latch.await()

            results += o.get()
        }

        jobs.map { it.cancelAndJoin() }
        assertTrue { results.dropFirst.all { it == results.first() } }
    }
}
