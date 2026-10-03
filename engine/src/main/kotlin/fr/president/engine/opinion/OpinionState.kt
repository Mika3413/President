package fr.president.engine.opinion

import fr.president.engine.util.History
import kotlinx.serialization.Serializable

@Serializable
class GroupOpinion(
    /** Opinion de fond, qui suit lentement le bilan. */
    var approval: Double,
    /** Choc temporaire lié aux décisions et événements récents. */
    var shock: Double = 0.0,
) {
    val effective: Double get() = (approval + shock).coerceIn(0.0, 1.0)
}

@Serializable
class OpinionState(
    val groups: MutableMap<String, GroupOpinion> = mutableMapOf(),
    var honeymoon: Double = 0.0,
    var nationalApproval: Double = 0.5,
    /** Derniers scores des facteurs, pour expliquer l'opinion au joueur. */
    val factorScores: MutableMap<String, Double> = mutableMapOf(),
    /** Valeurs des variables au début de la partie (facteurs relatifs). */
    val startValues: MutableMap<String, Double> = mutableMapOf(),
    val history: History = History(),
)
