# AnySubscripts

Counterpart to https://github.com/screensailor/OptionalSubscripts/

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
val oneList = (o["one"] as List<any>).map { it() }
assertEquals(listOf(null, null, 2), oneList)

o["one", 2] = mapOf("three" to 4)
assertEquals(4, o["one", 2, "three"])

assertEquals(4, o["one", 2, "three"])

o["one", 2] = null
assertNull(o["one"])
```