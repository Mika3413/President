package fr.president.engine.diplomacy

import kotlinx.serialization.Serializable

@Serializable
data class DiplomacyDefinitions(
    val clauseTypes: List<ClauseTypeDef>,
    val memoryKinds: List<MemoryKindDef>,
    val evaluation: EvaluationParameters,
    val relationLabels: List<RelationLabelDef>,
) {
    fun clause(id: String): ClauseTypeDef = clauseTypes.first { it.id == id }
    fun memoryKind(id: String): MemoryKindDef? = memoryKinds.firstOrNull { it.id == id }
}

/** Type de clause négociable. [mutual] = engage les deux parties de façon symétrique. */
@Serializable
data class ClauseTypeDef(
    val id: String,
    val label: String,
    val description: String,
    val mutual: Boolean = false,
    val params: List<ClauseParamDef>,
    /** Coefficients de valorisation utilisés par l'IA. */
    val valuation: Map<String, Double> = emptyMap(),
)

@Serializable
data class ClauseParamDef(
    val id: String,
    val label: String,
    val unit: String,
    val min: Double,
    val max: Double,
    val step: Double,
    val default: Double,
)

/** Type de souvenir diplomatique : poids, demi-vie et impact sur la confiance. */
@Serializable
data class MemoryKindDef(
    val id: String,
    val positiveLabel: String,
    val negativeLabel: String,
    val halfLifeDays: Double,
    val affectsTrust: Boolean = false,
    /** Poids appliqué quand le souvenir est créé par une action standard. */
    val defaultWeight: Double = 0.0,
)

@Serializable
data class EvaluationParameters(
    val baseThreshold: Double,
    val toughnessThreshold: Double,
    val relationWeight: Double,
    val trustWeight: Double,
    val durationCautionPerYear: Double,
    val maxCounterSteps: Int,
    val counterReachableGap: Double,
    val responseDelayDaysMin: Double,
    val responseDelayDaysMax: Double,
    val perceptionNoise: Double,
    val proposalExpiryDays: Double,
    val aiProposalRelationMinimum: Double,
    /** Délai minimal avant qu'un pays relance une proposition après un refus ou un silence. */
    val aiProposalCooldownDays: Double,
)

@Serializable
data class RelationLabelDef(val upTo: Double, val label: String)
