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
        val used = ctx.state.dialogue.usedSignatures
        val attempts = ctx.db.config.simulation.messageUniquenessAttempts
        var candidate: Composed? = null
        repeat(attempts) {
            val composed = Composed(
                subject = render(pickVariant(template.subject, context, rng)?.text ?: "", context, rng),
                body = composeBody(template, context, rng),
            )
            val signature = Hashing.messageSignature(composed.body)
            if (signature !in used) {
                used += signature
                return composed
            }
            candidate = composed
        }
        // Toutes les tentatives ont produit un texte déjà vu : on date explicitement le message.
        val fallback = candidate!!.let { it.copy(body = it.body + "\n\n" + datedSignature()) }
        used += Hashing.messageSignature(fallback.body)
        ctx.log("dialogue", "Variantes épuisées pour $templateId : message daté")
        return fallback
    }

    private fun composeBody(template: DialogueTemplate, context: DialogueContext, rng: GameRandom): String {
        val paragraphs = mutableListOf<String>()
        for (section in template.sections) {
            if (section.optional && !rng.chance(section.chance)) continue
            val variant = pickVariant(section.variants, context, rng) ?: continue
            paragraphs += render(variant.text, context, rng)
        }
        return paragraphs.filter { it.isNotBlank() }.joinToString("\n\n")
    }

    private fun pickVariant(variants: List<DialogueVariant>, context: DialogueContext, rng: GameRandom): DialogueVariant? {
        val eligible = variants.filter { v ->
            v.`when`.all { it in context.tags } && v.unless.none { it in context.tags }
        }
        if (eligible.isEmpty()) return null
        // Les variantes spécifiques au contexte sont privilégiées par rapport aux génériques.
        val specific = eligible.filter { it.`when`.isNotEmpty() }
        val pool = if (specific.isNotEmpty() && rng.chance(SPECIFIC_PREFERENCE)) specific else eligible
        return rng.pickWeighted(pool) { it.weight }
    }

    private fun render(text: String, context: DialogueContext, rng: GameRandom): String {
        var result = LEXICON_PATTERN.replace(text) { match ->
            val options = ctx.db.lexicon.words[match.groupValues[1]]
            options?.let { rng.pick(it) } ?: match.groupValues[1]
        }
        result = VARIABLE_PATTERN.replace(result) { match ->
            context.variables[match.groupValues[1]] ?: match.value
        }
        return result.replaceFirstChar { it.uppercaseChar() }
    }

    private fun datedSignature(): String =
        "Fait le " + ctx.now.toDateTime().format(DATE_FORMAT) + "."

    private companion object {
        const val SPECIFIC_PREFERENCE = 0.75
        val LEXICON_PATTERN = Regex("""\[\[([a-z_]+)]]""")
        val VARIABLE_PATTERN = Regex("""\{([a-zA-Z_]+)}""")
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy 'à' HH'h'mm", Locale.FRENCH)
    }
}
