package fr.president.engine.data

import kotlinx.serialization.Serializable

@Serializable
data class TerritoryDefinition(
    val regions: List<RegionDef>,
    val departments: List<DepartmentDef>,
    val cities: List<CityDef>,
)

/** Caractéristiques socio-économiques locales (valeurs par défaut de région ou propres au département). */
@Serializable
data class LocalProfileDef(
    val unemployment: Double? = null,
    val incomeIndex: Double? = null,
    val urbanShare: Double? = null,
    val seniorShare: Double? = null,
    /** Orientation politique dominante du territoire, de -1 (gauche) à +1 (droite). */
    val politicalLeaning: Double? = null,
    /** Accès aux soins (1 = moyenne nationale). */
    val healthAccess: Double? = null,
    /** Délinquance (1 = moyenne nationale). */
    val crime: Double? = null,
    /** Part de l'industrie dans l'emploi local. */
    val industryShare: Double? = null,
    /** Part de l'agriculture dans l'emploi local. */
    val agricultureShare: Double? = null,
    /** Pollution de l'air (1 = moyenne nationale). */
    val pollution: Double? = null,
)

@Serializable
data class RegionDef(
    val code: String,
    val name: String,
    val capitalCityId: String,
    val defaults: LocalProfileDef,
)

@Serializable
data class DepartmentDef(
    val code: String,
    val name: String,
    /** Article défini (« le », « la », « l' », « les », vide pour Paris). */
    val article: String = "le",
    /** Département d'outre-mer (affiché en médaillon). */
    val overseas: Boolean = false,
    val region: String,
    val population: Long,
    val profile: LocalProfileDef = LocalProfileDef(),
)

@Serializable
data class CityDef(
    val id: String,
    val name: String,
    val department: String,
    val lon: Double,
    val lat: Double,
    val population: Long,
    val urbanAreaPopulation: Long,
    /** 1 = métropole, 2 = grande ville, 3 = ville moyenne : sert au niveau de détail de la carte. */
    val rank: Int,
)
