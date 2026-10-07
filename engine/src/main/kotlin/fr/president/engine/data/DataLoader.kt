package fr.president.engine.data

import fr.president.engine.dialogue.DialogueFile
import fr.president.engine.dialogue.Lexicon
import fr.president.engine.diplomacy.DiplomacyDefinitions
import fr.president.engine.events.EventFile
import fr.president.engine.readout.ReadoutsFile
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.serializer

/** Charge et valide tous les fichiers de données à partir d'un [DataSource]. */
class DataLoader(private val source: DataSource) {

    fun load(snapshotId: String? = null): GameDatabase {
        val config = read<GameConfig>(CONFIG_PATH)
        val snapshot = read<SnapshotDefinition>("$SNAPSHOT_DIR/${snapshotId ?: config.defaultSnapshot}.json")
        val countries = snapshot.countries.map { loadCountry(it) }.associateBy { it.id }
        val files = config.files
        val unitTypes = read<UnitTypesFile>(files.unitTypes)
        val db = GameDatabase(
            config = config,
            snapshot = snapshot,
            countries = countries,
            economyParameters = read(files.economyParameters),
            infrastructureTypes = read<InfrastructureTypesFile>(files.infrastructureTypes).types.associateBy { it.id },
            events = fr.president.engine.events.ResponsePacks.merge(
                files.events.flatMap { read<EventFile>(it).events },
                files.eventResponses?.let { read<fr.president.engine.events.ResponsesFile>(it) },
            ),
            dialogue = files.dialogue.flatMap { read<DialogueFile>(it).templates }.associateBy { it.id },
            lexicon = read<Lexicon>(files.lexicon),
            diplomacy = read<DiplomacyDefinitions>(files.diplomacyClauses),
            names = files.names.mapValues { (_, path) -> read<NamePool>(path) },
            readouts = read<ReadoutsFile>(files.readouts),
            zones = fr.president.engine.military.ZoneGraph(read<ZonesFile>(files.zones)),
            unitTypes = unitTypes.types.associateBy { it.id },
            militaryParameters = unitTypes.parameters,
            help = read(files.help),
            media = files.media?.let { read(it) },
            worldCities = files.worldCities?.let { read<WorldCitiesFile>(it).cities }.orEmpty(),
            intensity = files.eventIntensity?.let { read(it) },
            frequency = files.eventFrequency?.let { read(it) },
            eu = files.eu?.let { read(it) },
            scenarios = files.scenarios?.let { read<fr.president.engine.setup.ScenariosFile>(it).scenarios }.orEmpty(),
            trade = files.trade?.let { read(it) },
            defense = files.defense?.let { read(it) },
            intel = files.intel?.let { read(it) },
            unrest = files.unrest?.let { read(it) },
            consequences = files.consequences?.let { read(it) },
            worldEvents = files.worldEvents?.let { read(it) },
            un = files.un?.let { read(it) },
            warfare = files.warfare?.let { read(it) },
            moments = files.moments?.let { read(it) },
            majorEvents = files.majorEvents?.let { read(it) },
        )
        DataValidator.validate(db)
        return db
    }

    private fun loadCountry(path: String): CountryData {
        val def = read<CountryDefinition>(path)
        return CountryData(
            definition = def,
            economy = read(def.economy),
            territory = def.territory?.let { read(it) },
            government = def.government?.let { read(it) },
            socialGroups = def.socialGroups?.let { read(it) },
            elections = def.elections?.let { read(it) },
            energy = def.energy?.let { read(it) },
            transport = def.transport?.let { read(it) },
            military = def.military?.let { read(it) },
            reforms = def.reforms?.let { read(it) },
            promises = def.promises?.let { read(it) },
            localActions = def.localActions?.let { read(it) },
            nationalActions = def.nationalActions?.let { read(it) },
            measures = def.measures?.let { read(it) },
            cabinet = def.cabinet?.let { read(it) },
            agenda = def.agenda?.let { read(it) },
            sectors = def.sectors?.let { read(it) },
            careers = def.careers?.let { read<fr.president.engine.politics.CareersFile>(it).careers }.orEmpty(),
            laws = def.laws?.let { read(it) },
            actors = def.actors?.let { read(it) },
            fiscal = def.fiscal?.let { read(it) },
            legislation = def.legislation?.let { read(it) },
        )
    }

    private inline fun <reified T> read(path: String): T = decode(serializer<T>(), path)

    private fun <T> decode(strategy: DeserializationStrategy<T>, path: String): T {
        val text = try {
            source.read("$DATA_ROOT/$path")
        } catch (e: Exception) {
            throw DataException("Fichier de données introuvable : $path", e)
        }
        return try {
            GameJson.data.decodeFromString(strategy, text)
        } catch (e: Exception) {
            throw DataException("Fichier de données invalide : $path (${e.message})", e)
        }
    }

    companion object {
        const val DATA_ROOT = "data"
        const val CONFIG_PATH = "config/game_config.json"
        const val SNAPSHOT_DIR = "world_snapshots"
    }
}

class DataException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
