package fr.president.engine.data

import kotlinx.serialization.Serializable

@Serializable
data class GovernmentDefinition(
    val ministries: List<MinistryDef>,
    val maxHighPriorities: Int,
    val candidatesPerMinistry: Int,
    val effectivenessWeights: EffectivenessWeights,
    val parliament: ParliamentDef,
    val localTitles: LocalTitles,
    val ministerDrift: MinisterDriftDef,
    /** Qualité initiale des services publics par domaine (0..1). */
    val initialServiceQuality: Map<String, Double>,
)

@Serializable
data class MinistryDef(
    val id: String,
    val title: String,
    val shortTitle: String,
    /** Domaine de service public influencé (santé, éducation...). */
    val domain: String? = null,
    val isPrimeMinister: Boolean = false,
)

@Serializable
data class EffectivenessWeights(
    val competence: Double,
    val management: Double,
    val experience: Double,
    val loyalty: Double,
    val highPriorityBonus: Double,
    val lowPriorityPenalty: Double,
)

@Serializable
data class ParliamentDef(
    val baseSupport: Double,
    val approvalWeight: Double,
    val primeMinisterDistanceWeight: Double,
    val openingBonus: Double,
    val voteNoise: Double,
    val passThreshold: Double,
    val forcePassApprovalCost: Double,
    val forcePassSupportCost: Double,
    val taxVoteDelayDays: Int,
    val spendingVoteDelayDays: Int,
)

@Serializable
data class LocalTitles(
    val prefect: String,
    val regionPresident: String,
    val departmentPresident: String,
    val mayor: String,
    val prefectFemale: String? = null,
    val regionPresidentFemale: String? = null,
    val departmentPresidentFemale: String? = null,
    val mayorFemale: String? = null,
) {
    fun prefect(female: Boolean) = if (female) prefectFemale ?: prefect else prefect
    fun regionPresident(female: Boolean) = if (female) regionPresidentFemale ?: regionPresident else regionPresident
    fun departmentPresident(female: Boolean) = if (female) departmentPresidentFemale ?: departmentPresident else departmentPresident
    fun mayor(female: Boolean) = if (female) mayorFemale ?: mayor else mayor
}

@Serializable
data class MinisterDriftDef(
    val loyaltyApprovalWeight: Double,
    val loyaltyAdjustmentMonthly: Double,
    val resignationLoyaltyThreshold: Double,
    val experienceGainMonthly: Double,
)
