package com.waluo

interface SnowflakeId {
    val workerId: Long
    val snowflake: Snowflake
    fun nextId(): Long
}