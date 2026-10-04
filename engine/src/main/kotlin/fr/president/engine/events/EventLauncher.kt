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
        val level = drawIntensity(def)
        val factor = level?.second?.factor ?: 1.0
        // Les montants tirés (dégâts, sommes demandées) suivent l'ampleur.
        val params = drawParams(def, scope).mapValues { (_, v) -> v * factor }.toMutableMap()
        level?.let { (index, l) -> params[EventIntensity.FACTOR] = l.factor; params[EventIntensity.LEVEL] = index.toDouble() }
        val instance = EventInstance(ctx.state.newId("evt"), def.id, scope.id, params, ctx.now)
        ctx.state.events.active.add(instance)
        ctx.state.events.lastFired[def.id] = ctx.now
        scope.id?.let { ctx.state.events.lastFiredScope["${def.id}:$it"] = ctx.now }
        def.immediateEffects.forEach { ctx.effects.trigger(EventIntensity.scale(it, factor), scope, params, def.id) }
        // Conséquences en chaîne propres au type d'événement (économie, services, voisins...).
        ctx.db.intensity?.consequences?.get(def.id)?.forEach { ctx.effects.trigger(EventIntensity.scale(it, factor), scope, params, def.id) }

        val vars = variables(def, scope, params)
        val titled = level?.let { (index, _) -> ctx.db.intensity?.headlines?.get(def.id)?.getOrNull(index) } ?: def.headline
        val headline = substitute(titled, vars)
        val focus = focusOf(scope)
        ctx.notifications.news(def.category, headline, focus)
        if (def.message != null) {
            val message = deliverMessage(def, instance, scope, vars, details = false)
            // Un courrier qui attend une réponse mérite une notification même s'il n'est pas urgent.
            if (def.urgency == Urgency.INFO && message != null) {
                ctx.notifications.post(def.category, Urgency.IMPORTANT, "Courrier : ${message.senderLabel}", message.subject, focus, journal = false)
            }
        } else {
            instance.resolved = true
        }
        // Un événement exceptionnel devient urgent ; un événement limité reste discret.
        val urgency = when {
            level == null -> def.urgency
            level.second.factor >= EXCEPTIONAL -> Urgency.URGENT
            level.second.factor <= MINOR && def.urgency == Urgency.URGENT -> Urgency.IMPORTANT
            else -> def.urgency
        }
        if (urgency != Urgency.INFO || def.message == null) {
            val text = substitute(def.notificationText, vars) + (level?.let { " Ampleur : ${it.second.label}." } ?: "")
            ctx.notifications.post(def.category, urgency, headline, text.trim(), focus)
        }
        ctx.log("events", "Événement ${def.id} (${scope.type} ${scope.id ?: "national"}) params=$params")
        return instance
    }

    /** Envoie (ou renvoie) le message associé à une instance d'événement. */
    fun deliverMessage(def: EventDefinition, instance: EventInstance, scope: ScopeRef, vars: Map<String, String>, details: Boolean): InboxMessage? {
        val m = def.message ?: return null
        val sender = senders.resolve(m.sender, m.ministry, scope)
        val builder = DialogueContextBuilder(ctx).sender(sender.character, sender.title)
        vars.forEach { (k, v) -> builder.variable(k, v) }
        if (instance.reasks > 0) builder.tag("followup:reask")
        if ("city" in vars) builder.tag("has:city")
        if (def.urgency == Urgency.URGENT) builder.tag("urgency:high")
        val composed = ctx.messages.compose(m.template, builder.build())
        val factor = instance.params[EventIntensity.FACTOR] ?: 1.0
        var body = composed.body
        vars["intensity"]?.let { body = "Ampleur estimée : $it.\n\n$body" }
        if (details && m.detailsTemplate != null) {
            body = ctx.messages.compose(m.detailsTemplate, builder.tag("details").build()).body + "\n\n" + body
        }
        val options = m.options.filter { !(details && it.requestDetails) }
            .map { MessageOption(it.id, EventIntensity.scaleCosts(substitute(it.label, vars), factor), EventIntensity.scaleCosts(substitute(it.hint, vars), factor)) }
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
        return message
    }

    fun variables(def: EventDefinition, scope: ScopeRef, params: Map<String, Double>): Map<String, String> {
        val vars = mutableMapOf<String, String>()
        val dept = senders.departmentOf(scope)
        if (dept != null) {
            vars["department"] = senders.departmentName(dept)
            ctx.playerData.territory?.departments?.firstOrNull { it.code == dept }?.let { d ->
                vars += fr.president.engine.data.PlaceNames.department(d.name, d.article)
            }
            ctx.state.territory.departments[dept]?.let { vars["region"] = senders.regionName(it.region) }
        }
        when (scope.type) {
            EventScope.CITY -> vars["city"] = senders.cityName(scope.id)
            EventScope.INFRASTRUCTURE -> {
                val name = ctx.catalog.item(scope.id!!)?.name ?: ""
                vars["infrastructure"] = name
                if (name.isNotBlank()) vars += fr.president.engine.data.PlaceNames.infrastructure(name)
            }
            EventScope.MINISTER -> ctx.state.characters[scope.id]?.let { vars["minister"] = it.fullName }
            EventScope.FOREIGN_COUNTRY -> vars += fr.president.engine.data.CountryNames(ctx.db.country(scope.id!!).definition).variables("foreign")
            else -> Unit
        }
        if (scope.type != EventScope.CITY && dept != null) {
            // Ville de référence du département pour les textes (« manifestation à ... »).
            ctx.playerData.territory!!.cities.filter { it.department == dept }.minByOrNull { it.rank }
                ?.let { vars["city"] = it.name }
        }
        // Principale guerre étrangère, pour les sommets et les résolutions : {aggressorThe}, {victimOf}...
        fr.president.engine.military.Geopolitics(ctx).mainWarWithout(ctx.state.player.countryId)?.let { w ->
            vars += fr.president.engine.data.CountryNames(ctx.db.country(w.attackers.first()).definition).variables("aggressor")
            vars += fr.president.engine.data.CountryNames(ctx.db.country(w.defenders.first()).definition).variables("victim")
        }
        params.forEach { (k, v) -> vars[k] = Formatting.amount(v) }
        params[EventIntensity.LEVEL]?.let { i -> ctx.db.intensity?.levels?.getOrNull(i.toInt())?.let { vars["intensity"] = it.label } }
        params["amount"]?.let { vars["amountText"] = Formatting.billions(it) }
        return vars
    }

    /** Tire l'ampleur (niveau et facteur), ou null pour un événement fixe. */
    private fun drawIntensity(def: EventDefinition): Pair<Int, IntensityLevel>? {
        val file = ctx.db.intensity ?: return null
        if (def.id in file.fixed || file.levels.isEmpty()) return null
        var roll = ctx.rng.nextDouble() * file.levels.sumOf { it.weight }
        file.levels.forEachIndexed { i, l -> roll -= l.weight; if (roll <= 0) return i to l }
        return file.levels.lastIndex to file.levels.last()
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
        private const val EXCEPTIONAL = 2.5
        private const val MINOR = 0.6
        private val VAR = Regex("""\{([a-zA-Z_]+)\}""")
        fun substitute(text: String, vars: Map<String, String>): String =
            VAR.replace(text) { vars[it.groupValues[1]] ?: it.value }
    }
}
