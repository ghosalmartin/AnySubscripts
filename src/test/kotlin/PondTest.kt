import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import utils.Database
import utils.RandomRoutes
import utils.not
import java.util.concurrent.CountDownLatch
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PondTest {

    @Test
    fun versioning() = runBlocking {
        val store = Store()
        val db = Database(store = store)
        val gushSource = hashMapOf<String, Pond.GushSource>()
        val pond = Pond(source = db, gushSources = gushSource)

        val route = !listOf("v/2.0/way/to", "my", "heart")
        store.set(route, "🤍")

        var hearts = ""
        val latch = CountDownLatch(1)

        val job = launch(Dispatchers.Default) {
            pond.stream(!listOf("way", "to", "my", "heart"))
                .collectLatest { heart ->
                    hearts += heart ?: ""

                    when (heart) {
                        null, hearts.isEmpty() -> store.set(!listOf("v/1.0/way/to", "my", "heart"), "❤️")
                        "❤️" -> store.set(!listOf("v/1.0/way/to", "my", "heart"), "💛")
                        "💛" -> store.set(!listOf("v/1.0/way/to", "my", "heart"), "💚")
                        "💚" -> db.setVersion("v/2.0/")
                        "🤍" -> latch.countDown()
                        else -> throw IllegalStateException("Never Be Game Over!")
                    }
                }
        }

        latch.await()

        assertEquals("❤️💛💚🤍", hearts)
        delay(20) //TODO Remove this but how to know to wait for cleanup?
        assertNull(gushSource["v/1.0/way/to"]?.referenceCount)
        assertEquals(1, gushSource["v/2.0/way/to"]?.referenceCount)
        job.cancelAndJoin()
    }

    @Test
    fun `reference counting`() = runBlocking {
        val store = Store()
        val db = Database(store = store)
        val gushSource = hashMapOf<String, Pond.GushSource>()
        val pond = Pond(source = db, gushSources = gushSource)

        val redRoute = !listOf("way", "to", "red", "heart")
        val blueRoute = !listOf("way", "to", "blue", "heart")

        store.set(
            !"v/1.0/way/to",
            value = mapOf(
                "red" to mapOf("heart" to "❤️"),
                "blue" to mapOf("heart" to "💙")
            )
        )

        val latch = CountDownLatch(2)

        val red = launch(Dispatchers.Default) {
            pond.stream(redRoute).collectLatest { heart ->
                assertEquals("❤️", heart)
                latch.countDown()
            }
        }

        val blue = launch(Dispatchers.Default) {
            pond.stream(blueRoute).collectLatest { heart ->
                assertEquals("💙", heart)
                latch.countDown()
            }
        }

        latch.await()

        assertEquals(2, gushSource["v/1.0/way/to"]?.referenceCount)

        red.cancelAndJoin()
        yield()

        assertEquals(1, gushSource["v/1.0/way/to"]?.referenceCount)

        blue.cancelAndJoin()
        yield()

        assertNull(gushSource["v/1.0/way/to"]?.referenceCount)
    }

    @RepeatedTest(10)
    fun `live mapping update`() = runBlocking {
        val store = Store()
        val db = Database(store = store)
        val pond = Pond(source = db)

        val routes = RandomRoutes(
            keys = "abc".map { it.toString() },
            indices = listOf(1, 2, 3),
            keyBias = 1f,
            length = 4..9,
            seed = 7
        ).generate(1000)

        val latch = CountDownLatch(3)
        val versions = (1..3).map { it }

        val result = Result()

        val jobs = mutableListOf<Job>()

        routes.forEach { route ->
            jobs += launch(Dispatchers.Default) {
                pond.stream(route).collectLatest {
                    result.set(route, it as? String)
                }
            }
        }

        versions.forEach { version ->
            jobs += launch(Dispatchers.Default) {
                routes.forEach { route ->
                    val customRoute =
                        listOf(Location("v/$version.0/${route.take(2).joinToString("/")}")) + route.drop(2)
                    store.set(customRoute, "✅ v$version")
                }
                delay(100)
                latch.countDown()
            }
        }

        latch.await()

        routes.forEach { route ->
            val v1 = listOf(Location("v/1.0/${route.take(2).joinToString("/")}")) + route.drop(2)
            val v3 = listOf(Location("v/3.0/${route.take(2).joinToString("/")}")) + route.drop(2)

            val l = store.get(v1) == "✅ v1"
            val r = store.get(v3) == "✅ v3"
            assertEquals(l, r)
        }

        val v1 = result.values.toMap()
        assertTrue(v1.isNotEmpty())
        assertTrue(v1.map { it.value }.all { it == "✅ v1" })

        db.setVersion("v/3.0/")

        delay(100)
        jobs.forEach { it.cancelAndJoin() }

        val v3 = result.values.toMap()

        assertTrue(v3.isNotEmpty())
        assertTrue(v3.map { it.value }.all { it == "✅ v3" })

        assertEquals(v1.keys, v3.keys)
    }

    private class Result(
        val values: MutableMap<Route, String> = mutableMapOf()
    ) {

        private sealed interface Intent {
            data class Set(val route: Route, val value: String?, val completableDeferred: CompletableDeferred<Unit>)
        }

        private val scope = CoroutineScope(Dispatchers.Unconfined)

        private val actor = scope.actor<Intent.Set> {
            consumeEach { (route, value, deferred) ->
                if (value != null) {
                    values[route] = value
                } else {
                    values.remove(route)
                }
                deferred.complete(Unit)
            }
        }

        suspend fun set(route: Route, value: String?) {
            CompletableDeferred<Unit>().apply {
                actor.send(Intent.Set(route, value, this))
            }.await()
        }
    }
}