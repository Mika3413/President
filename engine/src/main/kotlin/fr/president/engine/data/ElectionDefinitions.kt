package fr.president.engine.data

import kotlinx.serialization.Serializable

@Serializable
data class ElectionsDefinition(
    val termYears: Int,
    val secondRoundGapDays: Int,
    val families: List<PoliticalFamilyDef>,
    val incumbentRecordWeight: Double,
    /** Prime au sortant : notoriété et stature présidentielle. */
    val incumbentBonus: Double = 0.0,
    val affinityWeight: Double,
    val choiceTemperature: Double,
    val turnoutDiscontentWeight: Double,
    val turnoutEnthusiasmWeight: Double,
    val pollNoise: Double,
    val scandalPenalty: Double,
    val legislative: LegislativeDef? = null,
    val referendum: ReferendumDef? = null,
    val local: LocalElectionsDef? = null,
)

@Serializable
enum class LocalLevel { REGION, DEPARTMENT, CITY }

/** Élections locales : renouvellement des exécutifs régionaux, départementaux et municipaux. */
@Serializable
data class LocalElectionsDef(
    val kinds: List<LocalElectionKind>,
    /** Sensibilité du vote local à l'opinion locale envers le président. */
    val swing: Double,
    val noise: Double,
    /** Avantage d'un élu sortant qui se représente. */
    val incumbentBonus: Double,
    val incumbentRerunChance: Double,
    /** Effet d'un mauvais (ou bon) résultat sur la discipline des députés. */
    val parliamentImpact: Double,
)

@Serializable
data class LocalElectionKind(
    val id: String,
    val label: String,
    val level: LocalLevel,
    val termYears: Int,
    /** Date du prochain scrutin après le début de partie (ISO-8601). */
    val firstDate: String,
)

/**
 * Élections législatives et vie de l'Assemblée. Le mode de scrutin majoritaire est
 * résumé par une amplification des écarts de voix ([seatAmplification] > 1).
 */
@Serializable
data class LegislativeDef(
    val seats: Int,
    val termYears: Int,
    /** Écart entre l'élection présidentielle et les législatives qui la suivent. */
    val daysAfterPresidential: Int,
    val seatAmplification: Double,
    /** Prime au camp présidentiel quand les législatives suivent de peu la présidentielle. */
    val coattailBonus: Double,
    val coattailWindowDays: Int,
    /** Part de la prime présidentielle au sortant conservée par son parti aux législatives. */
    val incumbentBonusShare: Double,
    /** Campagne entre l'annonce d'une dissolution et le scrutin. */
    val dissolutionCampaignDays: Int,
    /** Délai minimal entre deux législatives avant une nouvelle dissolution. */
    val minDaysBetweenDissolutions: Int,
    /** Distance idéologique au-delà de laquelle un groupe ne soutient plus le gouvernement. */
    val allyRange: Double,
    val approvalWeight: Double,
    /** Coût de cohésion d'un Premier ministre éloigné du président. */
    val cohesionWeight: Double,
    /** Sous ce soutien, un passage en force déclenche une motion de censure à l'issue incertaine. */
    val censureThreshold: Double,
    val censureDelayDays: Int,
    val censureNoise: Double,
    /** Motion de censure spontanée de l'opposition quand le soutien tombe très bas. */
    val spontaneousCensureSupport: Double,
    val spontaneousCensureChanceMonthly: Double,
    val governmentFallApprovalCost: Double,
)

/** Référendum : le président soumet directement une réforme aux électeurs. */
@Serializable
data class ReferendumDef(
    val campaignDays: Int,
    val minDaysBetween: Int,
    /** Poids de l'opinion envers le président dans le vote (le référendum devient plébiscite). */
    val approvalWeight: Double,
    /** Sensibilité du vote d'un groupe aux effets immédiats de la réforme sur lui. */
    val interestWeight: Double,
    val noise: Double,
    val victorySupportBonus: Double,
    val defeatApprovalCost: Double,
    /** Durée pendant laquelle une réforme rejetée ne peut être représentée. */
    val defeatLockDays: Int,
)

@Serializable
data class PoliticalFamilyDef(
    val id: String,
    val name: String,
    val economicPosition: Double,
    val socialPosition: Double,
    val baseStrength: Double,
    /** Couleur d'affichage (hémicycle, résultats), en hexadécimal RVB. */
    val color: String = "888888",
)

/** Réserves de noms pour le générateur de personnages fictifs. */
@Serializable
data class NamePool(
    val maleFirstNames: List<String>,
    val femaleFirstNames: List<String>,
    val lastNames: List<String>,
)
