package fr.president.engine.elections

import fr.president.engine.time.WorldTime
import kotlinx.serialization.Serializable

@Serializable
data class LocalElectionResult(
    val time: WorldTime,
    val kindId: String,
    val contested: Int,
    /** Nombre d'exécutifs remportés par le camp présidentiel. */
    val won: Int,
    /** Territoires (codes ou identifiants de ville) qui ont changé de camp. */
    val gains: List<String> = emptyList(),
    val losses: List<String> = emptyList(),
)

@Serializable
class LocalElectionState(
    /** Type de scrutin -> date du prochain. */
    val next: MutableMap<String, WorldTime> = mutableMapOf(),
    val results: MutableList<LocalElectionResult> = mutableListOf(),
)
