package fr.president.engine.territory

import kotlinx.serialization.Serializable

@Serializable
class TerritoryState(
    val regions: MutableMap<String, RegionState> = mutableMapOf(),
    val departments: MutableMap<String, DepartmentState> = mutableMapOf(),
    val cities: MutableMap<String, CityState> = mutableMapOf(),
)

@Serializable
class RegionState(
    val code: String,
    var presidentId: String? = null,
    var prefectId: String? = null,
    var approval: Double = 0.5,
)

@Serializable
class DepartmentState(
    val code: String,
    val region: String,
    var population: Long,
    /** Écart de chômage local par rapport à la moyenne nationale (points). */
    var unemploymentOffset: Double,
    var unemployment: Double,
    var incomeIndex: Double,
    val urbanShare: Double,
    val seniorShare: Double,
    /** Orientation politique dominante (-1 gauche, +1 droite). */
    val politicalLeaning: Double = 0.0,
    var approval: Double = 0.5,
    /** Choc d'opinion local (décroît avec le temps). */
    var localShock: Double = 0.0,
    var presidentId: String? = null,
)

@Serializable
class CityState(
    val id: String,
    val department: String,
    var population: Long,
    var mayorId: String? = null,
    var satisfaction: Double = 0.5,
)
