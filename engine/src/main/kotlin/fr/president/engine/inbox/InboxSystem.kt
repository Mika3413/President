package fr.president.engine.inbox

import fr.president.engine.diplomacy.DiplomacyService
import fr.president.engine.events.EventResolver
import fr.president.engine.simulation.Cadence
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.simulation.SimulationSystem

/**
 * Le temps ne s'arrête pas : un message sans réponse à l'échéance reçoit
 * la réponse par défaut, appliquée par les services de l'État.
 */
class InboxSystem : SimulationSystem {
    override val name = "inbox"
    override val cadence = Cadence.HOURLY

    override fun run(ctx: SimulationContext) {
        val expired = ctx.state.inbox.messages.filter { m ->
            m.awaitingAnswer && m.deadline != null && ctx.now >= m.deadline && m.defaultOptionId != null
        }
        expired.forEach { answer(ctx, it, it.defaultOptionId!!, byDefault = true) }
    }

    companion object {
        fun answer(ctx: SimulationContext, message: InboxMessage, optionId: String, byDefault: Boolean) {
            when (message.origin) {
                MessageOrigin.EVENT -> EventResolver(ctx).choose(message, optionId, byDefault)
                MessageOrigin.PROPOSAL, MessageOrigin.DIPLOMATIC_RESPONSE ->
                    DiplomacyService(ctx).answerMessage(message, optionId, byDefault)
                MessageOrigin.ALLIANCE_CALL -> fr.president.engine.military.WarService(ctx).answerAllianceCall(message, optionId)
                MessageOrigin.ULTIMATUM -> fr.president.engine.diplomacy.UltimatumService(ctx).answerPlayer(message, optionId)
                MessageOrigin.INFO, MessageOrigin.CONVERSATION -> {
                    message.chosenOptionId = optionId
                    message.read = true
                }
            }
        }
    }
}
