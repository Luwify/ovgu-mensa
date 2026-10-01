package de.luwify.mensamd.card

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** Formats a card amount (thousandths of a euro) as euros, rounded half-up to cents like the original app. */
fun formatCardAmount(thousandths: Int, locale: Locale): String {
    val euros = BigDecimal.valueOf(thousandths.toLong(), 3).setScale(2, RoundingMode.HALF_UP)
    val format = NumberFormat.getCurrencyInstance(locale)
    format.currency = Currency.getInstance("EUR")
    return format.format(euros)
}
