package fr.president.engine.time

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Instant du monde simulé, en secondes depuis l'époque Unix du calendrier du jeu.
 * Toutes les durées de gameplay (projets, guerres, élections...) s'expriment en temps du monde.
 */
@Serializable
@JvmInline
value class WorldTime(val seconds: Long) : Comparable<WorldTime> {

    override fun compareTo(other: WorldTime): Int = seconds.compareTo(other.seconds)

    fun plusHours(hours: Long): WorldTime = WorldTime(seconds + hours * SECONDS_PER_HOUR)
    fun plusDays(days: Long): WorldTime = WorldTime(seconds + days * SECONDS_PER_DAY)
    fun plusDays(days: Double): WorldTime = WorldTime(seconds + (days * SECONDS_PER_DAY).toLong())
    fun plusYears(years: Int): WorldTime =
        fromDateTime(toDateTime().plusYears(years.toLong()))

    fun toDateTime(): LocalDateTime = LocalDateTime.ofEpochSecond(seconds, 0, ZoneOffset.UTC)

    /** Index de jour absolu, utile pour détecter les changements de jour. */
    val dayIndex: Long get() = Math.floorDiv(seconds, SECONDS_PER_DAY)
    val hourIndex: Long get() = Math.floorDiv(seconds, SECONDS_PER_HOUR)
    val monthIndex: Int get() = toDateTime().let { it.year * MONTHS_PER_YEAR + it.monthValue - 1 }
    val month: Int get() = toDateTime().monthValue

    fun daysUntil(other: WorldTime): Double = (other.seconds - seconds).toDouble() / SECONDS_PER_DAY

    companion object {
        const val SECONDS_PER_HOUR = 3600L
        const val SECONDS_PER_DAY = 86_400L
        const val MONTHS_PER_YEAR = 12
        const val DAYS_PER_YEAR = 365.25

        fun parse(isoInstant: String): WorldTime = WorldTime(Instant.parse(isoInstant).epochSecond)
        fun fromDateTime(dt: LocalDateTime): WorldTime = WorldTime(dt.toEpochSecond(ZoneOffset.UTC))
    }
}
