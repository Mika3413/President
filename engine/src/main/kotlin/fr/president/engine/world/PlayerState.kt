package fr.president.engine.world

import fr.president.engine.time.WorldTime
import kotlinx.serialization.Serializable

@Serializable
data class GameOver(val time: WorldTime, val reason: String)

@Serializable
class PlayerState(
    val countryId: String,
    val presidentId: String,
    var termStart: WorldTime,
    var termNumber: Int = 1,
    var gameOver: GameOver? = null,
    /** Promesses de campagne et valeurs de référence au début du mandat. */
    val promises: MutableList<String> = mutableListOf(),
    val promiseBaselines: MutableMap<String, Double> = mutableMapOf(),
    var warsThisTerm: Int = 0,
    /** Étape du tutoriel guidé (-1 : terminé ou ancienne partie). Une nouvelle partie commence à 0. */
    var tourStep: Int = -1,
    /** Scénario de départ choisi. */
    var scenario: String? = null,
)
