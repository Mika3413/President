package fr.president.engine.government

import fr.president.engine.time.WorldTime
import kotlinx.serialization.Serializable

@Serializable
enum class PolicyKind { TAX_RATE, SPENDING }

@Serializable
enum class PolicyStatus { PENDING_VOTE, ADOPTED, REJECTED, FORCED }

/** Mesure budgétaire soumise au Parlement (procédure simplifiée, conséquences réalistes). */
@Serializable
class PolicyProposal(
    val id: String,
    val kind: PolicyKind,
    val itemId: String,
    val oldValue: Double,
    val newValue: Double,
    val submittedAt: WorldTime,
    val voteAt: WorldTime,
    var status: PolicyStatus = PolicyStatus.PENDING_VOTE,
    var supportAtVote: Double? = null,
)

@Serializable
class PolicyState(val proposals: MutableList<PolicyProposal> = mutableListOf())
