package de.luwify.mensamd.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HostFallbackTest {
    private val main = "https://main.example/"
    private val backup = "https://backup.example/"
    private val third = "https://third.example/"
    private val minute = 60_000L
    private var now = 1_000_000_000_000L
    private val fallback = HostFallback(listOf(main, backup, third), { now })

    @Test
    fun mainFirst_byDefault() {
        assertEquals(listOf(main, backup, third), fallback.order())
    }

    @Test
    fun workingFallback_isTriedFirst_forAnHour() {
        fallback.succeeded(backup)
        assertEquals(listOf(backup, main, third), fallback.order())
        now += 59 * minute
        assertEquals(listOf(backup, main, third), fallback.order())
    }

    @Test
    fun afterAnHour_theMainHostIsTriedFirstAgain() {
        fallback.succeeded(backup)
        now += 60 * minute
        assertEquals(listOf(main, backup, third), fallback.order())
    }

    @Test
    fun usingTheRememberedFallback_doesNotExtendIt() {
        fallback.succeeded(backup)
        now += 50 * minute
        fallback.succeeded(backup)
        now += 10 * minute
        assertEquals(main, fallback.order().first())
    }

    @Test
    fun mainWorkingAgain_forgetsTheFallback() {
        fallback.succeeded(backup)
        fallback.succeeded(main)
        assertEquals(listOf(main, backup, third), fallback.order())
    }

    @Test
    fun anotherFallbackWorking_replacesTheRememberedOne() {
        fallback.succeeded(backup)
        now += 30 * minute
        fallback.succeeded(third)
        assertEquals(listOf(third, main, backup), fallback.order())
        now += 59 * minute
        assertEquals(third, fallback.order().first())
    }

    @Test
    fun clockMovedBackwards_endsTheMemory() {
        fallback.succeeded(backup)
        now -= minute
        assertEquals(main, fallback.order().first())
    }

    @Test
    fun singleHost_alwaysThatHost() {
        val only = HostFallback(listOf(main), { now })
        only.succeeded(main)
        assertEquals(listOf(main), only.order())
    }

    private class Memory(var stored: RememberedHost? = null) : HostMemory {
        var writes = 0
        override fun read() = stored
        override fun write(host: RememberedHost?) {
            stored = host
            writes++
        }
    }

    private fun restarted(memory: HostMemory) = HostFallback(listOf(main, backup, third), { now }, memory = memory)

    @Test
    fun aColdStartKeepsTheFallback_forTheRestOfTheHour() {
        val memory = Memory()
        restarted(memory).succeeded(backup)
        now += 30 * minute
        assertEquals(listOf(backup, main, third), restarted(memory).order())
        now += 30 * minute // the hour counts from the first success, across starts
        assertEquals(listOf(main, backup, third), restarted(memory).order())
    }

    @Test
    fun usingTheRestoredFallbackDoesNotExtendIt() {
        val memory = Memory()
        restarted(memory).succeeded(backup)
        now += 59 * minute
        restarted(memory).succeeded(backup)
        assertEquals(1, memory.writes)
        now += 2 * minute
        assertEquals(listOf(main, backup, third), restarted(memory).order())
    }

    @Test
    fun theMainHostWorking_forgetsItForTheNextStartToo() {
        val memory = Memory()
        restarted(memory).succeeded(backup)
        restarted(memory).succeeded(main)
        assertEquals(null, memory.stored)
        assertEquals(listOf(main, backup, third), restarted(memory).order())
    }

    @Test
    fun aRemovedHostOrAnEarlierClockIsIgnored() {
        assertEquals(listOf(main, backup, third), restarted(Memory(RememberedHost("https://gone.example/", now))).order())
        assertEquals(listOf(main, backup, third), restarted(Memory(RememberedHost(backup, now + minute))).order())
    }
}
