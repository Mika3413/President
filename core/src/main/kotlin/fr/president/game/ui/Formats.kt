package fr.president.game.ui

import fr.president.engine.time.WorldTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formats de date de l'interface. */
object Formats {
    private val DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRENCH)
    private val DATE_TIME = DateTimeFormatter.ofPattern("EEE d MMM yyyy · HH'h'mm", Locale.FRENCH)
    /** Une décimale, virgule française : « 1,5 ». */
    fun decimal(v: Double): String = String.format(java.util.Locale.FRENCH, "%.1f", v)
    fun date(t: WorldTime): String = t.toDateTime().format(DATE)
    fun dateTime(t: WorldTime): String = t.toDateTime().format(DATE_TIME)
}
