import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
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

@ExperimentalCoroutinesApi
class PondTest {

    @Test
    fun versioning() = runTest {
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
        testScheduler.advanceUntilIdle()
        assertNull(gushSource["v/1.0/way/to"]?.referenceCount)
        assertEquals(1, gushSource["v/2.0/way/to"]?.referenceCount)
        job.cancelAndJoin()
    }

    @Test
    fun `reference counting`() = runTest {
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

    @Test
    fun `live mapping update`() = runTest {
        val store = Store(scope = this)
        val db = Database(scope = this, store = store)
        val pond = Pond(scope = this, source = db)

        val routes = RandomRoutes(
            keys = "abc".map { it.toString() },
            indices = listOf(1, 2, 3),
            keyBias = 1f,
            length = 4..9,
            seed = 7
        ).generate(500)

        val versions = (1..3).map { it }
        val latch = CountDownLatch(versions.size)

        val result = Result(scope = this)

        routes.forEach { route ->
            launch {
                pond.stream(route).collectLatest {
                    result.set(route, it as? String)
                }
            }
        }

        versions.forEach { version ->
            launch {
                routes.forEach { route ->
                    val customRoute =
                        listOf(Location("v/$version.0/${route.take(2).joinToString("/")}")) + route.drop(2)
                    store.set(customRoute, "✅ v$version")
                }
                latch.countDown()
            }
        }

        testScheduler.advanceUntilIdle()
        latch.await()

        routes.forEach { route ->
            val v1 = listOf(Location("v/1.0/${route.take(2).joinToString("/")}")) + route.drop(2)
            val v3 = listOf(Location("v/3.0/${route.take(2).joinToString("/")}")) + route.drop(2)

            val l = store.get(v1) == "✅ v1"
            val r = store.get(v3) == "✅ v3"
            assertEquals(l, r)
        }

        val v1 = result.get()
        assertTrue(v1.isNotEmpty())
        assertTrue(v1.map { it.value }.all { it == "✅ v1" })

        result.clear()

        db.setVersion("v/3.0/")

        testScheduler.advanceUntilIdle()

        val v3 = result.get()

        assertTrue(v3.isNotEmpty())
        assertTrue(v3.map { it.value }.all { it == "✅ v3" })

        assertEquals(v1.keys, v3.keys)

        coroutineContext.cancelChildren()
    }

    private class Result(
        scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
        private val values: MutableMap<Route, String> = mutableMapOf(),
    ) {

        private sealed interface Intent {
            data class Set(val route: Route, val value: String?, val completableDeferred: CompletableDeferred<Unit>): Intent
            data class Clear(val completableDeferred: CompletableDeferred<Unit>): Intent
            data class Get(val completableDeferred: CompletableDeferred<Map<Route, String>>): Intent
        }

        private val actor = scope.actor<Intent> {
            consumeEach { intent ->
                when(intent){
                    is Intent.Clear -> values.clear().also { intent.completableDeferred.complete(Unit) }
                    is Intent.Set -> {
                        if (intent.value != null) {
                            values[intent.route] = intent.value
                        } else {
                            values.remove(intent.route)
                        }
                        intent.completableDeferred.complete(Unit)
                    }
                    is Intent.Get -> intent.completableDeferred.complete(values)
                }
            }
        }

        suspend fun get() =
            CompletableDeferred<Map<Route, String>>().apply {
                actor.send(Intent.Get(this))
            }.await()

        suspend fun clear() =
            CompletableDeferred<Unit>().apply {
                actor.send(Intent.Clear(this))
            }.await()

        suspend fun set(route: Route, value: String?) {
            CompletableDeferred<Unit>().apply {
                actor.send(Intent.Set(route, value, this))
            }.await()
        }
    }
}