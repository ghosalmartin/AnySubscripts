package com.bskyb.skynamespace

import get
import org.junit.jupiter.api.Test
import set
import kotlin.test.assertEquals

class AnyTest {

    @Test
    fun subscripts() {

        var o: Any?

        o = null
        o = emptyList<Any?>()
        o = emptyMap<Any?, Any?>()

        val expected = "👋"

        o.get() = "👋"
        assertEquals(expected, o[""])

    }

//
//    func test_subscript() throws {
//
//        var o: Any?
//
//        o = nil
//        o = []
//        o = [:]
//
//        o[] = "👋"
//        try hope(o[]) == "👋"
//
//            o["one"] = 1
//            try hope(o["one"]) == 1
//
//                o["one", 2] = 2
//                try hope(o["one", 2]) == 2
//
//                    o["one", 3] = nil
//                    try hope(o["one"]) == [nil, nil, 2] // did not append
//                        o["one", 2] = ["three": 4]
//                        try hope(o["one", 2, "three"]) == 4
//                            try hope(o[\.["one"][2]["three"]]) == 4
//
//                                o["one", 2] = nil
//                                hope.true(o["one"] == nil)
//
//                                o["one", "two"] = nil
//                                hope.true(o[] == nil)
//                            }
//
}