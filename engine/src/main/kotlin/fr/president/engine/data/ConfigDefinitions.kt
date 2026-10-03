package fr.president.engine.data

import fr.president.engine.time.GamePace
import kotlinx.serialization.Serializable

/** Configuration générale (data/config/game_config.json). */
@Serializable
data class GameConfig(
    val paces: List<GamePace>,
    val defaultPace: String,
    val defaultSnapshot: String,
    val simulation: SimulationSettings,
    val files: GlobalDataFiles,
) {
    fun pace(id: String): GamePace = paces.first { it.id == id }
}

@Serializable
data class SimulationSettings(
    val notificationFeedSize: Int,
    val inboxHistorySize: Int,
    val messageUniquenessAttempts: Int,
    val maxCatchUpDays: Int,
    val pollIntervalDays: Int,
    val aiDecisionIntervalDays: Int,
    val memoryRetentionDays: Int,
)

/** Fichiers communs à toutes les parties. */
@Serializable
data class GlobalDataFiles(
    val economyParameters: String,
    val infrastructureTypes: String,
    val diplomacyClauses: String,
    val lexicon: String,
    val readouts: String,
    val events: List<String>,
    val dialogue: List<String>,
    val names: Map<String, String>,
)

/** Photographie initiale du monde (data/world_snapshots/). Une partie la copie puis s'en détache. */
@Serializable
data class SnapshotDefinition(
    val id: String,
    val label: String,
    val startDate: String,
    val dataVersion: Int,
    val playableCountries: List<String>,
    val countries: List<String>,
    val initialRelations: List<InitialRelationDef> = emptyList(),
)

@Serializable
data class InitialRelationDef(
    val a: String,
    val b: String,
    val kind: String,
    val weight: Double,
    val label: String,
)
