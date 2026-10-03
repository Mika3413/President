package fr.president.engine.dialogue

import fr.president.engine.diplomacy.DiplomacyService
import fr.president.engine.diplomacy.RelationCalculator
import fr.president.engine.events.InteractionOutcome
import fr.president.engine.events.ScopeRef
import fr.president.engine.inbox.InboxMessage
import fr.president.engine.inbox.MessageOrigin
import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.politics.Character
import fr.president.engine.politics.CharacterRole
import fr.president.engine.politics.Traits
import fr.president.engine.simulation.ScheduledAction
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.util.clamp01

/** Sujets d'un entretien à l'initiative du président. */
enum class ConversationTopic(val label: String, val hint: String, val foreign: Boolean) {
    STRENGTHEN("Renforcer nos liens", "Un échange cordial pour entretenir la relation", true),
    REASSURE("Rassurer sur nos intentions", "Apaiser une relation tendue", true),
    SEEK_SUPPORT("Solliciter son soutien", "Demander de l'appui sur la scène internationale", true),
    CONCERN("Exprimer une préoccupation", "Faire part de votre désaccord, sans rompre", true),
    WARN("Adresser une mise en garde", "Fermeté : peut dissuader, ou braquer", true),
    LISTEN("Écouter ses attentes", "L'élu vous expose les besoins de son territoire", false),
    VISIT("Annoncer une visite présidentielle", "Votre venue mobilise le territoire", false),
    PRAISE("Saluer son action", "Une marque de reconnaissance", false),
    REPRIMAND("Le recadrer", "Rappeler fermement la ligne de l'État", false),
}

/** Tonalité de la réponse de l'interlocuteur. */
enum class ConversationMood(val tag: String, val label: String) {
    WARM("mood:warm", "Échange chaleureux"),
    NEUTRAL("mood:neutral", "Échange courtois"),
    COLD("mood:cold", "Échange tendu"),
}

/**
 * Entretiens à l'initiative du président avec un dirigeant étranger ou un élu local. La réponse,
 * rédigée sans IA, dépend de la relation, du tempérament de l'interlocuteur et de l'historique ;
 * le compte rendu arrive dans la messagerie et l'entretien a des conséquences.
 */
class ConversationService(private val ctx: SimulationContext) {

    data class Result(val mood: ConversationMood, val summary: String, val messageId: String)

    fun topicsFor(character: Character): List<ConversationTopic> =
        ConversationTopic.entries.filter { it.foreign == (character.role == CharacterRole.FOREIGN_LEADER) }

    /** Raison pour laquelle l'entretien est impossible (null = possible). */
    fun blocker(characterId: String): String? {
        val c = ctx.state.characters[characterId] ?: return "Personnalité inconnue"
        if (!c.active) return "Cette personnalité n'exerce plus de fonctions"
        if (c.role == CharacterRole.FOREIGN_LEADER) {
            val country = foreignCountryOf(c) ?: return "Pays inconnu"
            if (fr.president.engine.military.Geopolitics(ctx).atWar(country, ctx.state.player.countryId)) {
                return "Aucun contact direct avec un pays en guerre contre nous"
            }
        }
        val last = ctx.state.dialogue.lastConversation[characterId] ?: return null
        val cooldown = if (c.role == CharacterRole.FOREIGN_LEADER) FOREIGN_COOLDOWN_DAYS else LOCAL_COOLDOWN_DAYS
        val wait = cooldown - last.daysUntil(ctx.now)
        return if (wait > 0) "Vous vous êtes entretenu récemment : prochain échange possible dans ${wait.toInt() + 1} jours" else null
    }

    fun talk(characterId: String, topic: ConversationTopic): kotlin.Result<Result> = runCatching {
        blocker(characterId)?.let { error(it) }
        val c = ctx.state.characters.getValue(characterId)
        require(topic in topicsFor(c)) { "Sujet inadapté à cet interlocuteur" }
        val mood = moodOf(c, topic)
        ctx.state.dialogue.lastConversation[characterId] = ctx.now
        val summary = if (c.role == CharacterRole.FOREIGN_LEADER) applyForeign(c, topic, mood) else applyLocal(c, topic, mood)
        val outcome = when (mood) {
            ConversationMood.WARM -> InteractionOutcome.ACCEPTED
            ConversationMood.NEUTRAL -> InteractionOutcome.NEUTRAL
            ConversationMood.COLD -> InteractionOutcome.REFUSED
        }
        val message = writeReport(c, topic, mood)
        ctx.memory.record(c.id, "talk:${topic.name.lowercase()}", outcome, topicMemoryLabel(topic))
        ctx.log("dialogue", "Entretien ${topic.name} avec ${c.fullName} : ${mood.name}")
        Result(mood, summary, message.id)
    }

