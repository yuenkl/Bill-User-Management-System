package com.bill.usermanagmentsystem

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform