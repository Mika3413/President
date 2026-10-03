package fr.president.engine.data

import kotlinx.serialization.Serializable

@Serializable
data class ElectionsDefinition(
    val termYears: Int,
    val secondRoundGapDays: Int,
    val families: List<PoliticalFamilyDef>,
    val incumbentRecordWeight: Double,
    val affinityWeight: Double,
    val choiceTemperature: Double,
    val turnoutDiscontentWeight: Double,
    val turnoutEnthusiasmWeight: Double,
    val pollNoise: Double,
    val scandalPenalty: Double,
)

@Serializable
data class PoliticalFamilyDef(
    val id: String,
    val name: String,
    val economicPosition: Double,
    val socialPosition: Double,
    val baseStrength: Double,
)

/** Réserves de noms pour le générateur de personnages fictifs. */
@Serializable
data class NamePool(
    val maleFirstNames: List<String>,
    val femaleFirstNames: List<String>,
    val lastNames: List<String>,
)
