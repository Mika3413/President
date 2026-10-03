package fr.president.engine.elections

import fr.president.engine.time.WorldTime
import kotlinx.serialization.Serializable

@Serializable
data class Candidate(
    val characterId: String,
    val familyId: String,
    val incumbent: Boolean,
    val economicPosition: Double,
    val socialPosition: Double,
    var momentum: Double = 0.0,
)

@Serializable
data class RoundResult(
    val turnout: Double,
    /** Identifiant du personnage -> part des suffrages exprimés. */
    val shares: Map<String, Double>,
)

@Serializable
data class ElectionResult(
    val time: WorldTime,
    val firstRound: RoundResult,
    val secondRound: RoundResult?,
    val winnerId: String,
    val incumbentWon: Boolean,
    /** Résultat de l'opposant par département au second tour (part du président sortant). */
    val incumbentShareByDepartment: Map<String, Double> = emptyMap(),
)

@Serializable
class ElectionState(
    var nextElection: WorldTime,
    val candidates: MutableList<Candidate> = mutableListOf(),
    var latestPoll: RoundResult? = null,
    var latestRunoffPoll: RoundResult? = null,
    var lastPollAt: WorldTime? = null,
    var pendingFirstRound: RoundResult? = null,
    val results: MutableList<ElectionResult> = mutableListOf(),
)
