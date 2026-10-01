/*
 * DesfireFileSettings.kt
 *
 * Copyright (C) 2011 Eric Butler
 * Copyright (C) 2026 Luwify
 *
 * Authors:
 * Eric Butler <eric@codebutler.com>
 *
 * Translated from Java to Kotlin and modified for Mensa MD on 2026-10-01.
 * Derived from com.codebutler.farebot.card.desfire.DesfireFileSettings.
 * Changes: only value files are parsed (the only kind this app reads); every other file type is
 * just reported as "other"; the Parcelable support was dropped.
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

/** Reply of GET_FILE_SETTINGS (without the status bytes). */
sealed interface DesfireFileSettings {
    /**
     * A value file. [value] is the "limited credit value" field, which Intercard cards use to
     * hold the last debit (same reading as the original MensaGuthaben app).
     */
    data class Value(val lowerLimit: Int, val upperLimit: Int, val value: Int, val limitedCreditEnabled: Byte) : DesfireFileSettings

    /** Standard, backup or record file: not interesting here. */
    data class Other(val fileType: Byte) : DesfireFileSettings

    companion object {
        private const val VALUE_FILE: Byte = 0x02

        // type, comm setting, 2 bytes access rights, then lower limit, upper limit, value (4 bytes each), flag.
        private const val VALUE_SETTINGS_SIZE = 4 + 4 + 4 + 4 + 1

        fun create(data: ByteArray): DesfireFileSettings {
            if (data.isEmpty()) throw DesfireException("Empty file settings")
            if (data[0] != VALUE_FILE) return Other(data[0])
            if (data.size < VALUE_SETTINGS_SIZE) throw DesfireException("Truncated value file settings")
            return Value(
                lowerLimit = littleEndianInt(data, 4),
                upperLimit = littleEndianInt(data, 8),
                value = littleEndianInt(data, 12),
                limitedCreditEnabled = data[16],
            )
        }
    }
}