    // ---- Tonalité ----

    private fun moodOf(c: Character, topic: ConversationTopic): ConversationMood {
        val relation = if (c.role == CharacterRole.FOREIGN_LEADER) {
            foreignCountryOf(c)?.let { RelationCalculator(ctx).score(it, ctx.state.player.countryId) } ?: c.relationWithPlayer
        } else c.relationWithPlayer
        var score = relation + ctx.rng.nextGaussian() * MOOD_NOISE
        score += when (topic) {
            ConversationTopic.STRENGTHEN, ConversationTopic.PRAISE -> FRIENDLY_TOPIC_BONUS + c.trait(Traits.OPENNESS) * TRAIT_WEIGHT
            ConversationTopic.REASSURE -> c.trait(Traits.CAUTION) * TRAIT_WEIGHT
            ConversationTopic.SEEK_SUPPORT -> c.trait(Traits.PRAGMATISM) * TRAIT_WEIGHT - ASK_PENALTY
            ConversationTopic.LISTEN, ConversationTopic.VISIT -> FRIENDLY_TOPIC_BONUS
            ConversationTopic.CONCERN -> -c.trait(Traits.EGO) * TRAIT_WEIGHT
            ConversationTopic.WARN, ConversationTopic.REPRIMAND ->
                -HARSH_TOPIC_PENALTY - c.trait(Traits.EGO) * TRAIT_WEIGHT - c.trait(Traits.AGGRESSIVENESS) * TRAIT_WEIGHT
        }
        return when {
            score >= WARM_THRESHOLD -> ConversationMood.WARM
            score <= COLD_THRESHOLD -> ConversationMood.COLD
            else -> ConversationMood.NEUTRAL
        }
    }

    // ---- Conséquences ----

    private fun applyForeign(c: Character, topic: ConversationTopic, mood: ConversationMood): String {
        val country = foreignCountryOf(c) ?: return ""
        val diplomacy = DiplomacyService(ctx)
        val name = ctx.db.country(country).definition.name
        return when (topic) {
            ConversationTopic.STRENGTHEN, ConversationTopic.REASSURE -> when (mood) {
                ConversationMood.WARM -> { diplomacy.remember(country, TALK_CORDIAL, "entretien avec le président"); "La relation avec $name se réchauffe." }
                ConversationMood.NEUTRAL -> { diplomacy.remember(country, TALK_CORDIAL, "entretien avec le président", SMALL_POSITIVE); "Échange poli, sans percée." }
                ConversationMood.COLD -> "Votre interlocuteur reste sur la réserve."
            }
            ConversationTopic.SEEK_SUPPORT -> when (mood) {
                ConversationMood.WARM -> { diplomacy.remember(country, "NEGOTIATION_GOODWILL", "soutien promis au président"); "$name se montre disposé à vous appuyer." }
                ConversationMood.NEUTRAL -> "$name ne s'engage pas, mais ne ferme pas la porte."
                ConversationMood.COLD -> { diplomacy.remember(country, TALK_TENSE, "sollicitation jugée déplacée"); "$name refuse de s'engager et le fait savoir." }
            }
            ConversationTopic.CONCERN -> when (mood) {
                ConversationMood.COLD -> { diplomacy.remember(country, TALK_TENSE, "reproches du président"); "Le ton est monté ; la relation se tend." }
                else -> { diplomacy.remember(country, "DISAGREEMENT", "désaccord exprimé franchement", SMALL_NEGATIVE); "Votre message a été entendu." }
            }
            ConversationTopic.WARN -> {
                diplomacy.remember(country, WARNING, "mise en garde du président")
                // Un dirigeant prudent recule ; un dirigeant agressif se braque.
                if (mood == ConversationMood.COLD) { diplomacy.remember(country, TALK_TENSE, "mise en garde mal reçue"); "La mise en garde a braqué $name." }
                else {
                    ctx.state.diplomacy.relation(country, ctx.state.player.countryId).memories.removeAll { it.kind == "THREAT" }
                    "$name a pris note de votre fermeté."
                }
            }
            else -> ""
        }
    }

