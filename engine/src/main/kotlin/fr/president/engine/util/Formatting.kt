package fr.president.engine.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Mise en forme des nombres à la française (moteur et interface). */
object Formatting {
    private val symbols = DecimalFormatSymbols(Locale.FRENCH).apply { groupingSeparator = ' ' }
    private val integer = DecimalFormat("#,##0", symbols)
    private val oneDecimal = DecimalFormat("#,##0.0", symbols)
    private val twoDecimals = DecimalFormat("#,##0.00", symbols)
    private const val THOUSAND = 1000.0
    private const val MILLION = 1_000_000.0
    private const val PERCENT = 100.0

    fun amount(v: Double): String = if (v >= 10 || v == Math.floor(v)) integer.format(v) else twoDecimals.format(v)
    fun integer(v: Double): String = integer.format(v)
    fun integer(v: Long): String = integer.format(v)
    fun percent(ratio: Double): String = oneDecimal.format(ratio * PERCENT) + " %"
    fun signedPercent(ratio: Double): String = (if (ratio >= 0) "+" else "") + percent(ratio)

    /** Montant exprimé en milliards -> texte lisible (M€ ou Md€). */
    fun billions(v: Double): String = when {
        kotlin.math.abs(v) >= THOUSAND -> integer.format(v) + " Md€"
        kotlin.math.abs(v) >= 1.0 -> oneDecimal.format(v) + " Md€"
        else -> integer.format(v * THOUSAND) + " M€"
    }

    fun population(v: Long): String = when {
        v >= MILLION -> oneDecimal.format(v / MILLION) + " M hab."
        else -> integer.format(v) + " hab."
    }
}
