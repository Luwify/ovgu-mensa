package de.luwify.mensamd.stats

import java.math.BigDecimal
import java.math.RoundingMode

/** Euro amounts typed by the user. Pure. */
object Amounts {
    private val PATTERN = Regex("""^\d{1,4}([.,]\d{1,2})?$""")

    /** "10", "10,5", "10.50", " 3,10 € " -> mills (thousandths of a euro); null if not a positive amount up to 9999.99 €. */
    fun parseEuro(text: String): Int? {
        val clean = text.replace("€", "").replace(' ', ' ').trim().replace(" ", "")
        if (!PATTERN.matches(clean)) return null
        val mills = BigDecimal(clean.replace(',', '.')).setScale(2, RoundingMode.UNNECESSARY).movePointRight(3).toInt()
        return mills.takeIf { it > 0 }
    }

    /** Mills to the text a field starts with: "5,60" (de) / "5.60" (en); whole euros without decimals. */
    fun toInput(mills: Int, decimalComma: Boolean): String {
        val euros = BigDecimal.valueOf(mills.toLong(), 3).setScale(2, RoundingMode.HALF_UP)
        val text = if (euros.stripTrailingZeros().scale() <= 0) euros.toBigInteger().toString() else euros.toPlainString()
        return if (decimalComma) text.replace('.', ',') else text
    }
}
