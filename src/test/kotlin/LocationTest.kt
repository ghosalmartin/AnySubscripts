package com.bskyb.skynamespace

import Route
import com.bskyb.skynamespace.utils.RandomRoutes
import com.bskyb.skynamespace.utils.not
import compareTo
import index
import key
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class LocationTest {

    @Test
    fun index() {
        val five = 5
        val location = !five
        assertEquals(five, location.index)
    }

    @Test
    fun key() {
        val hello = "hello"
        val location = !hello
        assertEquals(hello, location.key)
    }

    @Test
    fun `comparing mixed with less than left location`() {
        val left = !5
        val right = !"5"
        assertTrue(left < right)
    }

    @Test
    fun `comparing mixed with greater than left location`() {
        val left = !"5"
        val right = !5
        assertFalse(left < right)
    }

    @Test
    fun `comparing indexes with less than left location`() {
        val left = !4
        val right = !5
        assertTrue(left < right)
    }

    @Test
    fun `comparing keys with less than left location`() {
        val left = !"4"
        val right = !"5"
        assertTrue(left < right)
    }

    @Test
    fun `comparing with ios sorted routes`() {
        val routes = !listOf(
            emptyList(),
            listOf(3, "a"),
            listOf(3, "a", "d", "e"),
            listOf("a"),
            listOf("a", 2, "d"),
            listOf("a", "e", 3, "b"),
            listOf("b", "e", "e"),
            listOf("c", 3, "d", "e", "d"),
            listOf("d"),
            listOf("d", 3)
        )
        val sorted: List<Route> = routes.shuffled().sortedWith { o1, o2 -> o1.compareTo(o2) }

        assertEquals(routes, sorted)
    }

    @Test
    fun `comparing routes`() {
        val randomRoutes =
            RandomRoutes(
                keys = "abcde".map { it.toString() },
                indices = listOf(1, 2, 3),
                keyBias = 0.8f,
                length = 0..5,
                seed = 4
            )

        val routes: List<Route> =
            randomRoutes.generate(10).sortedWith { o1, o2 -> o1.compareTo(o2) }

        val expected = !listOf(
            emptyList(),
            emptyList(),
            listOf("a"),
            listOf("a", "a", "e"),
            listOf("c", 1),
            listOf("c", "e"),
            listOf("d", "d", 2, "d"),
            listOf("e"),
            listOf("e"),
            listOf("e", "e", "d"),
        )

        assertEquals(expected, routes)
    }
}
