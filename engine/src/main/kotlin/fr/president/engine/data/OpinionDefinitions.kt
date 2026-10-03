package fr.president.engine.data

import kotlinx.serialization.Serializable

@Serializable
data class SocialGroupsDefinition(
    val partitions: List<PartitionDef>,
    val groups: List<SocialGroupDef>,
    val factors: List<OpinionFactorDef>,
    val honeymoonBonus: Double,
    val honeymoonDecayMonthly: Double,
    val adjustmentMonthly: Double,
    val shockDecayMonthly: Double,
    val localShockDecayMonthly: Double,
    val localUnemploymentWeight: Double,
    /** Poids de la proximité politique entre le président et le territoire. */
    val localLeaningWeight: Double = 0.0,
)

/** Une partition découpe toute la population (âge, statut, revenus, habitat). */
@Serializable
data class PartitionDef(val id: String, val label: String)

@Serializable
data class SocialGroupDef(
    val id: String,
    val label: String,
    val partition: String,
    val populationShare: Double,
    val baseApproval: Double,
    val economicLeaning: Double,
    val socialLeaning: Double,
    val baseTurnout: Double,
    /** Poids de chaque facteur d'opinion pour ce groupe. */
    val sensitivities: Map<String, Double>,
    /**
     * Attribut départemental déterminant la présence locale du groupe
     * (urbanShare, ruralShare, seniorShare, nonSeniorShare, incomeIndex).
     */
    val localAttribute: String? = null,
    val localElasticity: Double = 1.0,
)

/**
 * Facteur d'opinion : transforme une variable de simulation en score [-1, 1].
 * score = (neutral - valeur) / scale si [lowerIsBetter], sinon (valeur - neutral) / scale.
 */
@Serializable
data class OpinionFactorDef(
    val id: String,
    val label: String,
    val variable: String,
    val neutral: Double,
    val scale: Double,
    val lowerIsBetter: Boolean = false,
    /** Si vrai, la valeur neutre est la valeur initiale de la partie et non [neutral]. */
    val relativeToStart: Boolean = false,
)
