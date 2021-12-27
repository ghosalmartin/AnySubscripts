package com.bskyb.skynamespace

import any
import com.bskyb.skynamespace.utils.not
import org.junit.jupiter.api.Test
import get
import invoke
import set
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AnyTest {

    @Test
    fun subscripts() {

        //Must use Delegate as we can't do self=mapOf() in an operator.
        var o: Any? by any()

        o = null
        o = emptyList<Any?>()
        o = emptyMap<Any?, Any?>()

        o = "👋"
        assertEquals("👋", o())

        o["two"] = 2
        assertEquals(2, o["two"])

        o["one"] = 1
        assertEquals(1, o["one"])

        o[!"one", !2] = 2
        assertEquals(2, o[!"one", !2])

        o[!"one", !3] = null

        o[10] = 10
        assertEquals(10, o[10])

        assertEquals(listOf(null, null, 2), o[!"one"])
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