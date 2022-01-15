package utils

import Geyser
import Location
import Route
import Store
import description
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

typealias Gushes = Flow<Any?>
typealias GushRouteToMapping = Flow<Pair<String, Route>?>

class Database(
    private val store: Store = Store()
) : Geyser<String, Gushes, GushRouteToMapping> {

    private sealed interface Intent {
        data class Stream(val gush: String, val completableDeferred: CompletableDeferred<Gushes>) : Intent
        data class Source(val route: Route, val completableDeferred: CompletableDeferred<GushRouteToMapping>) : Intent
        data class SetVersion(val version: String, val completableDeferred: CompletableDeferred<Unit>) : Intent
    }

    private val versionStateFlow = MutableStateFlow("v/1.0/")
    private var gushRouteCount = 2

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private val actor = scope.actor<Intent> {
        consumeEach { intent ->
            when (intent) {
                is Intent.SetVersion -> {
                    versionStateFlow.value = intent.version
                    intent.completableDeferred.complete(Unit)
                }
                is Intent.Source ->
                    callbackFlow {
                        if (intent.route.size >= gushRouteCount) {
                            val f = scope.launch {
                                versionStateFlow.collectLatest { version ->
                                    val subRoute = intent.route.take(gushRouteCount)
                                    val gush = version + subRoute.joinToString("/") { it.description }
                                    send(Pair(gush, subRoute))
                                }
                            }
                            awaitClose {
                                f.cancel("Never Be Game Over!")
                            }
                        } else {
                            send(null)
                        }
                    }.also { intent.completableDeferred.complete(it) }
                is Intent.Stream -> store.stream(Location(intent.gush)).also {
                    intent.completableDeferred.complete(it)
                }
            }
        }
    }

    suspend fun setVersion(version: String) =
        CompletableDeferred<Unit>().apply {
            actor.send(Intent.SetVersion(version, this))
        }.await()

    override suspend fun stream(gush: String): Gushes =
        CompletableDeferred<Gushes>().apply {
            actor.send(Intent.Stream(gush, this))
        }.await()

    override suspend fun source(route: Route): GushRouteToMapping =
        CompletableDeferred<GushRouteToMapping>().apply {
            actor.send(Intent.Source(route, this))
        }.await()
}

