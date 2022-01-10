import any.any
import any.get
import any.set
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

typealias BatchUpdates = Collection<Pair<Route, Any?>>
typealias TransactionalLevel = Int
typealias ID = Int
typealias Subject = MutableMap<ID, ProducerScope<Any?>>

class Store {
    private sealed interface Intent {
        data class Set(val route: Route, val value: Any?, val ack: CompletableDeferred<Boolean>) : Intent
        data class Insert(
            val route: Route,
            val completedDeferred: CompletableDeferred<Flow<Any?>>
        ) : Intent

        data class Batch(val updates: BatchUpdates) : Intent
        data class Transaction(val updates: Store) : Intent
        data class Get(val route: Route?, val ack: CompletableDeferred<Any?>) : Intent
    }

    private val scope = CoroutineScope(Dispatchers.Default)

    private val actor = scope.actor<Intent> {
        val transactionUpdates = mutableMapOf<TransactionalLevel, BatchUpdates>()
        val subscriptions: Tree<Location, Subject> = Tree()
        val data: Any? by any()

        var transactionLevel = 0
        var count = 0

        consumeEach { intent ->
            when (intent) {
                is Intent.Get -> data.also { intent.ack.complete(intent.route?.let { data[it] } ?: data) }
                is Intent.Batch -> {
                    val routes: MutableSet<Route> = mutableSetOf()
                    intent.updates.forEach { (route, value) ->
                        data[route] = value
                        routes += subscriptions.routes(route)
                    }
                    routes.sortedWith { route1, route2 -> route1.compareTo(route2) }.forEach { route ->
                        subscriptions[route]?.let {
                            it.values.forEach { it.send(data[route]) }
                        }
                    }
                }
                is Intent.Insert -> callbackFlow {
                    val route = intent.route
                    send(data[route])
                    count++
                    val id = count
                    subscriptions[route, mutableMapOf()]?.put(id, this)
                    awaitClose {
                        subscriptions[route]?.let {
                            it.remove(id)
                            if (it.isEmpty()) {
                                subscriptions[route] = null as Subject?
                            }
                        }
                    }
                }.apply {
                    intent.completedDeferred.complete(this)
                }
                is Intent.Transaction -> TODO()
                is Intent.Set -> {
                    val (route, value, ack) = intent
                    if (transactionLevel == 0) {
                        data[route] = value
                        route.lineage.reversed().forEach { lineage ->
                            subscriptions[lineage].let { subject ->
                                subject?.values?.forEach {
                                    it.send(data[lineage])
                                }
                            }
                        }
                        subscriptions.getTree(route)?.traverse { subRoute, subject ->
                            subject?.let {
                                launch {
                                    subject.values.forEach {
                                        it.send(value[subRoute])
                                    }
                                }
                            }
                        }
                        ack.complete(true)
                    } else {
                        transactionUpdates.getOrPut(
                            key = transactionLevel,
                            defaultValue = { listOf(route to value) }
                        )
                    }
                }
            }
        }
    }

    //TODO Bufferingpolicy variable
    suspend fun stream(vararg route: Location): Flow<Any?> = stream(route.toList())

    //TODO Bufferingpolicy variable, Also callbackFlow may die after it completes cause wut
    suspend fun stream(route: Route): Flow<Any?> =
        CompletableDeferred<Flow<Any?>>().apply {
            actor.send(Intent.Insert(route, this))
        }.await()

    suspend fun set(vararg route: Location, value: Any?) = set(route.toList(), value)
    suspend fun set(route: Route, value: Any?) =
        CompletableDeferred<Boolean>().apply {
            actor.send(Intent.Set(route, value, this))
        }.await()

    suspend fun batch(batchUpdates: BatchUpdates) {
        actor.send(Intent.Batch(batchUpdates))
    }

    suspend fun get(vararg route: Location) = get(route.toList())
    suspend fun get(route: Route? = null): Any? =
        CompletableDeferred<Any?>().apply {
            actor.send(Intent.Get(route, this))

        }.await()
}