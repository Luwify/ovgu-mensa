/*
 * IntercardReader.kt
 *
 * Copyright (C) 2014 Jakob Wenzel
 * Copyright (C) 2026 Luwify
 *
 * Authors:
 * Jakob Wenzel <jakobwenzel92@gmail.com>
 *
 * Translated from Java to Kotlin and modified for Mensa MD on 2026-10-01.
 * Derived from de.yazo_games.mensaguthaben.cardreader.IntercardReader (MensaGuthaben).
 * Changes: Android logging removed (card data is never logged); a transport error is passed on
 * instead of being read as "unsupported card".
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

/** Intercard: application 0x5F8415, file 1 is a value file; the balance is GET_VALUE, the last debit its limited credit value. */
class IntercardReader : CardReader {
    override fun readCard(card: DesfireProtocol): CardBalance? =
        orNullUnlessIo {
            card.selectApp(APP_ID)
            val settings = card.getFileSettings(FILE_ID)
            if (settings !is DesfireFileSettings.Value) return@orNullUnlessIo null
            CardBalance(card.readValue(FILE_ID), settings.value)
        }

    private companion object {
        const val APP_ID = 0x5F8415
        const val FILE_ID = 1
    }
}
