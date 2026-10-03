package fr.president.engine.simulation

import fr.president.engine.data.CountryData
import fr.president.engine.data.GameDatabase
import fr.president.engine.dialogue.InteractionMemory
import fr.president.engine.dialogue.MessageComposer
import fr.president.engine.effects.EffectApplier
import fr.president.engine.events.VariableResolver
import fr.president.engine.infrastructure.InfrastructureCatalog
import fr.president.engine.notifications.NotificationCenter
import fr.president.engine.politics.CharacterGenerator
import fr.president.engine.util.DebugLog
import fr.president.engine.util.GameRandom
import fr.president.engine.time.WorldTime
import fr.president.engine.world.WorldState

/**
 * Point d'accès partagé par les systèmes : état, données, services transverses.
 * Ne contient aucune règle de simulation.
 */
class SimulationContext(
    val state: WorldState,
    val db: GameDatabase,
    val debug: DebugLog = DebugLog(),
) {
    val playerData: CountryData = db.country(state.player.countryId)
    val catalog = InfrastructureCatalog(playerData)
    val notifications = NotificationCenter(this)
    val variables = VariableResolver(this)
    val effects = EffectApplier(this)
    val memory = InteractionMemory(this)
    val messages = MessageComposer(this)
    val characters = CharacterGenerator(db)
    val scheduler = Scheduler(state.scheduler)

    val now: WorldTime get() = state.time
    val rng: GameRandom get() = state.rng

    fun log(category: String, message: String) = debug.log(state.time.seconds, category, message)
}
