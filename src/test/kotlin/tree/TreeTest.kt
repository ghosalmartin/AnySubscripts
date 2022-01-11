package tree

import Location
import utils.RandomRoutes
import utils.not
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@OptIn(ExperimentalTime::class)
internal class TreeTest {

    @Test
    fun subscript() {
        val tree = Tree<Location, Int>()

        assertNull(tree())

        tree(0)
        assertEquals(0, tree())

        tree[!1] = 1
        assertEquals(1, tree[!1])

        tree[!1, !2, !3] = 3
        assertEquals(3, tree[!1, !2, !3])

        tree[!1, !"2", !3] = 3
        assertEquals(3, tree[!1, !"2", !3])

        tree[!1] = null as Int?
        assertEquals(3, tree[!1, !2, !3])
        assertEquals(3, tree[!1, !"2", !3])

        tree[!1] = null as Tree<Location, Int>?
        assertNull(tree[!1, !2, !3])
        assertNull(tree[!1, !"2", !3])

        tree[!1, !2] = Tree(
            branches = mutableMapOf(
                !1 to Tree(value = 1),
                !2 to Tree(value = 2),
                !"a" to Tree(value = 3),
                !"b" to Tree(value = 4)
            )
        )

        assertEquals(1, tree[!1, !2, !1])
        assertEquals(2, tree[!1, !2, !2])
        assertEquals(3, tree[!1, !2, !"a"])
        assertEquals(4, tree[!1, !2, !"b"])

        tree[!1, !2] = Tree(
            branches = mutableMapOf(
                !"a" to Tree(value = 3), !"b" to Tree(value = 5)
            )
        )

        assertEquals(3, tree[!1, !2, !"a"])
        assertEquals(5, tree[!1, !2, !"b"])
    }

    @Test
    fun `get set validity`() {
        val randomRoutes = RandomRoutes(
            keys = "abcde".map { it.toString() },
            indices = listOf(1, 2, 3),
            keyBias = 0.8f,
            length = 5..20,
            seed = 4
        )

        val routes: List<Collection<Location>> = randomRoutes.generate(10000)
        val branches = mutableMapOf<Location, Tree<Location, String>>()
        val tree = Tree(branches = branches)

        routes.forEach { route ->
            tree[route] = "✅"
            assertEquals("✅", tree[route])
        }
    }

    @Test
    fun `set performance`() {
        val randomRoutes = RandomRoutes(
            keys = "abcde".map { it.toString() },
            indices = listOf(1, 2, 3),
            keyBias = 0.8f,
            length = 5..20,
            seed = 4
        )

        val routes: List<Collection<Location>> = randomRoutes.generate(10000)
        val branches = mutableMapOf<Location, Tree<Location, String>>()
        val tree = Tree(branches = branches)

        measureTime {
            routes.forEach { route ->
                tree[route] = "✅"
            }
        }.also {
            println(it)
        }

        assertEquals(6, branches.size)
    }

    @Test
    fun traverse() {
        val tree = Tree<Int, Int>()
        val traversal = mutableMapOf<List<Int>, Int>()

        for (x in 1..5) {
            for (y in 1..5) {
                for (z in 1..5) {
                    tree[x, y, z] = x * y * z
                    traversal.put(listOf(x, y, z), x * y * z)
                }
            }
        }

        tree.traverse { route, value ->
            if (traversal.get(key = route.toList()) == value) {
                traversal.remove(route)
            }
        }

        assertEquals(0, traversal.size)
    }
}
