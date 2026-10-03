package fr.president.engine.government

import fr.president.engine.time.WorldTime
import kotlinx.serialization.Serializable

@Serializable
data class LegislativeResult(
    val time: WorldTime,
    /** Famille politique -> part des suffrages. */
    val votes: Map<String, Double>,
    /** Famille politique -> sièges obtenus. */
    val seats: Map<String, Int>,
    val turnout: Double,
    val afterDissolution: Boolean,
)

@Serializable
data class ReferendumResult(
    val time: WorldTime,
    val reformId: String,
    val yesShare: Double,
    val turnout: Double,
)

/** Composition de l'Assemblée et calendrier parlementaire (législatives, censure, référendums). */
@Serializable
class ParliamentState(
    /** Famille politique -> nombre de sièges. Vide tant que l'Assemblée n'est pas constituée. */
    val seats: MutableMap<String, Int> = mutableMapOf(),
    var nextLegislative: WorldTime? = null,
    var lastLegislative: WorldTime? = null,
    var dissolutionPending: Boolean = false,
    var censurePending: Boolean = false,
    val legislativeResults: MutableList<LegislativeResult> = mutableListOf(),
    /** Réforme soumise à référendum, en campagne. */
    var referendumReform: String? = null,
    var referendumAt: WorldTime? = null,
    var lastReferendum: WorldTime? = null,
    val referendumResults: MutableList<ReferendumResult> = mutableListOf(),
    /** Réforme -> date jusqu'à laquelle elle ne peut être représentée après un « non ». */
    val lockedReforms: MutableMap<String, WorldTime> = mutableMapOf(),
    var censuresSurvived: Int = 0,
    var governmentsFallen: Int = 0,
    /** Famille politique -> sièges au Sénat. */
    val senateSeats: MutableMap<String, Int> = mutableMapOf(),
    var nextSenateRenewal: WorldTime? = null,
    val senateResults: MutableList<SenateResult> = mutableListOf(),
    var nextEuropean: WorldTime? = null,
    val europeanResults: MutableList<EuropeanResult> = mutableListOf(),
)

@Serializable
data class SenateResult(val time: WorldTime, val seats: Map<String, Int>, val gained: Int)

@Serializable
data class EuropeanResult(val time: WorldTime, val votes: Map<String, Double>, val seats: Map<String, Int>, val turnout: Double)
