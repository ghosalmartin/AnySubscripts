package com.bskyb.skynamespace

import any
import com.bskyb.skynamespace.utils.RandomRoutes
import org.junit.jupiter.api.Test
import get
import invoke
import set
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@OptIn(ExperimentalTime::class)
class AnyTest {

    @Test
    fun subscripts() {
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
        val oneList = (o["one"] as List<any>).map { it() }
        assertEquals(listOf(null, null, 2), oneList)

        o["one", 2] = mapOf("three" to 4)
        assertEquals(4, o["one", 2, "three"])

        assertEquals(4, o["one", 2, "three"])

        o["one", 2] = null
        assertNull(o["one"])
    }

    @Test
    fun `get set validity`() {
        val randomRoutes =
            RandomRoutes(
                keys = "abcde".map { it.toString() },
                indices = listOf(1, 2, 3),
                keyBias = 0.8f,
                length = 5..20,
                seed = 4
            )

        val routes = randomRoutes.generate(10000)

        val o: Any? by any()

        routes.forEach { route ->
            o[route] = "✅"
            assertEquals("✅", o[route])
        }
    }

    @Test
    fun `path performance`() {
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

        val o: Any? by any()

        val time = measureTime {
            routes.forEach { route ->
                o[route] = "✅"
            }
        }

        println(time)
    }
}