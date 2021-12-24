package com.bskyb.skynamespace.utils

import Location
import Route

internal operator fun Collection<Collection<Any>>.not(): List<Route> =
    map {
        when (it) {
            is List<*> -> !it
            else -> throw IllegalArgumentException("This only accepts Collections of Collections of Strings/Ints")
        }
    }

internal operator fun Collection<Any>.not(): Route =
    map {
        when (it) {
            is String -> !it
            is Int -> !it
            else -> throw IllegalArgumentException("Location only accepts Strings and Ints")
        }
    }

internal operator fun String.not(): Location = Location(this)
internal operator fun Int.not(): Location = Location(this)

