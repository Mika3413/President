package fr.president.engine.session

import fr.president.engine.data.GameDatabase
import fr.president.engine.government.PolicyService
import fr.president.engine.inbox.InboxSystem
import fr.president.engine.readout.CharacterReadout
import fr.president.engine.readout.LocalReadouts
import fr.president.engine.readout.NationalReadouts
import fr.president.engine.save.SaveFile
import fr.president.engine.setup.NewGameFactory
import fr.president.engine.setup.NewGameOptions
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.simulation.Simulator
import fr.president.engine.world.WorldState

/**
 * Façade d'une partie en cours, utilisée par toutes les interfaces (libGDX, Android, tests).
 * Elle ne contient pas de règles : elle délègue aux commandes et aux systèmes.
 */
class GameSession(
    val db: GameDatabase,
    val state: WorldState,
    private val realClock: () -> Long = System::currentTimeMillis,
) {
    val context = SimulationContext(state, db)
    private val simulator = Simulator(context)

    val government = GovernmentCommands(context)
    val infrastructure = InfrastructureCommands(context)
    val localActions = LocalActionCommands(context)
    val diplomacy = DiplomacyCommands(context)
    val policy = PolicyService(context)
    val national = NationalReadouts(context)
    val local = LocalReadouts(context)
    val advisor = fr.president.engine.readout.AdvisorReadout(context)
    val characters = CharacterReadout(context)
    val military = MilitaryCommands(context)
    val militaryReadouts = fr.president.engine.readout.MilitaryReadouts(context)
    val parliament = fr.president.engine.government.ParliamentService(context)
    val conversations = fr.president.engine.dialogue.ConversationService(context)
    val senate = fr.president.engine.government.SenateService(context)

    init {
        fr.president.engine.military.MilitarySetup(context).ensure()
        parliament.ensure()
        fr.president.engine.elections.LocalElectionService(context).ensure()
        senate.ensure()
    }

    val isGameOver: Boolean get() = state.player.gameOver != null

    /**
     * Instant réel (ms UTC) du prochain moment marquant déjà planifié (vote, scrutin, réponse
     * diplomatique, événement...). Sert à réveiller l'application fermée au bon moment.
     */
    fun nextKeyMomentRealMillis(): Long? {
        val next = state.scheduler.actions
            .filter { it !is fr.president.engine.simulation.ScheduledAction.Tutorial && it.at > state.time }
            .minOfOrNull { it.at } ?: return null
        return state.meta.clock.realMillisAt(next)
    }

    /** Rattrape le temps réel écoulé : à appeler au retour du joueur puis régulièrement. */
    fun advanceToNow(): Simulator.Report = simulator.advanceTo(state.meta.clock.worldTimeAt(realClock()))

    fun answer(messageId: String, optionId: String) {
        val message = state.inbox.messages.first { it.id == messageId }
        if (!message.awaitingAnswer) return
        InboxSystem.answer(context, message, optionId, byDefault = false)
    }

    fun markRead(messageId: String) {
        state.inbox.messages.firstOrNull { it.id == messageId }?.read = true
    }

    fun toSaveFile(gameVersion: String): SaveFile = SaveFile(SaveFile.CURRENT_FORMAT, realClock(), gameVersion, state)

    companion object {
        fun newGame(db: GameDatabase, options: NewGameOptions, realClock: () -> Long = System::currentTimeMillis) =
            GameSession(db, NewGameFactory(db).create(options), realClock)

        fun fromSave(db: GameDatabase, save: SaveFile, realClock: () -> Long = System::currentTimeMillis) =
            GameSession(db, save.state, realClock)
    }
}
