package fr.president.engine.events

import fr.president.engine.effects.EffectSpec
import fr.president.engine.inbox.InboxMessage
import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import fr.president.engine.politics.Traits
import fr.president.engine.simulation.ScheduledAction
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.territory.ProjectState

/** Applique la réponse choisie (par le joueur ou par défaut) à un événement. */
class EventResolver(private val ctx: SimulationContext) {

    fun choose(message: InboxMessage, optionId: String, byDefault: Boolean) {
        val instance = ctx.state.events.active.firstOrNull { it.id == message.originId } ?: return
        val def = ctx.db.event(instance.definitionId)
        val option = def.message?.options?.firstOrNull { it.id == optionId } ?: return
        message.chosenOptionId = optionId
        message.answeredByDefault = byDefault
        message.read = true
        val scope = ScopeRef(def.scope, instance.scopeId, message.senderId)

        when {
            option.requestDetails -> {
                ctx.scheduler.schedule(ScheduledAction.EventDetails(ctx.now.plusDays(DETAILS_DELAY_DAYS), instance.id))
                return
            }
            option.reaskAfterDays != null -> {
                instance.reasks++
                message.senderId?.let { ctx.memory.record(it, def.id, InteractionOutcome.POSTPONED) }
                ctx.scheduler.schedule(ScheduledAction.EventReask(ctx.now.plusDays(option.reaskAfterDays), instance.id))
                return
            }
        }
        option.effects.forEach { ctx.effects.trigger(it, scope, instance.params, def.id) }
        option.project?.let { startProject(it, scope, instance, def) }
        message.senderId?.let { senderId ->
            ctx.memory.record(senderId, def.id, option.outcome)
            revealTrait(senderId)
        }
        instance.resolved = true
        ctx.state.events.active.remove(instance)
        if (byDefault) {
            ctx.notifications.post(
                def.category, Urgency.INFO,
                "Réponse automatique : ${message.subject}",
                "Faute de décision dans les délais, l'option « ${option.label} » a été appliquée par vos services.",
                message.focusId,
            )
        }
        ctx.log("events", "Réponse ${option.id} à ${def.id}${if (byDefault) " (par défaut)" else ""}")
    }

    private fun startProject(spec: ProjectSpec, scope: ScopeRef, instance: EventInstance, def: EventDefinition) {
        val cost = (spec.costParam?.let { instance.params[it] } ?: 0.0) * spec.costFactor
        val vars = EventLauncher(ctx).variables(def, scope, instance.params)
        val resolved = spec.onCompletion.mapNotNull { e ->
            ctx.effects.resolveTarget(e.target, scope)?.let { target ->
                EffectSpec(target, (e.param?.let { instance.params[it] } ?: e.amount) * e.factor, days = e.days)
            }
        }
        val project = ProjectState(
            id = ctx.state.newId("prj"),
            name = EventLauncher.substitute(spec.name, vars),
            kind = spec.kind,
            locationId = scope.id ?: "",
            startedAt = ctx.now,
            completesAt = ctx.now.plusDays(spec.durationDays),
            costBillions = cost,
            onCompletion = resolved,
        )
        ctx.state.projects.add(project)
        // Le coût est décaissé progressivement pendant toute la durée du chantier.
        if (cost > 0) ctx.effects.trigger(EffectSpec("budget.oneOff", cost, days = spec.durationDays), scope, emptyMap(), project.id)
        ctx.scheduler.schedule(ScheduledAction.ProjectCompletion(project.completesAt, project.id))
        ctx.notifications.post(NotificationCategory.PROJECTS, Urgency.INFO, "Projet lancé : ${project.name}",
            "Fin des travaux prévue dans ${spec.durationDays.toInt()} jours.", project.locationId)
    }

    /** À chaque échange, le président apprend à mieux connaître son interlocuteur. */
    private fun revealTrait(characterId: String) {
        val c = ctx.state.characters[characterId] ?: return
        val unknown = Traits.ALL.filter { it !in c.knownTraits }
        if (unknown.isNotEmpty() && ctx.rng.chance(REVEAL_CHANCE)) c.knownTraits += ctx.rng.pick(unknown)
    }

    private companion object {
        const val DETAILS_DELAY_DAYS = 1.0
        const val REVEAL_CHANCE = 0.5
    }
}
