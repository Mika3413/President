package fr.president.engine.data

import kotlinx.serialization.Serializable

@Serializable
data class MilitaryFile(
    val budgetSpendingItem: String,
    val bases: List<MilitaryBaseDef>,
    val units: List<MilitaryUnitDef>,
)

@Serializable
data class MilitaryBaseDef(
    val id: String,
    val name: String,
    val branch: String,
    val lon: Double,
    val lat: Double,
    val department: String,
    val capacityUnits: Int,
)

@Serializable
data class MilitaryUnitDef(
    val id: String,
    val name: String,
    val branch: String,
    val type: String,
    val baseId: String,
    val personnel: Int,
    val equipment: Map<String, Int> = emptyMap(),
    val readiness: Double,
    val morale: Double,
    val ammunition: Double,
    val fuel: Double,
)
