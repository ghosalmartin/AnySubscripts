package com.bskyb.skynamespace

import any
import com.bskyb.skynamespace.utils.not
import org.junit.jupiter.api.Test
import get
import invoke
import set
import kotlin.test.assertEquals

class AnyTest {

    @Test
    fun subscripts() {

        //Must use Delegate as we can't do self=mapOf() in an operator.
        var o: Any? by any()

        o = null
        o = emptyList<Any?>()
        o = emptyMap<Any?, Any?>()


        //Sets Root Element API 1
        //Also must use o() to retrieve the value if we want to keep access to the delegate for later use.
        val expected = "👋"
        o(to = "👋")
        assertEquals(expected, o())

        //Sets Root Element API 2
        //Also must use o() to retrieve the value if we want to keep access to the delegate for later use.
        val expected2 = "📎"
        o = expected2
        assertEquals(expected2, o())

        //Sets Element via invoke API 1
        o("two", to = 2)
        assertEquals(2, o("two"))

        //Sets Elements via operator but cannot set Root element via this method
        o["one"] = 1
        assertEquals(1, o["one"])

        o[!"one", !2] = 2
        assertEquals(2, o[!"one", !2])

        println(o)
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