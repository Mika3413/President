package fr.president.engine.time

import kotlinx.serialization.Serializable

/** Rythme d'une partie : combien d'heures du monde s'écoulent pendant une heure réelle. */
@Serializable
data class GamePace(
    val id: String,
    val label: String,
    val worldHoursPerRealHour: Double,
    val description: String = "",
)
