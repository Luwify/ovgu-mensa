/*
 * MagnaCartaReader.kt
 *
 * Copyright (C) 2014 Jakob Wenzel
 * Copyright (C) 2026 Luwify
 *
 * Authors:
 * Jakob Wenzel <jakobwenzel92@gmail.com>
 *
 * Translated from Java to Kotlin and modified for Mensa MD on 2026-10-01.
 * Derived from de.yazo_games.mensaguthaben.cardreader.MagnaCartaReader (MensaGuthaben).
 * Changes: Android logging removed (card data is never logged); a transport error is passed on
 * instead of being read as "unsupported card"; too-short file data is "unsupported" instead of
 * an IndexOutOfBoundsException.
 *
 * NOT VERIFIED ON A REAL CARD: ported from the original, covered only by unit tests on a fake
 * session. The author has no MagnaCarta card.
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

/** MagnaCarta: application 0xF080F3, file 2 is a plain data file with the balance at bytes 6..7 (big endian). No last transaction. */
class MagnaCartaReader : CardReader {
    // The original doesn't use getFileSettings or the file list here: the card answers those with
    // an authentication error.
    override fun readCard(card: DesfireProtocol): CardBalance? =
        orNullUnlessIo {
            card.selectApp(APP_ID)
            val data = card.readFile(FILE_ID)
            if (data.size < 8) return@orNullUnlessIo null
            val low = data[7].toInt() and 0xFF
            val hi = data[6].toInt() and 0xFF
            CardBalance((hi shl 8 or low) * 10, null)
        }

    private companion object {
        const val APP_ID = 0xF080F3
        const val FILE_ID = 2
    }
}
