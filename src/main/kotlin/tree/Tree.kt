package tree

import dropFirst
import lineage

data class Tree<Key, Value>(
    private var value: Value? = null,
    private var branches: MutableMap<Key, Tree<Key, Value>> = mutableMapOf()
) where Key : Comparable<Key> {

    operator fun invoke(): Value? = value

    operator fun invoke(value: Value?) {
        this.value = value
    }

    operator fun get(vararg route: Key, defaultValue: Value? = null): Value? =
        get(route.toList(), defaultValue)

    operator fun <Route> get(route: Route, defaultValue: Value? = null): Value? where Route : Collection<Key> =
        defaultValue?.let {
            getValueWithDefault(route, defaultValue)
        } ?: getValue(route)

    operator fun <Route> set(route: Route, newValue: Value?) where Route : Collection<Key> {
        setValue(route, newValue)
    }

    operator fun set(vararg route: Key, newValue: Value?) {
        set(route.toList(), newValue)
    }

    operator fun set(vararg route: Key, newTree: Tree<Key, Value>?) {
        setTree(route.toList(), newTree)
    }

    fun <Route> getTree(
        route: Route,
        defaultTree: Tree<Key, Value>? = null
    ): Tree<Key, Value>? where Route : Collection<Key> =
        defaultTree?.let {
            getTreeWithDefault(route, it)
        } ?: getTree(route)

    private fun <Route> getValueWithDefault(route: Route, defaultValue: Value): Value? where Route : Collection<Key> =
        getValue(route) ?: run {
            setValue(route, defaultValue)
            defaultValue
        }

    private fun <Route> getTreeWithDefault(
        route: Route,
        defaultTree: Tree<Key, Value>
    ): Tree<Key, Value> where Route : Collection<Key> =
        getTree(route) ?: kotlin.run {
            setTree(route, defaultTree)
            defaultTree
        }

    private fun <Route> getValue(route: Route): Value? where Route : Collection<Key> =
        getTree(route)?.value

    private fun <Route> setValue(route: Route, newValue: Value?) where Route : Collection<Key> {
        if (route.isEmpty()) {
            invoke(newValue)
        } else {
            branches.getOrPut(route.first()) { Tree() }.setValue(route.dropFirst, newValue)
        }
    }

    private fun <Route> getTree(route: Route): Tree<Key, Value>? where Route : Collection<Key> =
        if (route.isEmpty()) this else branches[route.first()]?.getTree(route.dropFirst)

    private fun <Route> setTree(route: Route, newValue: Tree<Key, Value>?) where Route : Collection<Key> {
        val key = route.firstOrNull()
        if (key != null) {
            branches.getOrPut(key) { Tree() }.setTree(route.dropFirst, newValue)
        } else {
            value = newValue?.value
            branches = newValue?.branches ?: mutableMapOf()
        }
    }

    fun <Route> routes(route: Route): List<List<Key>> where Route : Collection<Key> {
        val o = route.lineage.reversed().toMutableList()
        val subTree: Tree<Key, Value>? = getTree(route)
        subTree?.traverse { subRoute: Collection<Key>, _: Value? ->
            o.add(route + subRoute)
        }
        return o
    }

    fun traverse(
        sorted: Boolean = true,
        operation: (route: Collection<Key>, value: Value?) -> Unit
    ) {
        traverse(
            sorted = sorted,
            route = emptyList(),
            tree = this,
            operation = operation
        )
    }

    private fun traverse(
        sorted: Boolean = true,
        route: Collection<Key>,
        tree: Tree<Key, Value>,
        operation: (route: Collection<Key>, value: Value?) -> Unit
    ) {
        operation(route, tree.value)
        tree.branches
            .entries
            .apply { if (sorted) sortedWith { t1, t2 -> t1.key.compareTo(t2.key) } }
            .toList()
            .forEach { (key, tree) ->
                traverse(sorted = sorted, route = route + listOf(key), tree = tree, operation = operation)
            }
    }
}
