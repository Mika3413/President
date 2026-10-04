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
    /** Affaires du gouvernement en attente d'arbitrage (initiatives, disputes, menaces...). */
    val affairs: MutableList<CabinetAffair> = mutableListOf(),
    /** Initiatives déjà proposées (on ne les repropose pas). */
    val proposedInitiatives: MutableSet<String> = mutableSetOf(),
    /** Dernière dispute de chaque type (délai avant qu'elle ne revienne). */
    val lastDisputes: MutableMap<String, fr.president.engine.time.WorldTime> = mutableMapOf(),
)
