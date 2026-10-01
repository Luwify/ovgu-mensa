package de.luwify.mensamd.card

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.Locale

/** All card bytes below are made up (no real card data). Amounts are thousandths of a euro. */
class CardReadersTest {
    private fun hex(s: String): ByteArray = s.filter { !it.isWhitespace() }.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun ByteArray.toHex() = joinToString(" ") { "%02X".format(it) }

    /** Replies per exact command, in order; the last reply repeats. Anything unscripted answers "illegal command". */
    private class FakeSession(private val replies: Map<String, List<String>>, private val failOn: String? = null) : DesfireSession {
        val sent = mutableListOf<String>()
        var reconnects = 0

        override fun transceive(command: ByteArray): ByteArray {
            val key = command.joinToString(" ") { "%02X".format(it) }
            sent += key
            if (key == failOn) throw IOException("tag lost")
            val queue = replies[key] ?: return byteArrayOf(0x91.toByte(), 0x1C)
            val n = sent.count { it == key } - 1
            return queue[minOf(n, queue.lastIndex)].filter { !it.isWhitespace() }
                .chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }

        override fun reconnect() { reconnects++ }
    }

    private val selectPicc = "90 5A 00 00 03 00 00 00 00"
    private val selectIntercard = "90 5A 00 00 03 5F 84 15 00"
    private val selectMagna = "90 5A 00 00 03 F0 80 F3 00"
    private val settingsFile1 = "90 F5 00 00 01 01 00"
    private val valueFile1 = "90 6C 00 00 01 01 00"
    private val readFile2 = "90 BD 00 00 07 02 00 00 00 00 00 00 00"
    private val ok = "91 00"
    private val appNotFound = "91 A0"

    // Value file settings: type 02, comm 00, access EEEE, lower 0, upper 0x000F4240, value (last debit), flag 00.
    private fun valueSettings(lastLe: String) = "02 00 EE EE 00000000 40420F00 $lastLe 00 $ok"

    private val intercard = mapOf(
        selectPicc to listOf(ok),
        selectMagna to listOf(appNotFound),
        selectIntercard to listOf(ok),
        settingsFile1 to listOf(valueSettings("C4090000")), // last debit 2500
        valueFile1 to listOf("34300000 $ok"), // 12340 = 12.34 EUR
    )

    @Test fun intercardBalanceAndLastDebit() {
        val session = FakeSession(intercard)
        val result = CardReaders.read(session)
        assertEquals(CardReadResult.Success(CardBalance(12340, 2500)), result)
        assertEquals(1, session.reconnects)
        assertEquals(listOf(selectPicc, selectMagna, selectIntercard, settingsFile1, valueFile1), session.sent)
    }

    @Test fun valueIsSignedLittleEndian() {
        val result = CardReaders.read(FakeSession(intercard + (valueFile1 to listOf("FFFFFFFF $ok"))))
        assertEquals(-1, (result as CardReadResult.Success).balance.value)
    }

    @Test fun magnaCartaBalanceHasNoLastTransaction() {
        // bytes 6..7 = 0x01F4 = 500, times 10 = 5000 = 5.00 EUR
        val card = mapOf(
            selectPicc to listOf(ok),
            selectMagna to listOf(ok),
            readFile2 to listOf("00 00 00 00 00 00 01 F4 00 00 $ok"),
        )
        val result = CardReaders.read(FakeSession(card))
        assertEquals(CardReadResult.Success(CardBalance(5000, null)), result)
    }

    @Test fun additionalFramesAreReassembled() {
        val card = mapOf(
            selectPicc to listOf(ok),
            selectMagna to listOf(ok),
            readFile2 to listOf("00 00 00 00 00 91 AF"),
            "90 AF 00 00 00" to listOf("00 01 F4 91 00"),
        )
        val session = FakeSession(card)
        val result = CardReaders.read(session)
        assertEquals(CardReadResult.Success(CardBalance(5000, null)), result)
        assertTrue("90 AF 00 00 00" in session.sent)
    }

