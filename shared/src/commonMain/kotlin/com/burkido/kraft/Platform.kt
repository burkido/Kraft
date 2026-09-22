package com.burkido.kraft

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform