# AnySubscripts

Counterpart to https://github.com/screensailor/OptionalSubscripts/

[![Unit Tests](https://github.com/ghosalmartin/AnySubscripts/actions/workflows/gradle.yml/badge.svg)](https://github.com/ghosalmartin/AnySubscripts/actions/workflows/gradle.yml)

All equality expressions below return `true`:

```kotlin
var o: Any? by any()

o = null
o = emptyList<Any?>()
o = emptyMap<Any?, Any?>()

o = "👋"
assertEquals("👋", o())

o["one"] = 1
assertEquals(1, o["one"])

o["one", 2] = 2
assertEquals(2, o["one", 2])

o["one", 3] = null
assertEquals(listOf(null, null, 2), o["one"])

o["one", 2] = mapOf("three" to 4)
assertEquals(4, o["one", 2, "three"])

assertEquals(4, o["one", 2, "three"])

o["one", 2] = null
assertNull(o["one"])
```

... including an Store actor with routed streams, batch updates and atomic transactions:

```
runBlocking {
    val store = Store()
    val route = !listOf("way", "to", "my", "heart")

    store.set(route, "?")

    store.stream(route)
        .collectLatest {
            when (it) {
                "?" -> store.set(route, "❤️")
                "❤️" -> store.set(route, "💛")
                "💛" -> store.set(route, "💚")
                "💚" -> cancel("Never Be Game Over!")
                else -> throw IllegalArgumentException("What else is in there? $it")
            }
        }
    }
}
```

