import kotlinx.coroutines.flow.Flow

interface Geyser<GushID, Gushes : Flow<Any?>, GushToRouteMapping : Flow<Pair<GushID, Route>>> {
    suspend fun stream(gush: GushID): Gushes
    suspend fun source(route: Route): GushToRouteMapping
}
