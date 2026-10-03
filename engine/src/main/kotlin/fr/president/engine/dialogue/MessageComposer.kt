package fr.president.engine.dialogue

import fr.president.engine.simulation.SimulationContext
import fr.president.engine.util.GameRandom
import fr.president.engine.util.Hashing
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Compose des messages procéduraux sans IA générative.
 * Chaque message est l'assemblage de sections tirées parmi de nombreuses variantes,
 * enrichies de synonymes. Une signature est mémorisée pour ne jamais répéter un texte
 * identique dans une même sauvegarde.
 */
class MessageComposer(private val ctx: SimulationContext) {

    data class Composed(val subject: String, val body: String)

    fun compose(templateId: String, context: DialogueContext, rng: GameRandom = ctx.rng): Composed {
        val template = ctx.db.template(templateId)
        val state = ctx.state.dialogue
        state.composedCount++
        val used = state.usedSignatures
        val attempts = ctx.db.config.simulation.messageUniquenessAttempts
        var candidate: Composed? = null
        var candidatePhrases: List<Long> = emptyList()
        repeat(attempts) {
            val phrases = mutableListOf<Long>()
            val composed = Composed(
                subject = render(pickVariant(templateId, SUBJECT, template.subject, context, rng, phrases)?.text ?: "", context, rng),
                body = composeBody(template, context, rng, phrases),
            )
            val signature = Hashing.messageSignature(composed.body)
            if (signature !in used) {
                used += signature
                remember(phrases, context.senderId)
                return composed
            }
            candidate = composed
            candidatePhrases = phrases
        }
        // Toutes les tentatives ont produit un texte déjà vu : on date explicitement le message.
        val fallback = candidate!!.let { it.copy(body = it.body + "\n\n" + datedSignature()) }
        used += Hashing.messageSignature(fallback.body)
        remember(candidatePhrases, context.senderId)
        ctx.log("dialogue", "Variantes épuisées pour $templateId : message daté")
        return fallback
    }

    private fun composeBody(template: DialogueTemplate, context: DialogueContext, rng: GameRandom, phrases: MutableList<Long>): String {
        val paragraphs = mutableListOf<String>()
        for (section in template.sections) {
            if (section.optional && !rng.chance(section.chance)) continue
            val variant = pickVariant(template.id, section.id, section.variants, context, rng, phrases) ?: continue
            paragraphs += render(variant.text, context, rng)
        }
        return paragraphs.filter { it.isNotBlank() }.joinToString("\n\n")
    }

    /**
     * Choisit une variante en évitant les répétitions : jamais une phrase que cet interlocuteur a
     * déjà écrite, ni une phrase lue dans les derniers messages. Si tout a servi, la moins récente.
     */
    private fun pickVariant(
        templateId: String, sectionId: String, variants: List<DialogueVariant>,
        context: DialogueContext, rng: GameRandom, phrases: MutableList<Long>,
    ): DialogueVariant? {
        val eligible = variants.filter { v ->
            v.`when`.all { it in context.tags } && v.unless.none { it in context.tags }
        }
        if (eligible.isEmpty()) return null
        val state = ctx.state.dialogue
        val keys = eligible.associateWith { phraseKey(templateId, sectionId, it.text) }
        val bySender = context.senderId?.let { state.phrasesBySender[it] }.orEmpty()
        val now = state.composedCount
        val fresh = eligible.filter { v ->
            val k = keys.getValue(v)
            k !in bySender && now - (state.phraseLastUse[k] ?: NEVER) > RECENT_MESSAGES
        }
        val pool = fresh.ifEmpty {
            // Tout a déjà servi : on privilégie ce que l'interlocuteur n'a pas dit, puis le plus ancien.
            val notBySender = eligible.filter { keys.getValue(it) !in bySender }.ifEmpty { eligible }
            val oldest = notBySender.minOf { state.phraseLastUse[keys.getValue(it)] ?: NEVER }
            notBySender.filter { (state.phraseLastUse[keys.getValue(it)] ?: NEVER) == oldest }
        }
        // Les variantes spécifiques au contexte sont privilégiées par rapport aux génériques.
        val specific = pool.filter { it.`when`.isNotEmpty() }
        val chosen = rng.pickWeighted(if (specific.isNotEmpty() && rng.chance(SPECIFIC_PREFERENCE)) specific else pool) { it.weight }
        chosen?.let { phrases += keys.getValue(it) }
        return chosen
    }

    // Les sections communes (ouverture, rappel, actualité, formule finale) sont partagées entre
    // modèles : la clé ne dépend pas du modèle, pour qu'une même phrase ne revienne pas d'un courrier à l'autre.
    @Suppress("UNUSED_PARAMETER")
    private fun phraseKey(templateId: String, sectionId: String, text: String): Long =
        Hashing.fnv1a64("$sectionId|$text")

    private fun remember(phrases: List<Long>, senderId: String?) {
        val state = ctx.state.dialogue
        val now = state.composedCount
        phrases.forEach { state.phraseLastUse[it] = now }
        if (senderId != null) {
            val set = state.phrasesBySender.getOrPut(senderId) { linkedSetOf() }
            set += phrases
            while (set.size > MAX_PHRASES_PER_SENDER) set.remove(set.first())
        }
        if (state.phraseLastUse.size > MAX_TRACKED_PHRASES) {
            state.phraseLastUse.entries.removeAll { now - it.value > PHRASE_MEMORY_MESSAGES }
        }
    }

    private fun render(text: String, context: DialogueContext, rng: GameRandom): String {
        // Variables d'abord, pour qu'elles puissent figurer dans une alternative.
        var result = VARIABLE_PATTERN.replace(text) { match ->
            context.variables[match.groupValues[1]] ?: match.value
        }
        // {{a|b|c}} : alternative tirée au sort à l'intérieur d'une phrase.
        result = ALTERNATIVE_PATTERN.replace(result) { match -> rng.pick(match.groupValues[1].split('|')) }
        result = LEXICON_PATTERN.replace(result) { match ->
            val options = ctx.db.lexicon.words[match.groupValues[1]]
            options?.let { rng.pick(it) } ?: match.groupValues[1]
        }
        return result.replaceFirstChar { it.uppercaseChar() }
    }


    private fun datedSignature(): String =
        "Fait le " + ctx.now.toDateTime().format(DATE_FORMAT) + "."

    private companion object {
        const val SPECIFIC_PREFERENCE = 0.75
        const val SUBJECT = "subject"
        const val NEVER = -1_000_000L
        /** Une phrase n'est pas reprise dans les N messages suivants, quel qu'en soit l'auteur. */
        const val RECENT_MESSAGES = 40L
        const val MAX_PHRASES_PER_SENDER = 800
        const val MAX_TRACKED_PHRASES = 20_000
        const val PHRASE_MEMORY_MESSAGES = 3_000L
        val ALTERNATIVE_PATTERN = Regex("""\{\{([^{}]+)}}""")
        val LEXICON_PATTERN = Regex("""\[\[([a-z_]+)]]""")
        val VARIABLE_PATTERN = Regex("""\{([a-zA-Z_]+)}""")
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy 'à' HH'h'mm", Locale.FRENCH)
    }
}
