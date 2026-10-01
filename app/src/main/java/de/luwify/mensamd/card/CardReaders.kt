/*
 * CardReaders.kt
 *
 * Copyright (C) 2014 Jakob Wenzel
 * Copyright (C) 2026 Luwify
 *
 * Authors:
 * Jakob Wenzel <jakobwenzel92@gmail.com>
 *
 * Translated from Java to Kotlin and modified for Mensa MD on 2026-10-01.
 * Derived from de.yazo_games.mensaguthaben.cardreader.Readers, ICardReader and ValueData
 * (MensaGuthaben). Changes: the Android tag handling (IsoDep connect/close) moved to
 * NfcCardScanner, this file only drives a [DesfireSession]; the result is a sealed type;
 * Android logging removed (card data is never logged).
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

/**
 * Amounts as the card stores them: thousandths of a euro (the original app divides by 1000).
 * [lastTransaction] is null if the card type doesn't have it.
 */
data class CardBalance(val value: Int, val lastTransaction: Int?)

sealed interface CardReadResult {
    data class Success(val balance: CardBalance) : CardReadResult

    /** Not a card we can read: not DESFire, none of the known applications, or the file needs a key. */
    data object Unsupported : CardReadResult

    /** The connection broke (card moved away). */
    data object Failed : CardReadResult
}

interface CardReader {
    /**
     * Throws [DesfireIoException] on communication errors only. A card that doesn't hold the
     * required data gives null.
     */
    fun readCard(card: DesfireProtocol): CardBalance?
}

/** Runs [block]; protocol-level errors (app/file missing, permission denied, odd reply) mean null, transport errors are rethrown. */
internal inline fun orNullUnlessIo(block: () -> CardBalance?): CardBalance? =
    try {
        block()
    } catch (e: DesfireIoException) {
        throw e
    } catch (e: DesfireException) {
        null
    }

object CardReaders {
    // Same order as the original.
    private val readers: List<CardReader> = listOf(MagnaCartaReader(), IntercardReader())

    /** Reads a freshly connected card. */
    fun read(session: DesfireSession): CardReadResult {
        val card = DesfireProtocol(session)
        return try {
            // Android has a bug on devices with a Broadcom NFC chip (issue 58773): the first command
            // after connecting gets a reply that is too short. The workaround is to issue a dummy
            // command, ignore that, and reconnect.
            try {
                card.selectApp(0)
            } catch (e: DesfireShortResponseException) {
                // workaround was needed
            }
            session.reconnect()

            readers.firstNotNullOfOrNull { it.readCard(card) }
                ?.let { CardReadResult.Success(it) }
                ?: CardReadResult.Unsupported
        } catch (e: DesfireIoException) {
            CardReadResult.Failed
        } catch (e: java.io.IOException) {
            CardReadResult.Failed
        } catch (e: DesfireException) {
            CardReadResult.Unsupported // not a DESFire card at all
        }
    }
}
