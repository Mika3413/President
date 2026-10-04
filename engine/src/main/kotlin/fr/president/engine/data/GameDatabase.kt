package fr.president.engine.data

import fr.president.engine.dialogue.DialogueTemplate
import fr.president.engine.dialogue.Lexicon
import fr.president.engine.diplomacy.DiplomacyDefinitions
import fr.president.engine.events.EventDefinition
import fr.president.engine.readout.ReadoutsFile

/** Définitions complètes chargées pour un pays. Les blocs détaillés sont absents en mode allégé. */
class CountryData(
    val definition: CountryDefinition,
    val economy: EconomySnapshot,
    val territory: TerritoryDefinition?,
    val government: GovernmentDefinition?,
    val socialGroups: SocialGroupsDefinition?,
    val elections: ElectionsDefinition?,
    val energy: EnergyFile?,
    val transport: TransportFile?,
    val military: MilitaryFile?,
    val reforms: fr.president.engine.government.ReformsFile? = null,
    val promises: fr.president.engine.government.PromisesFile? = null,
    val localActions: fr.president.engine.territory.LocalActionsFile? = null,
    val nationalActions: fr.president.engine.territory.NationalActionsFile? = null,
    val measures: fr.president.engine.crisis.MeasuresFile? = null,
    val cabinet: fr.president.engine.government.CabinetFile? = null,
    val agenda: fr.president.engine.session.AgendaRules? = null,
    val sectors: fr.president.engine.economy.SectorsFile? = null,
) {
    val id: String get() = definition.id
}

/**
 * Base de données immuable du jeu : tout ce qui vient des fichiers de données.
 * Le moteur ne contient aucune valeur propre à un pays ; tout passe par ici.
 */
class GameDatabase(
    val config: GameConfig,
    val snapshot: SnapshotDefinition,
    val countries: Map<String, CountryData>,
    val economyParameters: EconomyParameters,
    val infrastructureTypes: Map<String, InfrastructureTypeDef>,
    val events: List<EventDefinition>,
    val dialogue: Map<String, DialogueTemplate>,
    val lexicon: Lexicon,
    val diplomacy: DiplomacyDefinitions,
    val names: Map<String, NamePool>,
    val readouts: ReadoutsFile,
    val zones: fr.president.engine.military.ZoneGraph,
    val unitTypes: Map<String, UnitTypeDef>,
    val militaryParameters: MilitaryParameters,
    val help: HelpFile,
    val media: fr.president.engine.media.MediaFile? = null,
    val worldCities: List<WorldCityDef> = emptyList(),
    val intensity: fr.president.engine.events.IntensityFile? = null,
    val frequency: EventFrequency? = null,
    val eu: fr.president.engine.diplomacy.EuFile? = null,
    val scenarios: List<fr.president.engine.setup.ScenarioDef> = emptyList(),
) {
    val alliances: List<AllianceDef> get() = snapshot.alliances
    fun unitType(id: String): UnitTypeDef = unitTypes[id] ?: error("Type d'unité inconnu : $id")
    fun country(id: String): CountryData = countries[id] ?: error("Pays inconnu : $id")
    fun template(id: String): DialogueTemplate = dialogue[id] ?: error("Modèle de dialogue inconnu : $id")
    fun event(id: String): EventDefinition = events.first { it.id == id }
}
