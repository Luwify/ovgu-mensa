/*
 * DesfireProtocol.kt
 *
 * Copyright (C) 2011 Eric Butler
 * Copyright (C) 2026 Luwify
 *
 * Authors:
 * Eric Butler <eric@codebutler.com>
 *
 * Translated from Java to Kotlin and modified for Mensa MD on 2026-10-01.
 * Derived from com.codebutler.farebot.card.desfire.DesfireProtocol (and byteArrayToInt from
 * com.codebutler.farebot.Utils) as bundled in MensaGuthaben by Jakob Wenzel.
 * Changes: no Android imports (the card is reached through [DesfireSession], so it can be
 * unit-tested); only the commands this app needs; a response shorter than the status bytes, a
 * transport failure and an endless 91 AF chain each get their own exception instead of an
 * ArrayIndexOutOfBoundsException / unbounded loop.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.luwify.mensamd.card

import java.io.ByteArrayOutputStream
import java.io.IOException

/** What the protocol needs from the card connection; backed by IsoDep on the phone, a fake in tests. */
interface DesfireSession {
    /** Sends one APDU and returns the raw reply. Throws [IOException] if the card is gone. */
    fun transceive(command: ByteArray): ByteArray

    /** Closes and reopens the connection (the Broadcom workaround needs this). */
    fun reconnect()
}

open class DesfireException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The transport failed (card moved away): not a statement about the card type. */
class DesfireIoException(cause: IOException) : DesfireException("Transport error", cause)

/** The reply was shorter than the two status bytes. Broadcom chips do this on the first command. */
class DesfireShortResponseException : DesfireException("Response too short")

/** DESFire native commands wrapped in ISO 7816 APDUs (CLA 0x90), with 91 AF frame reassembly. */
class DesfireProtocol(private val session: DesfireSession) {
    fun selectApp(appId: Int) {
        send(SELECT_APPLICATION, byteArrayOf((appId shr 16).toByte(), (appId shr 8).toByte(), appId.toByte()))
    }

    fun getFileSettings(fileNo: Int): DesfireFileSettings =
        DesfireFileSettings.create(send(GET_FILE_SETTINGS, byteArrayOf(fileNo.toByte())))

    fun readFile(fileNo: Int): ByteArray =
        send(READ_DATA, byteArrayOf(fileNo.toByte(), 0, 0, 0, 0, 0, 0))

    /** The value file's current value, a little-endian int32 on the card. */
    fun readValue(fileNo: Int): Int {
        val buf = send(READ_VALUE, byteArrayOf(fileNo.toByte()))
        if (buf.size != 4) throw DesfireException("Invalid value length")
        return littleEndianInt(buf, 0)
    }

    private fun send(command: Byte, parameters: ByteArray? = null): ByteArray {
        val output = ByteArrayOutputStream()
        var recv = transceive(wrapMessage(command, parameters))
        var frames = 0
        while (true) {
            if (recv.size < 2) throw DesfireShortResponseException()
            if (recv[recv.size - 2] != 0x91.toByte()) throw DesfireException("Invalid response")
            output.write(recv, 0, recv.size - 2)

            when (val status = recv[recv.size - 1]) {
                OPERATION_OK -> break
                ADDITIONAL_FRAME -> {
                    if (++frames > MAX_FRAMES) throw DesfireException("Too many frames")
                    recv = transceive(wrapMessage(GET_ADDITIONAL_FRAME, null))
                }
                PERMISSION_DENIED -> throw DesfireException("Permission denied")
                else -> throw DesfireException("Unknown status code: " + Integer.toHexString(status.toInt() and 0xFF))
            }
        }
        return output.toByteArray()
    }

    private fun transceive(apdu: ByteArray): ByteArray =
        try {
            session.transceive(apdu)
        } catch (e: IOException) {
            throw DesfireIoException(e)
        }

    private fun wrapMessage(command: Byte, parameters: ByteArray?): ByteArray {
        val stream = ByteArrayOutputStream()
        stream.write(0x90)
        stream.write(command.toInt())
        stream.write(0x00)
        stream.write(0x00)
        if (parameters != null) {
            stream.write(parameters.size)
            stream.write(parameters)
        }
        stream.write(0x00)
        return stream.toByteArray()
    }

    private companion object {
        const val GET_ADDITIONAL_FRAME: Byte = 0xAF.toByte()
        const val SELECT_APPLICATION: Byte = 0x5A
        const val READ_DATA: Byte = 0xBD.toByte()
        const val READ_VALUE: Byte = 0x6C
        const val GET_FILE_SETTINGS: Byte = 0xF5.toByte()

        const val OPERATION_OK: Byte = 0x00
        const val PERMISSION_DENIED: Byte = 0x9D.toByte()
        const val ADDITIONAL_FRAME: Byte = 0xAF.toByte()

        /** A real read needs one or two; the cap only stops a misbehaving card from looping forever. */
        const val MAX_FRAMES = 64
    }
}

/** Reads 4 bytes at [offset] as a little-endian int32. */
internal fun littleEndianInt(b: ByteArray, offset: Int): Int =
    (b[offset].toInt() and 0xFF) or
        ((b[offset + 1].toInt() and 0xFF) shl 8) or
        ((b[offset + 2].toInt() and 0xFF) shl 16) or
        ((b[offset + 3].toInt() and 0xFF) shl 24)
