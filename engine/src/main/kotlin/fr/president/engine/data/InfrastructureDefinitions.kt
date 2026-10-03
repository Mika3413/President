package fr.president.engine.data

import kotlinx.serialization.Serializable

@Serializable
data class InfrastructureTypeDef(
    val id: String,
    val label: String,
    val category: String,
    val producesElectricity: Boolean = false,
    /** Perte d'état annuelle avec un entretien normal. */
    val degradationPerYear: Double,
    /** Nombre d'années d'entretien manquant avant qu'un état critique devienne probable. */
    val renovationCostPerMW: Double = 0.0,
    val renovationBaseCostMillions: Double,
    val renovationDays: Int,
    val canClose: Boolean = false,
)

@Serializable
data class InfrastructureTypesFile(val types: List<InfrastructureTypeDef>)

@Serializable
data class InfrastructureDef(
    val id: String,
    val type: String,
    val name: String,
    val lon: Double,
    val lat: Double,
    val department: String,
    val capacityMW: Double = 0.0,
    val capacityFactor: Double = 0.0,
    val employees: Int,
    val maintenanceCostMillions: Double,
    val initialCondition: Double,
    val commissionedYear: Int,
    val description: String = "",
)

@Serializable
data class GenerationAggregateDef(
    val id: String,
    val label: String,
    val capacityMW: Double,
    val capacityFactor: Double,
)

@Serializable
data class NetworkDef(
    val id: String,
    val kind: String,
    val name: String,
    val cityIds: List<String>,
)

@Serializable
data class EnergyFile(
    val electricityDemandTWh: Double,
    val items: List<InfrastructureDef>,
    val nationalGeneration: List<GenerationAggregateDef>,
)

@Serializable
data class TransportFile(
    val items: List<InfrastructureDef>,
    val networks: List<NetworkDef>,
)
