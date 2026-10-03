package fr.president.engine.time

import kotlinx.serialization.Serializable

/**
 * Horloge persistante : relie le temps réel UTC au temps du monde.
 * Le rythme est verrouillé à la création de la partie et ne change jamais ensuite.
 * Si l'horloge système recule, le monde ne recule pas : il attend simplement.
 */
@Serializable
data class WorldClock(
    val paceId: String,
    val worldHoursPerRealHour: Double,
    val anchorRealUtcMillis: Long,
    val anchorWorld: WorldTime,
) {
    fun worldTimeAt(realUtcMillis: Long): WorldTime {
        val elapsedRealMillis = (realUtcMillis - anchorRealUtcMillis).coerceAtLeast(0L)
        val elapsedWorldSeconds = elapsedRealMillis / MILLIS_PER_SECOND * worldHoursPerRealHour
        return WorldTime(anchorWorld.seconds + elapsedWorldSeconds.toLong())
    }

    /** Durée réelle (ms) correspondant à une durée du monde, pour planifier des notifications. */
    fun realMillisFor(worldSeconds: Long): Long =
        (worldSeconds / worldHoursPerRealHour * MILLIS_PER_SECOND).toLong()

    /** Instant réel (ms UTC) où le monde atteindra [world]. */
    fun realMillisAt(world: WorldTime): Long =
        anchorRealUtcMillis + realMillisFor(world.seconds - anchorWorld.seconds)

    private companion object {
        const val MILLIS_PER_SECOND = 1000.0
    }
}