    private fun applyLocal(c: Character, topic: ConversationTopic, mood: ConversationMood): String {
        val relationDelta = when (topic) {
            ConversationTopic.LISTEN -> if (mood == ConversationMood.COLD) 0.02 else 0.08
            ConversationTopic.VISIT -> 0.1
            ConversationTopic.PRAISE -> if (mood == ConversationMood.COLD) 0.0 else 0.06
            ConversationTopic.REPRIMAND -> -0.15
            else -> 0.0
        }
        c.relationWithPlayer = (c.relationWithPlayer + relationDelta).clamp01()
        val dept = departmentOf(c)
        return when (topic) {
            ConversationTopic.LISTEN -> {
                scheduleRequest(c)
                "${c.fullName} vous expose ses priorités ; une demande formelle suivra."
            }
            ConversationTopic.VISIT -> {
                dept?.let { ctx.state.territory.departments[it]?.let { d -> d.localShock += VISIT_LOCAL_BOOST } }
                "Votre visite est annoncée : l'accueil local devrait être favorable."
            }
            ConversationTopic.PRAISE -> if (mood == ConversationMood.COLD) "L'élu reste méfiant malgré vos compliments." else "L'élu apprécie la reconnaissance de l'État."
            ConversationTopic.REPRIMAND -> {
                if (mood == ConversationMood.COLD && ctx.rng.chance(PUBLIC_CLASH_CHANCE)) {
                    dept?.let { ctx.state.territory.departments[it]?.let { d -> d.localShock -= CLASH_LOCAL_COST } }
                    ctx.notifications.news(NotificationCategory.TERRITORY, "${c.fullName} dénonce publiquement les pressions de l'Élysée")
                    "L'élu rend l'échange public : la presse locale s'en empare."
                } else "Le message est passé."
            }
            else -> ""
        }
    }

    /** Après un entretien d'écoute, l'élu formalise une demande adaptée à son territoire. */
    private fun scheduleRequest(c: Character) {
        val ref = c.roleRef ?: return
        val scope = when (c.role) {
            CharacterRole.MAYOR -> fr.president.engine.events.EventScope.CITY
            CharacterRole.DEPARTMENT_PRESIDENT -> fr.president.engine.events.EventScope.DEPARTMENT
            CharacterRole.REGION_PRESIDENT -> fr.president.engine.events.EventScope.DEPARTMENT
            else -> return
        }
        val scopeId = if (c.role == CharacterRole.REGION_PRESIDENT) {
            ctx.state.territory.departments.values.filter { it.region == ref }.maxByOrNull { it.population }?.code ?: return
        } else ref
        val candidates = ctx.db.events.filter { it.scope == scope && it.category == NotificationCategory.TERRITORY && it.message != null }
        if (candidates.isEmpty()) return
        val def = ctx.rng.pick(candidates)
        ctx.scheduler.schedule(ScheduledAction.EventLaunch(ctx.now.plusDays(ctx.rng.nextDouble(REQUEST_MIN_DAYS, REQUEST_MAX_DAYS)), def.id, scopeId))
    }

    // ---- Compte rendu ----

    private fun writeReport(c: Character, topic: ConversationTopic, mood: ConversationMood): InboxMessage {
        val foreign = c.role == CharacterRole.FOREIGN_LEADER
        val title = titleOf(c)
        val builder = DialogueContextBuilder(ctx).sender(c, title)
            .tag("topic:${topic.name.lowercase()}")
            .tag(mood.tag)
            .tag(if (foreign) "talk:foreign" else "talk:local")
        if (foreign) foreignCountryOf(c)?.let { builder.variables(fr.president.engine.data.CountryNames(ctx.db.country(it).definition).variables("foreign")) }
        localPlace(c)?.let { builder.variable("place", it) }
        val composed = ctx.messages.compose(if (foreign) FOREIGN_TEMPLATE else LOCAL_TEMPLATE, builder.build())
        val message = InboxMessage(
            id = ctx.state.newId("msg"),
            senderId = c.id,
            senderLabel = "${c.fullName}, $title",
            subject = composed.subject,
            body = composed.body,
            time = ctx.now,
            category = if (foreign) NotificationCategory.DIPLOMACY else NotificationCategory.TERRITORY,
            origin = MessageOrigin.CONVERSATION,
            originId = topic.name,
            focusId = if (foreign) foreignCountryOf(c) else departmentOf(c),
            read = true,
        )
        ctx.state.inbox.messages.add(message)
        return message
    }

