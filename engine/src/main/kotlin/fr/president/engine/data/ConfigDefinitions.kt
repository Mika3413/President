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
    val zones: String,
    val unitTypes: String,
    val help: String,
    /** Presse et sondages (facultatif). */
    val media: String? = null,
    /** Villes des pays étrangers (facultatif). */
    val worldCities: String? = null,
    /** Ampleur variable des événements (facultatif). */
    val eventIntensity: String? = null,
)

@kotlinx.serialization.Serializable
data class WorldCityDef(
    val id: String,
    val name: String,
    val country: String,
    val lat: Double,
    val lon: Double,
    val populationMillions: Double,
    val capital: Boolean = false,
    val rank: Int = 3,
)

@kotlinx.serialization.Serializable
data class WorldCitiesFile(val cities: List<WorldCityDef>)

@Serializable
data class HelpFile(val tutorial: List<TutorialDef>, val glossary: List<GlossaryDef>)

@Serializable
data class TutorialDef(val delayHours: Int, val subject: String, val body: String)

@Serializable
data class GlossaryDef(val term: String, val definition: String)

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
    val alliances: List<AllianceDef> = emptyList(),
)

/** Alliance (OTAN, UE...) : une alliance défensive engage ses membres en cas d'agression. */
@Serializable
data class AllianceDef(val id: String, val name: String, val defensive: Boolean, val members: List<String>)

@Serializable
data class InitialRelationDef(
    val a: String,
    val b: String,
    val kind: String,
    val weight: Double,
    val label: String,
)
