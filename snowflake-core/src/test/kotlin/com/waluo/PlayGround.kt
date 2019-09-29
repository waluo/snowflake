package com.waluo

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

fun main() {
    qps(IdRing(1))
}

private fun qps(snowflakeId: SnowflakeId) {
    val counter = AtomicLong()

    val pool = Executors.newFixedThreadPool(50)
    for (i in 1..50)
        pool.submit {
            while (true) {
                snowflakeId.nextId()
                counter.incrementAndGet()
            }
        }

    println(snowflakeId.snowflake.spec())

    while (true) {
        counter.set(0)
        Thread.sleep(1000)
        println("${counter.get()} ids/second")
    }
}