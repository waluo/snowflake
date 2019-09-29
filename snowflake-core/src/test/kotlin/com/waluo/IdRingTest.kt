package com.waluo

import io.kotlintest.inspectors.forAll
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.StringSpec

class IdRingTest : StringSpec({

    "increase the timestamp when the sequence reaches the maximum" {
        val snowflake = Snowflake(timeBits = 30, workerBits = 30)
        val epochSecond = snowflake.spec().epochSecond
        val sequenceNumberPerSecond = snowflake.spec().sequenceNumberPerSecond.toInt()
        val idRing = IdRing(workerId = 1, snowflake = snowflake, ringBits = 1, timeGen = { epochSecond })

        // the previous sequence is used to initialize
        // 1 << 1 = 2
        val startSequence = 2

        val ids = sequence {
            while (true) yield(idRing.nextId())
        }.take(sequenceNumberPerSecond + 1 - startSequence).toList()

        ids.dropLast(1).forAll {
            snowflake.getDeltaSeconds(it) shouldBe 0
        }
        snowflake.getDeltaSeconds(ids.last()) shouldBe 1
    }

    "generate only unique ids" {
        val snowflake = Snowflake(timeBits = 30, workerBits = 26)
        val worker = IdRing(1, ringBits = 6, snowflake = snowflake)
        val n = 2000000
        val distinct = (1..n).map { worker.nextId() }.distinct()
        distinct.size shouldBe n
        shouldThrow<IllegalArgumentException> {
            IdRing(1, ringBits = 7, snowflake = snowflake)
        }.message shouldBe "ringBits[7] can't be greater or equal to timeBits[7]"
    }

    "i do not care time goes backwards" {
        class StaticTimeWorker {
            val snowflake = Snowflake()
            var time = snowflake.spec().epochSecond + 1
            private val worker = IdRing(0, timeGen = { time })
            fun nextId() = worker.nextId()
        }

        // the previous sequence is used to initialize
        // 1 << 9 = 512
        val startSequence = 512

        val worker = StaticTimeWorker()

        // reported at https://github.com/twitter/snowflake/issues/6
        // first we generate 2 ids with the same time, so that we get the sequqence to 1
        val id1 = worker.nextId()
        worker.snowflake.getDeltaSeconds(id1) shouldBe 1
        worker.snowflake.getSequence(id1) shouldBe startSequence
        val id2 = worker.nextId()
        worker.snowflake.getDeltaSeconds(id2) shouldBe 1
        worker.snowflake.getSequence(id2) shouldBe startSequence + 1

        //then we set the time backwards
        worker.time = worker.snowflake.spec().epochSecond

        val id3 = worker.nextId()
        worker.snowflake.getDeltaSeconds(id3) shouldBe 1
        worker.snowflake.getSequence(id3) shouldBe startSequence + 2
    }
})