import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors

typealias Continuation = ProducerScope<Flow<Any?>>

data class Pond<GushID, Source : Geyser<GushID, Flow<Any?>, Flow<Pair<GushID, Route>?>>>(
    private val source: Source,
    private val store: Store = Store(),
    private val sourceIDs: HashMap<Route, GushID> = hashMapOf(),
    private val gushSources: HashMap<GushID, GushSource> = hashMapOf()
) {

    private var cancellingGracePeriodInNanoseconds: Long = 0
        set(value) {
            field = maxOf(0, value * 1_000_000_000)
        }

    data class GushSource(
        val job: Job,
        var pendingContinuations: MutableList<PendingContinuation>?,
        var cancelTimestamp: Long? = 0L,
        var referenceCount: Int = 0,
    ) {
        constructor(job: Job, pendingContinuation: PendingContinuation) : this(job, mutableListOf(pendingContinuation))
    }

    data class PendingContinuation(
        val route: Route,
        val continuation: Continuation
    )

    private interface Intent {
        data class Stream(val route: Route, val completableDeferred: CompletableDeferred<Flow<Any?>>) : Intent
        data class Cancel<GushID>(val id: GushID) : Intent
        data class Yield<GushID>(
            val route: Route,
            val source: GushID,
            val continuation: Continuation
        ) : Intent

        data class Count<GushID>(val id: GushID, val change: Int) : Intent
    }

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private val actor = scope.actor<Intent> {
        consumeEach {
            when (it) {
                is Intent.Stream -> streamActor.send(it)
                is Intent.Yield<*> -> yieldActor.send(it)
                is Intent.Count<*> -> countActor.send(it)
                is Intent.Cancel<*> -> cancelActor.send(it)
            }
        }
    }
    private val streamActor: SendChannel<Intent.Stream> = scope.actor {
        consumeEach { intent ->
            callbackFlow {
                val job = scope.launch {
                    source.source(intent.route).filterNotNull().collectLatest { (srcID, srcRoute) ->
                        val id = sourceIDs[srcRoute]

                        if (id == srcID) {
                            val gushSource = gushSources[srcID]

                            if (gushSource?.pendingContinuations != null) {
                                gushSource.pendingContinuations?.add(
                                    PendingContinuation(
                                        route = intent.route,
                                        continuation = this@callbackFlow
                                    )
                                )
                            } else {
                                yield(intent.route, srcID, this@callbackFlow)
                            }
                            return@collectLatest
                        }

                        id?.let { cancel(it) }

                        val job = scope.launch {
                            source.stream(srcID).collectLatest { gush ->
                                store.set(srcRoute, gush)
                                gushSources[srcID]?.let { gushSource ->
                                    gushSource.pendingContinuations?.let { pendingContinuations ->
                                        val pending = pendingContinuations.toList()
                                        gushSource.pendingContinuations = null
                                        pending.forEach { (route, continuation) ->
                                            yield(route, srcID, continuation)
                                        }
                                    }
                                } ?: return@collectLatest
                            }
                        }
                        sourceIDs[srcRoute] = srcID
                        gushSources[srcID] = GushSource(
                            job = job,
                            pendingContinuation = PendingContinuation(
                                route = intent.route,
                                continuation = this@callbackFlow
                            ),
                        )
                    }
                }
                awaitClose {
                    job.cancel()
                }
            }.flatMapLatest { it }.also { intent.completableDeferred.complete(it) }
        }
    }

    private val yieldActor: SendChannel<Intent.Yield<*>> = scope.actor {
        consumeEach { (route, source, continuation) ->
            continuation.send(
                callbackFlow {
                    count(source as GushID, +1)
                    val job = scope.launch {
                        store.stream(route).collectLatest {
                            this@callbackFlow.send(it)
                        }
                    }

                    awaitClose {
                        job.cancel()
                        scope.launch {
                            count(source as GushID, -1)
                        }
                    }
                }
            )
        }
    }

    private val countActor: SendChannel<Intent.Count<*>> = scope.actor {
        consumeEach { (id, change) ->
            gushSources[id]?.let {
                val result = it.referenceCount + change
                val gushId = id as GushID
                when {
                    result > 0 -> {
                        it.referenceCount = result
                        it.cancelTimestamp = null
                    }
                    cancellingGracePeriodInNanoseconds > 0 -> {
                        it.cancelTimestamp = System.currentTimeMillis()

                        scope.launch {
                            val t = it.cancelTimestamp
                            val sleep = cancellingGracePeriodInNanoseconds
                            delay(sleep)
                            if (it.cancelTimestamp == t) {
                                cancel(gushId)
                            }
                        }
                    }
                    else -> cancel(gushId)
                }
            }
        }
    }

    private val cancelActor: SendChannel<Intent.Cancel<*>> = scope.actor {
        consumeEach { (id) ->
            gushSources.remove(id)?.job?.cancelAndJoin()
        }
    }

    private suspend fun yield(route: Route, source: GushID, continuation: ProducerScope<Flow<Any?>>) {
        actor.send(Intent.Yield(route, source, continuation))
    }

    suspend fun stream(route: Route): Flow<Any?> =
        CompletableDeferred<Flow<Any?>>().apply {
            actor.send(Intent.Stream(route, this))
        }.await()

    private suspend fun count(id: GushID, change: Int) {
        actor.send(Intent.Count(id, change))
    }

    private suspend fun cancel(id: GushID) {
        actor.send(Intent.Cancel(id))
    }

}