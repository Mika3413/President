package fr.president.engine.government

import fr.president.engine.time.WorldTime
import kotlinx.serialization.Serializable

@Serializable
enum class PolicyKind { TAX_RATE, SPENDING, REFORM, LAW, FISCAL, BUDGET_BILL, LAW_BILL }

@Serializable
enum class PolicyStatus { PENDING_VOTE, ADOPTED, REJECTED, FORCED, PENDING_CENSURE, PENDING_REFERENDUM }

/** Mesure budgétaire soumise au Parlement (procédure simplifiée, conséquences réalistes). */
@Serializable
class PolicyProposal(
    val id: String,
    val kind: PolicyKind,
    val itemId: String,
    val oldValue: Double,
    var newValue: Double,
    val submittedAt: WorldTime,
    val voteAt: WorldTime,
    var status: PolicyStatus = PolicyStatus.PENDING_VOTE,
    var supportAtVote: Double? = null,
    /** Soutien supplémentaire obtenu par les amendements et tractations. */
    var supportBonus: Double = 0.0,
    /** Portée du texte après amendements (1 = texte initial). */
    var effectScale: Double = 1.0,
    /** Amendements déjà négociés (identifiants). */
    val amendments: MutableList<String> = mutableListOf(),
    /** Titre d'un texte (loi de finances, projet de loi). */
    val title: String = "",
    /** Changements contenus dans un texte (loi de finances ou projet de loi). */
    val changes: MutableList<fr.president.engine.legislation.LeverChange> = mutableListOf(),
)

@Serializable
class PolicyState(
    val proposals: MutableList<PolicyProposal> = mutableListOf(),
    val adoptedReforms: MutableMap<String, fr.president.engine.time.WorldTime> = mutableMapOf(),
)