    private fun titleOf(c: Character): String {
        val titles = ctx.playerData.government?.localTitles
        return when (c.role) {
            CharacterRole.FOREIGN_LEADER -> foreignCountryOf(c)?.let { ctx.db.country(it).definition.let { d -> "${d.institutions.headOfGovernment(c.female)} (${d.name})" } } ?: ""
            CharacterRole.MAYOR -> "${titles?.mayor(c.female) ?: "Maire"} de ${localPlace(c) ?: ""}"
            CharacterRole.DEPARTMENT_PRESIDENT -> "${titles?.departmentPresident(c.female) ?: "Président du département"} (${localPlace(c) ?: ""})"
            CharacterRole.REGION_PRESIDENT -> "${titles?.regionPresident(c.female) ?: "Président de région"} (${localPlace(c) ?: ""})"
            CharacterRole.PREFECT -> "${titles?.prefect(c.female) ?: "Préfet"} (${localPlace(c) ?: ""})"
            else -> ""
        }
    }

    private fun localPlace(c: Character): String? {
        val ref = c.roleRef ?: return null
        val names = fr.president.engine.events.SenderResolver(ctx)
        return when (c.role) {
            CharacterRole.MAYOR -> names.cityName(ref)
            CharacterRole.DEPARTMENT_PRESIDENT -> names.departmentName(ref)
            CharacterRole.REGION_PRESIDENT, CharacterRole.PREFECT -> names.regionName(ref)
            else -> null
        }?.ifBlank { null }
    }

    private fun departmentOf(c: Character): String? {
        val ref = c.roleRef ?: return null
        return when (c.role) {
            CharacterRole.MAYOR -> ctx.state.territory.cities[ref]?.department
            CharacterRole.DEPARTMENT_PRESIDENT -> ref
            CharacterRole.REGION_PRESIDENT, CharacterRole.PREFECT ->
                ctx.state.territory.departments.values.filter { it.region == ref }.maxByOrNull { it.population }?.code
            else -> null
        }
    }

    private fun foreignCountryOf(c: Character): String? =
        ctx.state.countries.values.firstOrNull { it.leaderId == c.id }?.id ?: c.roleRef

    private fun topicMemoryLabel(topic: ConversationTopic): String = when (topic) {
        ConversationTopic.STRENGTHEN -> "notre entretien sur l'avenir de nos relations"
        ConversationTopic.REASSURE -> "notre échange destiné à apaiser les tensions"
        ConversationTopic.SEEK_SUPPORT -> "votre demande de soutien"
        ConversationTopic.CONCERN -> "vos préoccupations"
        ConversationTopic.WARN -> "votre mise en garde"
        ConversationTopic.LISTEN -> "les besoins de notre territoire"
        ConversationTopic.VISIT -> "votre visite"
        ConversationTopic.PRAISE -> "votre message de reconnaissance"
        ConversationTopic.REPRIMAND -> "votre rappel à l'ordre"
    }

    private companion object {
        const val FOREIGN_TEMPLATE = "talk_foreign"
        const val LOCAL_TEMPLATE = "talk_local"
        const val TALK_CORDIAL = "TALK_CORDIAL"
        const val TALK_TENSE = "TALK_TENSE"
        const val WARNING = "WARNING"
        const val FOREIGN_COOLDOWN_DAYS = 45.0
        const val LOCAL_COOLDOWN_DAYS = 30.0
        const val MOOD_NOISE = 0.08
        const val TRAIT_WEIGHT = 0.15
        const val FRIENDLY_TOPIC_BONUS = 0.1
        const val ASK_PENALTY = 0.05
        const val HARSH_TOPIC_PENALTY = 0.1
        const val WARM_THRESHOLD = 0.62
        const val COLD_THRESHOLD = 0.4
        const val SMALL_POSITIVE = 0.015
        const val SMALL_NEGATIVE = -0.015
        const val VISIT_LOCAL_BOOST = 0.03
        const val CLASH_LOCAL_COST = 0.015
        const val PUBLIC_CLASH_CHANCE = 0.5
        const val REQUEST_MIN_DAYS = 3.0
        const val REQUEST_MAX_DAYS = 12.0
    }
}
