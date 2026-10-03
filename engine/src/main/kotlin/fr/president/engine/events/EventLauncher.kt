package fr.president.engine.events

import fr.president.engine.dialogue.DialogueContextBuilder
import fr.president.engine.inbox.InboxMessage
import fr.president.engine.inbox.MessageOption
import fr.president.engine.inbox.MessageOrigin
import fr.president.engine.notifications.Urgency
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.util.Formatting
import kotlin.math.roundToLong

/** Instancie un événement : paramètres, effets immédiats, brève, notification et message. */
class EventLauncher(private val ctx: SimulationContext) {
    private val senders = SenderResolver(ctx)

    fun launch(def: EventDefinition, scope: ScopeRef): EventInstance {
        val params = drawParams(def, scope)
        val instance = EventInstance(ctx.state.newId("evt"), def.id, scope.id, params, ctx.now)
        ctx.state.events.active.add(instance)
        ctx.state.events.lastFired[def.id] = ctx.now
        scope.id?.let { ctx.state.events.lastFiredScope["${def.id}:$it"] = ctx.now }
        def.immediateEffects.forEach { ctx.effects.trigger(it, scope, params, def.id) }

        val vars = variables(def, scope, params)
        val headline = substitute(def.headline, vars)
        val focus = focusOf(scope)
        ctx.notifications.news(def.category, headline, focus)
        if (def.message != null) {
            deliverMessage(def, instance, scope, vars, details = false)
        } else {
            instance.resolved = true
        }
        if (def.urgency != Urgency.INFO || def.message == null) {
            ctx.notifications.post(def.category, def.urgency, headline, substitute(def.notificationText, vars), focus)
        }
        ctx.log("events", "Événement ${def.id} (${scope.type} ${scope.id ?: "national"}) params=$params")
        return instance
    }

    /** Envoie (ou renvoie) le message associé à une instance d'événement. */
    fun deliverMessage(def: EventDefinition, instance: EventInstance, scope: ScopeRef, vars: Map<String, String>, details: Boolean) {
        val m = def.message ?: return
        val sender = senders.resolve(m.sender, m.ministry, scope)
        val builder = DialogueContextBuilder(ctx).sender(sender.character, sender.title)
        vars.forEach { (k, v) -> builder.variable(k, v) }
        if (instance.reasks > 0) builder.tag("followup:reask")
        if ("city" in vars) builder.tag("has:city")
        if (def.urgency == Urgency.URGENT) builder.tag("urgency:high")
        val composed = ctx.messages.compose(m.template, builder.build())
        var body = composed.body
        if (details && m.detailsTemplate != null) {
            body = ctx.messages.compose(m.detailsTemplate, builder.tag("details").build()).body + "\n\n" + body
        }
        val options = m.options.filter { !(details && it.requestDetails) }
            .map { MessageOption(it.id, substitute(it.label, vars), substitute(it.hint, vars)) }
        val message = InboxMessage(
            id = ctx.state.newId("msg"),
            senderId = sender.character?.id,
            senderLabel = sender.label,
            subject = composed.subject,
            body = body,
            time = ctx.now,
            category = def.category,
            origin = MessageOrigin.EVENT,
            originId = instance.id,
            options = options,
            deadline = ctx.now.plusDays(m.responseDays),
            defaultOptionId = m.defaultOption,
            focusId = focusOf(scope),
        )
        instance.messageId = message.id
        ctx.state.inbox.messages.add(message)
        trimInbox()
    }

    fun variables(def: EventDefinition, scope: ScopeRef, params: Map<String, Double>): Map<String, String> {
        val vars = mutableMapOf<String, String>()
        val dept = senders.departmentOf(scope)
        if (dept != null) {
            vars["department"] = senders.departmentName(dept)
            ctx.state.territory.departments[dept]?.let { vars["region"] = senders.regionName(it.region) }
        }
        when (scope.type) {
            EventScope.CITY -> vars["city"] = senders.cityName(scope.id)
            EventScope.INFRASTRUCTURE -> vars["infrastructure"] = ctx.catalog.item(scope.id!!)?.name ?: ""
            EventScope.MINISTER -> ctx.state.characters[scope.id]?.let { vars["minister"] = it.fullName }
            EventScope.FOREIGN_COUNTRY -> vars["foreignCountry"] = ctx.db.country(scope.id!!).definition.name
            else -> Unit
        }
        if (scope.type != EventScope.CITY && dept != null) {
            // Ville de référence du département pour les textes (« manifestation à ... »).
            ctx.playerData.territory!!.cities.filter { it.department == dept }.minByOrNull { it.rank }
                ?.let { vars["city"] = it.name }
        }
        params.forEach { (k, v) -> vars[k] = Formatting.amount(v) }
        params["amount"]?.let { vars["amountText"] = Formatting.billions(it) }
        return vars
    }

    private fun drawParams(def: EventDefinition, scope: ScopeRef): Map<String, Double> = def.params.associate { p ->
        val scale = p.scaleBy?.let { (ctx.variables.resolve(it, scope) ?: 1.0).coerceAtLeast(p.minScale) } ?: 1.0
        var value = ctx.rng.nextDouble(p.min, p.max) * scale
        if (p.round > 0) value = (value / p.round).roundToLong() * p.round
        p.name to value
    }

    private fun focusOf(scope: ScopeRef): String? = when (scope.type) {
        EventScope.DEPARTMENT, EventScope.CITY, EventScope.INFRASTRUCTURE, EventScope.FOREIGN_COUNTRY -> scope.id
        else -> null
    }

    private fun trimInbox() {
        val messages = ctx.state.inbox.messages
        val max = ctx.db.config.simulation.inboxHistorySize
        while (messages.size > max) {
            val oldest = messages.firstOrNull { !it.awaitingAnswer } ?: break
            messages.remove(oldest)
        }
    }

    companion object {
        private val VAR = Regex("""\{([a-zA-Z_]+)}""")
        fun substitute(text: String, vars: Map<String, String>): String =
            VAR.replace(text) { vars[it.groupValues[1]] ?: it.value }
    }
}
