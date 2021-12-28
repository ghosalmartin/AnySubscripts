package com.bskyb.skynamespace

import Location
import any
import com.bskyb.skynamespace.utils.RandomRoutes
import com.bskyb.skynamespace.utils.not
import delegateGet
import org.junit.jupiter.api.Test
import get
import invoke
import set
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@OptIn(ExperimentalTime::class)
class AnyTest {

    @Test
    fun subscripts() {

        //Must use Delegate as we can't do self=mapOf() in an operator.
        var o: Any? by any()

        o = null
        o = emptyList<Any?>()
        o = emptyMap<Any?, Any?>()

        o = "👋"
        assertEquals("👋", o())

        o["two"] = 2
        assertEquals(2, o["two"])

        o["one"] = 1
        assertEquals(1, o["one"])

        o["one", 2] = 2
        assertEquals(2, o["one", 2])

        o["one", 3] = null

        o["one", 10] = 10
        assertEquals(10, o["one", 10])
    }

    @Test
    fun swiftFunctionality() {
        var o: Any? by any()

        o = null
        o = emptyList<Any?>()
        o = emptyMap<Any?, Any?>()

        o = "👋"
        assertEquals("👋", o())

        o["one"] = 1
        assertEquals(1, o["one"])

        o["one", 2] = 2
        assertEquals(2, o["one", 2])

        o["one", 3] = null
        assertEquals(listOf(null, null, 2), o["one"])

        o["one", 2] = mapOf("three" to 4)
        assertEquals(4, o["one", 2, "three"])

        assertEquals(4, o["one", 2, "three"])

        o["one", 2] = null
        assertNull(o["one"])
    }

    @Test
    fun nestedKeys() {
        val o: Any? by any()

        val route = listOf("e", "a", "a", "e", 1,2,3,"e", "e", "a", "a", "e", "e", "e", "a", "a", "e", "e", 1, 2, 3).map {
            when (it) {
                is String -> Location(it)
                is Int -> Location(it)
                else -> throw IllegalArgumentException()
            }
        }
        println(route.size)
        o[route] = "✅"

        println(o)
        assertEquals("✅", o[route])
    }

    @Test
    fun `any path performance`() {
        val randomRoutes =
            RandomRoutes(
                keys = "abcde".map { it.toString() },
                indices = listOf(1, 2, 3),
                keyBias = 0.8f,
                length = 5..20,
                seed = 4
            )

        val routes = randomRoutes.generate(10000)

        val time = measureTime {
            routes.forEach {
                get(it)
            }
        }
        println(time)
    }

    @Test
    fun `set performance`() {
        val randomRoutes =
            RandomRoutes(
                keys = "abcde".map { it.toString() },
                indices = listOf(1, 2, 3),
                keyBias = 0.8f,
                length = 5..20,
                seed = 4
            )

        val routes = randomRoutes.generate(10000)

        val o: Any by any()

        val time = measureTime {
            routes.forEach { route ->
                o[route] = "✅"
            }
        }
        println((o() as List<*>).size)
        println(time)
    }

    @Test
    fun `get validity`() {
        val randomRoutes =
            RandomRoutes(
                keys = "abcde".map { it.toString() },
                indices = listOf(1, 2, 3),
                keyBias = 0.8f,
                length = 5..20,
                seed = 4
            )

        val routes = randomRoutes.generate(10000)

        val o: Any by any()

        routes.forEach { route ->
            o[route] = "✅"
        }

        routes.forEach { route ->
            assertEquals("✅", o[route])
        }

    }
}