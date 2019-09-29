package com.waluo

import io.kotlintest.inspectors.forAll
import io.kotlintest.matchers.numerics.shouldBeGreaterThan
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.WordSpec

class IdWorkerTest : WordSpec({

    "IdWorker" should {

        "generate an id" {
            val s = IdWorker(1)
            val id: Long = s.nextId()
            id shouldBeGreaterThan 0
        }

        "return the correct job id" {
            val s = IdWorker(1)
            s.workerId shouldBe 1
        }

        "properly mask worker id" {
            val workerId = 1L
            val worker = IdWorker(workerId)
            (1..10000).map { worker.nextId() }.forAll {
                worker.snowflake.getWorkerId(it) shouldBe workerId
            }
        }

        "properly mask timestamp"  {
            val snowflake = Snowflake(timeBits = 31, workerBits = 1)
            val epochSecond = snowflake.spec().epochSecond
            val worker = IdWorker(workerId = 1, snowflake = snowflake, timeGen = { epochSecond })
            (1..10000).map { worker.nextId() }.forAll {
                worker.snowflake.spec(it).timestamp shouldBe epochSecond
            }
        }

        "roll over sequence id" {
            val snowflake = Snowflake(timeBits = 31, workerBits = 31) // seqBits = 64 -1 -31 -31
            val worker = IdWorker(workerId = 1, snowflake = snowflake)
            (1..3).map { worker.nextId() }.forAll {
                worker.snowflake.getWorkerId(it) shouldBe 1
            }
        }

        "generate increasing ids" {
            val worker = IdWorker(1)
            var lastId = 0L
            for (i in 1..100) {
                val id = worker.nextId()
                id shouldBeGreaterThan lastId
                lastId = id
            }
        }

        "generate 1 million ids quickly" {
            val worker = IdWorker(1)
            val t = System.currentTimeMillis()
            for (i in 1..1000000) {
                worker.nextId()
            }
            val t2 = System.currentTimeMillis()
            println("generated 1000000 ids in ${t2 - t} ms, or ${1000000000.0 / (t2 - t)} ids/second")
            1 shouldBeGreaterThan 0
        }

        "sleep if we would rollover twice in the same millisecond" {
            class LimitedTimeWorker {
                val snowflake = Snowflake(timeBits = 31, workerBits = 31)
                var time = snowflake.spec().epochSecond + 1
                var queue = (0..9999).map { time }.let { it + (time + 1) }.listIterator()
                private val worker = IdWorker(0, snowflake = snowflake, timeGen = { queue.next() })
                fun nextId() = worker.nextId()
            }

            val worker = LimitedTimeWorker()

            worker.nextId() // seq 0
            worker.nextId() // seq 1, roll over
            worker.nextId() // next time

            worker.queue.hasNext() shouldBe false
        }

        "generate only unique ids" {
            val worker = IdWorker(1)
            val n = 2000000
            val distinct = (1..n).map { worker.nextId() }.distinct()
            distinct.size shouldBe n
        }

        "generate ids over 50 billion" {
            val worker = IdWorker(0)
            worker.nextId() shouldBeGreaterThan 50000000000L
        }

        "generate only unique ids, even when time goes backwards" {
            class StaticTimeWorker {
                val snowflake = Snowflake()
                var time = snowflake.spec().epochSecond + 1
                private val worker = IdWorker(0, timeGen = { time })
                fun nextId() = worker.nextId()
            }

            val worker = StaticTimeWorker()

            // reported at https://github.com/twitter/snowflake/issues/6
            // first we generate 2 ids with the same time, so that we get the sequqence to 1
            val id1 = worker.nextId()
            worker.snowflake.getDeltaSeconds(id1) shouldBe 1
            worker.snowflake.getSequence(id1) shouldBe 0
            val id2 = worker.nextId()
            worker.snowflake.getDeltaSeconds(id2) shouldBe 1
            worker.snowflake.getSequence(id2) shouldBe 1

            //then we set the time backwards
            worker.time = worker.snowflake.spec().epochSecond
            shouldThrow<IllegalArgumentException> {
                worker.nextId()
            }.message shouldBe "Clock moved backwards. Refusing to generate id for 1 seconds"

            worker.time = worker.snowflake.spec().epochSecond + 1
            val id3 = worker.nextId()
            worker.snowflake.getDeltaSeconds(id3) shouldBe 1
            worker.snowflake.getSequence(id3) shouldBe 2
        }
    }
})