package fr.president.engine.diplomacy

import fr.president.engine.time.WorldTime
import kotlinx.serialization.Serializable

@Serializable
data class DiplomaticMemory(
    val kind: String,
    val weight: Double,
    val time: WorldTime,
    /** Précision contextuelle (ex. « accord énergétique »). */
    val detail: String = "",
)

/** Ce qu'un pays retient d'un autre (relation orientée : observateur -> cible). */
@Serializable
class RelationState(val memories: MutableList<DiplomaticMemory> = mutableListOf())

@Serializable
data class Clause(
    val type: String,
    /** Pays qui fournit la prestation (ignoré pour les clauses mutuelles). */
    val giver: String,
    val params: Map<String, Double>,
)

@Serializable
enum class ProposalStatus { PENDING, ACCEPTED, REFUSED, COUNTERED, EXPIRED, WITHDRAWN }

@Serializable
class Proposal(
    val id: String,
    val from: String,
    val to: String,
    val clauses: List<Clause>,
    val durationYears: Int,
    val createdAt: WorldTime,
    var status: ProposalStatus = ProposalStatus.PENDING,
    val counterOfId: String? = null,
    var responseDue: WorldTime? = null,
    val reasons: MutableList<String> = mutableListOf(),
)

@Serializable
class Agreement(
    val id: String,
    val parties: List<String>,
    val clauses: List<Clause>,
    val signedAt: WorldTime,
    val expiresAt: WorldTime,
    var active: Boolean = true,
    var brokenBy: String? = null,
)

@Serializable
class DiplomacyState(
    val relations: MutableMap<String, RelationState> = mutableMapOf(),
    val proposals: MutableList<Proposal> = mutableListOf(),
    val agreements: MutableList<Agreement> = mutableListOf(),
    val sanctions: MutableList<Sanction> = mutableListOf(),
) {
    fun relation(observer: String, target: String): RelationState =
        relations.getOrPut(key(observer, target)) { RelationState() }

    companion object {
        fun key(observer: String, target: String) = "$observer>$target"
    }
}
