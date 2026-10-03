package fr.president.engine.infrastructure

import fr.president.engine.time.WorldTime
import kotlinx.serialization.Serializable

@Serializable
class InfrastructureState(
    val id: String,
    val type: String,
    /** État matériel 0..1 (1 = neuf). */
    var condition: Double,
    /** Niveau d'entretien décidé (1.0 = normal). */
    var maintenanceLevel: Double = 1.0,
    var offlineUntil: WorldTime? = null,
    var closed: Boolean = false,
    var incidents: Int = 0,
    var renovationProjectId: String? = null,
) {
    fun isOperational(now: WorldTime): Boolean = !closed && (offlineUntil?.let { now >= it } ?: true)
}
