data class Drop<Success>(
    val origin: Route,
    val destination: Route,
    val result: Result<Success>
)

//
// public extension Drop {
//
//    @inlinable func map<A>(_ transform: (Success) -> A) -> Drop<Route, A, Failure> {
//        Drop<Route, A, Failure>(
//            origin: origin,
//            destination: destination,
//            result: result.map(transform)
//        )
//    }
// }
