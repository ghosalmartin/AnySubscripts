package com.bskyb.skynamespace

import any
import org.junit.jupiter.api.Test
import set
import get
import invoke
import kotlin.test.assertEquals

class AnyTest {

    @Test
    fun subscripts() {

        var o: Any? by any()

        o = null
        o = emptyList<Any?>()
        o = emptyMap<Any?, Any?>()

        val expected = "👋"

        o("👋")

        assertEquals(expected, o())

        val expected2 = "📎"

        o = expected2

        assertEquals(expected2, o())


        o["one"] = 1

        assertEquals(1, o["one"])
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