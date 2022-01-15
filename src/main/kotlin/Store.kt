import any.any
import any.get
import any.set
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import tree.Tree

typealias BatchUpdates = MutableList<Pair<Route, Any?>>
typealias TransactionalLevel = Int
typealias ID = Int
typealias Subject = MutableMap<ID, ProducerScope<Any?>>

class Store(
    scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
    private val transactionUpdates: MutableMap<TransactionalLevel, BatchUpdates> = mutableMapOf(),
    private val subscriptions: Tree<Location, Subject> = Tree(),
    private var transactionLevel: Int = 0,
    private var count: Int = 0,
) {
    private sealed interface Intent {
        data class Set(val route: Route, val value: Any?, val ack: CompletableDeferred<Boolean>) : Intent
        data class Insert(val route: Route, val completedDeferred: CompletableDeferred<Flow<Any?>>) : Intent
        data class Batch(val updates: BatchUpdates, val ack: CompletableDeferred<Boolean>) : Intent
        sealed interface Transaction : Intent {
            data class IncrementTransactionLevel(val ack: CompletableDeferred<Boolean>) : Transaction
            data class ProcessTransaction(
                val ack: CompletableDeferred<Boolean>,
                val wasSuccess: Boolean,
            ) : Transaction
        }

        data class Get(val route: Route?, val ack: CompletableDeferred<Any?>) : Intent
        data class TransactionLevel(val ack: CompletableDeferred<Int>) : Intent
    }

    private val data: Any? by any()

    private val actor = scope.actor<Intent> {
        consumeEach {
            when (it) {
                is Intent.Batch -> batchActor.send(it)
                is Intent.Get -> getActor.send(it)
                is Intent.Insert -> insertActor.send(it)
                is Intent.Set -> setActor.send(it)
                is Intent.Transaction -> transactionActor.send(it)
                is Intent.TransactionLevel -> transactionLevelActor.send(it)
            }
        }
    }

    private val transactionActor: SendChannel<Intent.Transaction> = scope.actor {
        consumeEach {
            when (it) {
                is Intent.Transaction.IncrementTransactionLevel -> transactionLevel += 1
                is Intent.Transaction.ProcessTransaction -> {
                    if (it.wasSuccess) {
                        val levelUpdates: MutableList<Pair<Collection<Location>, Any?>> =
                            transactionUpdates.remove(transactionLevel) ?: mutableListOf()
                        transactionLevel--
                        if (transactionLevel > 0) {
                            transactionUpdates.getOrPut(transactionLevel) { mutableListOf() }
                                .addAll(levelUpdates)
                        } else {
                            batch(levelUpdates)
                        }
                        it.ack.complete(true)
                    } else {
                        transactionUpdates.remove(transactionLevel)
                        transactionLevel -= 1
                        it.ack.completeExceptionally(IllegalStateException("Transaction failed"))
                    }
                }
            }
        }
    }

    private val transactionLevelActor: SendChannel<Intent.TransactionLevel> = scope.actor {
        consumeEach {
            it.ack.complete(transactionLevel)
        }
    }

    private val getActor: SendChannel<Intent.Get> = scope.actor {
        consumeEach { intent ->
            data.also {
                intent.ack.complete(intent.route?.let { data[it] } ?: data)
            }
        }
    }

    private val insertActor: SendChannel<Intent.Insert> = scope.actor {
        consumeEach {
            callbackFlow {
                val route = it.route
                send(data[route])
                count += 1
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
                it.completedDeferred.complete(this)
            }
        }
    }

    private val batchActor: SendChannel<Intent.Batch> = scope.actor {
        consumeEach {
            val routes: MutableSet<Route> = mutableSetOf()
            it.updates.forEach { (route, value) ->
                data[route] = value
                routes += subscriptions.routes(route)
            }
            routes.sortedWith { route1, route2 -> route1.compareTo(route2) }
                .forEach { route ->
                    subscriptions[route]?.let {
                        it.values.forEach { it.send(data[route]) }
                    }
                }
            it.ack.complete(true)
        }
    }

    private val setActor: SendChannel<Intent.Set> = scope.actor {
        consumeEach {
            val (route, value, ack) = it
            if (transactionLevel == 0) {
                data[route] = value
                route.lineage.reversed().forEach { lineage ->
                    subscriptions[lineage].let { subject ->
                        subject?.values?.toList()?.forEach {
                            it.send(data[lineage])
                        }
                    }
                }
                subscriptions.getTree(route)?.traverse { subRoute, subject ->
                    subject?.let {
                        scope.launch {
                            subject.values.toList().forEach {
                                it.send(value[subRoute])
                            }
                        }
                    }
                }
            } else {
                transactionUpdates.getOrPut(
                    key = transactionLevel,
                    defaultValue = { mutableListOf() }
                ).add(route to value)
            }
            ack.complete(true)
        }
    }

    suspend fun stream(vararg route: Location): Flow<Any?> = stream(route.toList())
    suspend fun stream(route: Route): Flow<Any?> =
        CompletableDeferred<Flow<Any?>>().apply {
            actor.send(Intent.Insert(route, this))
        }.await()

    suspend fun set(vararg location: Location, value: Any?) = set(location.toList(), value)
    suspend fun set(route: Route, value: Any?) =
        CompletableDeferred<Boolean>().apply {
            actor.send(Intent.Set(route, value, this))
        }.await()

    suspend fun batch(batchUpdates: BatchUpdates): Boolean =
        CompletableDeferred<Boolean>().apply {
            actor.send(Intent.Batch(batchUpdates, this))
        }.await()

    suspend fun get(vararg route: Location) = get(route.toList())
    suspend fun get(route: Route? = null): Any? =
        CompletableDeferred<Any?>().apply {
            actor.send(Intent.Get(route, this))
        }.await()

    suspend fun transaction(updates: suspend Store.() -> Unit) {
        CompletableDeferred<Boolean>().apply {
            actor.send(Intent.Transaction.IncrementTransactionLevel(this))
            val isSuccessful = try {
                updates()
                true
            } catch (exception: Exception) {
                false
            }
            actor.send(Intent.Transaction.ProcessTransaction(this, isSuccessful))
        }.await()
    }

    suspend fun transactionalLevel(): Int =
        CompletableDeferred<Int>().apply {
            actor.send(Intent.TransactionLevel(this))
        }.await()
}
