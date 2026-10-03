package fr.president.engine.events

import fr.president.engine.simulation.SimulationContext

/** Relances (report) et compléments d'information demandés par le joueur. */
class EventFollowUps(private val ctx: SimulationContext) {

    fun reask(instanceId: String) = redeliver(instanceId, details = false)
    fun details(instanceId: String) = redeliver(instanceId, details = true)

    private fun redeliver(instanceId: String, details: Boolean) {
        val instance = ctx.state.events.active.firstOrNull { it.id == instanceId } ?: return
        val def = ctx.db.event(instance.definitionId)
        val previous = ctx.state.inbox.messages.firstOrNull { it.id == instance.messageId }
        val scope = ScopeRef(def.scope, instance.scopeId, previous?.senderId)
        val launcher = EventLauncher(ctx)
        launcher.deliverMessage(def, instance, scope, launcher.variables(def, scope, instance.params), details)
    }
}
