package fr.president.engine.government

import kotlinx.serialization.Serializable

@Serializable
enum class Priority(val label: String) { LOW("Faible"), NORMAL("Normale"), HIGH("Haute") }

@Serializable
class GovernmentState(
    var primeMinisterId: String? = null,
    /** Ministère -> identifiant du ministre. */
    val ministers: MutableMap<String, String> = mutableMapOf(),
    val priorities: MutableMap<String, Priority> = mutableMapOf(),
    /** Ministère -> personnalités disponibles pour une nomination. */
    val candidates: MutableMap<String, MutableList<String>> = mutableMapOf(),
    var parliamentSupport: Double = 0.5,
)
