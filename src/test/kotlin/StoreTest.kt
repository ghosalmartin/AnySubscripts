import any.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import utils.not

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
}