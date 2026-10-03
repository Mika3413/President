package fr.president.engine.military

import kotlinx.serialization.Serializable

@Serializable
class MilitaryState(
    val units: MutableMap<String, UnitState> = mutableMapOf(),
    var overallReadiness: Double = 0.6,
)

/** Unité cohérente (brigade, escadron, groupe naval) : jamais de soldat individuel. */
@Serializable
class UnitState(
    val id: String,
    val branch: String,
    val type: String,
    var baseId: String,
    var personnel: Int,
    val equipment: MutableMap<String, Int>,
    var readiness: Double,
    var morale: Double,
    var ammunition: Double,
    var fuel: Double,
    var fatigue: Double = 0.2,
)
