package fr.president.engine.data

import kotlinx.serialization.Serializable

/** Niveau de simulation d'un pays : complet (jouable) ou allégé (IA). */
@Serializable
enum class DetailLevel { FULL, LIGHT }

@Serializable
data class CountryDefinition(
    val id: String,
    val name: String,
    /** Article défini (« la », « le », « l' », « les ») pour accorder le nom dans les textes. */
    val article: String = "",
    val adjective: String,
    val language: String,
    val namePool: String,
    val detail: DetailLevel,
    val population: Long,
    val capitalCityId: String? = null,
    val economy: String,
    val leader: LeaderProfile,
    val strategic: StrategicProfile,
    val institutions: InstitutionsDefinition,
    val territory: String? = null,
    val government: String? = null,
    val socialGroups: String? = null,
    val elections: String? = null,
    val energy: String? = null,
    val transport: String? = null,
    val military: String? = null,
    val reforms: String? = null,
    val promises: String? = null,
    val demography: fr.president.engine.government.DemographyDef? = null,
)

@Serializable
data class InstitutionsDefinition(
    val headOfStateTitle: String,
    val headOfGovernmentTitle: String,
    val honorificMale: String,
    val honorificFemale: String,
    val headOfGovernmentTitleFemale: String? = null,
) {
    /** Titre du chef de gouvernement accordé au genre de la personne. */
    fun headOfGovernment(female: Boolean): String = if (female) headOfGovernmentTitleFemale ?: headOfGovernmentTitle else headOfGovernmentTitle
}

/** Tendances du dirigeant généré : chaque trait est tiré dans l'intervalle [min, max]. */
@Serializable
data class LeaderProfile(
    val traitRanges: Map<String, List<Double>>,
    val ageRange: List<Int>,
    val economicLeaningRange: List<Double>,
)

@Serializable
data class StrategicProfile(
    /** Solde électrique annuel (TWh) ; négatif = besoin d'importer. */
    val electricityBalanceTWh: Double,
    val tradeWithPartnersBillions: Map<String, Double> = emptyMap(),
    val alliances: List<String> = emptyList(),
    val priorities: List<String> = emptyList(),
    val intelligenceQuality: Double,
    val militaryBudgetBillions: Double,
    val capital: CapitalDef? = null,
    /** Puissance nucléaire : dissuasion contre une agression de son territoire. */
    val nuclear: Boolean = false,
    /** Réseau électrique interconnecté avec celui du pays joueur. */
    val electricityInterconnected: Boolean = false,
    /** Forces conventionnelles (nombre d'unités générées pour les pays IA). */
    val forces: ForcesDef = ForcesDef(),
    /** Zones de pêche partagées avec la France (conflits de pêche possibles). */
    val fisheryNeighbor: Boolean = false
)

@Serializable
data class CapitalDef(val name: String, val lon: Double, val lat: Double)

@Serializable
data class ForcesDef(val land: Int = 0, val air: Int = 0, val sea: Int = 0)