    @Test fun endlessAdditionalFramesGiveUp() {
        val card = mapOf(
            selectPicc to listOf(ok),
            selectMagna to listOf(ok),
            readFile2 to listOf("00 91 AF"),
            "90 AF 00 00 00" to listOf("00 91 AF"),
            selectIntercard to listOf(appNotFound),
        )
        assertEquals(CardReadResult.Unsupported, CardReaders.read(FakeSession(card)))
    }

    @Test fun broadcomShortReplyToFirstCommandIsIgnoredAndReconnects() {
        val session = FakeSession(intercard + (selectPicc to listOf("00")))
        val result = CardReaders.read(session)
        assertTrue(result is CardReadResult.Success)
        assertEquals(1, session.reconnects)
    }

    @Test fun noKnownApplicationIsUnsupported() {
        val card = mapOf(selectPicc to listOf(ok), selectMagna to listOf(appNotFound), selectIntercard to listOf(appNotFound))
        assertEquals(CardReadResult.Unsupported, CardReaders.read(FakeSession(card)))
    }

    @Test fun intercardFileThatIsNotAValueFileIsUnsupported() {
        val card = intercard + (settingsFile1 to listOf("00 00 EE EE 10 00 00 $ok"))
        assertEquals(CardReadResult.Unsupported, CardReaders.read(FakeSession(card)))
    }

    @Test fun permissionDeniedIsUnsupported() {
        val card = intercard + (valueFile1 to listOf("91 9D"))
        assertEquals(CardReadResult.Unsupported, CardReaders.read(FakeSession(card)))
    }

    @Test fun truncatedReplyDataIsUnsupportedNotACrash() {
        assertEquals(CardReadResult.Unsupported, CardReaders.read(FakeSession(intercard + (settingsFile1 to listOf("02 00 EE EE 00 $ok")))))
        assertEquals(CardReadResult.Unsupported, CardReaders.read(FakeSession(intercard + (valueFile1 to listOf("3430 $ok")))))
        val shortMagna = mapOf(selectPicc to listOf(ok), selectMagna to listOf(ok), readFile2 to listOf("00 01 $ok"), selectIntercard to listOf(appNotFound))
        assertEquals(CardReadResult.Unsupported, CardReaders.read(FakeSession(shortMagna)))
    }

    @Test fun anotherIsoDepCardIsUnsupported() {
        // A non-DESFire ISO-DEP card answers every command with an ISO 7816 error (6D 00).
        val everything = listOf(selectPicc, selectMagna, selectIntercard).associateWith { listOf("6D 00") }
        assertEquals(CardReadResult.Unsupported, CardReaders.read(FakeSession(everything)))
    }

    @Test fun lostCardDuringReadFailsInsteadOfUnsupported() {
        assertEquals(CardReadResult.Failed, CardReaders.read(FakeSession(intercard, failOn = valueFile1)))
        assertEquals(CardReadResult.Failed, CardReaders.read(FakeSession(intercard, failOn = selectMagna)))
    }

    @Test fun apdusAreWrappedLikeTheOriginal() {
        assertEquals("90 5A 00 00 03 5F 84 15 00", selectIntercard)
        val session = FakeSession(intercard)
        DesfireProtocol(session).selectApp(0x5F8415)
        assertEquals(listOf(selectIntercard), session.sent)
    }

    @Test fun amountsAreFormattedInEuros() {
        fun fmt(v: Int, l: Locale) = formatCardAmount(v, l).replace(' ', ' ')
        assertEquals("12,34 €", fmt(12340, Locale.GERMANY))
        assertEquals("€12.34", fmt(12340, Locale.US))
        assertEquals("0,01 €", fmt(5, Locale.GERMANY)) // 5/1000 rounds half up
        assertEquals("0,00 €", fmt(4, Locale.GERMANY))
        assertFalse(fmt(2500, Locale.GERMANY).contains("2500"))
        assertEquals("2,50 €", fmt(2500, Locale.GERMANY))
    }

    @Test fun readResultsMapToScreenStates() {
        val b = CardBalance(1, null)
        assertEquals(CardScanState.Success(b), CardReadResult.Success(b).toState())
        assertEquals(CardScanState.Unsupported, CardReadResult.Unsupported.toState())
        assertEquals(CardScanState.Error, CardReadResult.Failed.toState())
    }
}
