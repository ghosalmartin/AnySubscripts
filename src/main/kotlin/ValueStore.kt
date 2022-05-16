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

typealias Stream<T> = ProducerScope<T>

class ValueStore<Value : Any>(
    scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
    initialValue: Value,
) {

    private val continuations: MutableMap<Int, Stream<Value>> = mutableMapOf()
    private var count = 0
    private var value: Value = initialValue

    private sealed interface Intent {
        data class Set<Value>(val newValue: Value, val completableDeferred: CompletableDeferred<Unit>) : Intent
        data class Insert<Value>(val continuation: Stream<Value>, val completableDeferred: CompletableDeferred<Unit>) :
            Intent

        data class Remove(val id: Int, val completableDeferred: CompletableDeferred<Unit>) : Intent
        data class StreamOp<Value>(val completedDeferred: CompletableDeferred<Flow<Value>>) : Intent
    }

    private val actor = scope.actor<Intent> {
        consumeEach {
            when (it) {
                is Intent.StreamOp<*> -> streamActor.send(it as Intent.StreamOp<Value>)
                is Intent.Insert<*> -> insertActor.send(it as Intent.Insert<Value>)
                is Intent.Set<*> -> setActor.send(it as Intent.Set<Value>)
                is Intent.Remove -> removeActor.send(it)
            }
        }
    }

    private val streamActor: SendChannel<Intent.StreamOp<Value>> = scope.actor<Intent.StreamOp<Value>> {
        consumeEach {
            it.completedDeferred.complete(
                callbackFlow {
                    awaitClose { }
                    insert(this)
                }
            )
        }
    }

    private val insertActor: SendChannel<Intent.Insert<Value>> = scope.actor<Intent.Insert<Value>> {
        consumeEach {
            it.continuation.send(value)
            val id = count + 1
            count = id
            continuations[id] = it.continuation
            it.continuation.invokeOnClose {
                scope.launch { remove(id) }
            }
            it.completableDeferred.complete(Unit)
        }
    }

    private val removeActor: SendChannel<Intent.Remove> = scope.actor<Intent.Remove> {
        consumeEach {
            continuations.remove(it.id)
            it.completableDeferred.complete(Unit)
        }
    }

    private val setActor: SendChannel<Intent.Set<Value>> = scope.actor<Intent.Set<Value>> {
        consumeEach {
            value = it.newValue
            continuations.forEach { continuation -> continuation.value.send(value) }
            it.completableDeferred.complete(Unit)
        }
    }

    suspend fun set(newValue: Value) {
        CompletableDeferred<Unit>().apply {
            actor.send(Intent.Set(newValue, this))
        }
    }

    suspend fun stream(): Flow<Value> =
        CompletableDeferred<Flow<Value>>().apply {
            actor.send(Intent.StreamOp(this as CompletableDeferred<Flow<Any>>))
        }.await()

    suspend fun insert(continuation: Stream<Value>) {
        CompletableDeferred<Unit>().apply {
            insertActor.send(Intent.Insert(continuation, this))
        }
    }

    suspend fun remove(id: Int) {
        CompletableDeferred<Unit>().apply {
            actor.send(Intent.Remove(id, this))
        }
    }
}