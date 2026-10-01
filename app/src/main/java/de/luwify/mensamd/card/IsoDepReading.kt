package de.luwify.mensamd.card

import android.nfc.Tag
import android.nfc.tech.IsoDep
import java.io.IOException

/** Reads a card from an NFC tag; shared by reader mode and the popup. Blocking: call off the main thread. Never logs. */
fun readCardFromTag(tag: Tag): CardReadResult = IsoDep.get(tag)?.readCard() ?: CardReadResult.Unsupported

internal fun IsoDep.readCard(): CardReadResult =
    try {
        connect()
        CardReaders.read(IsoDepSession(this))
    } catch (e: IOException) {
        CardReadResult.Failed
    } catch (e: RuntimeException) {
        CardReadResult.Failed
    } finally {
        try {
            close()
        } catch (e: IOException) {
            // already gone
        }
    }

private class IsoDepSession(private val isoDep: IsoDep) : DesfireSession {
    override fun transceive(command: ByteArray): ByteArray = isoDep.transceive(command)

    override fun reconnect() {
        isoDep.close()
        isoDep.connect()
    }
}
