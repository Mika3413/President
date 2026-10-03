package fr.president.engine.dialogue

import fr.president.engine.politics.Character
import fr.president.engine.politics.CharacterRole
import fr.president.engine.politics.Traits
import fr.president.engine.simulation.SimulationContext

/** Variables et étiquettes servant à composer un message. */
data class DialogueContext(
    val variables: Map<String, String>,
    val tags: Set<String>,
    /** Auteur du message : ses phrases déjà employées ne sont jamais reprises. */
    val senderId: String? = null,
)

/** Construit le contexte de dialogue à partir de l'expéditeur, de la relation et de l'historique. */
class DialogueContextBuilder(private val ctx: SimulationContext) {
    private val variables = mutableMapOf<String, String>()
    private val tags = mutableSetOf<String>()
    private var senderId: String? = null

    init {
        val president = ctx.state.characters.getValue(ctx.state.player.presidentId)
        val institutions = ctx.playerData.definition.institutions
        variables["honorific"] = if (president.female) institutions.honorificFemale else institutions.honorificMale
        variables["president"] = president.fullName
        variables["country"] = ctx.playerData.definition.name
        tags += if (president.female) "president:female" else "president:male"
        tags += seasonTag(ctx.now.month)
        economyTags()
        newsTags()
    }

    fun sender(character: Character?, title: String): DialogueContextBuilder {
        variables["senderTitle"] = title
        if (character == null) return this
        senderId = character.id
        variables["sender"] = character.fullName
        variables["senderLast"] = character.lastName
        tags += if (character.female) "sender:female" else "sender:male"
        traitTags(character)
        tags += when {
            character.relationWithPlayer >= GOOD_RELATION -> "relation:good"
            character.relationWithPlayer <= BAD_RELATION -> "relation:bad"
            else -> "relation:neutral"
        }
        tags += ctx.memory.historyTags(character.id)
        tags += Voice.of(character).tag
        tags += when (character.role) {
            CharacterRole.MAYOR, CharacterRole.DEPARTMENT_PRESIDENT, CharacterRole.REGION_PRESIDENT -> "sender:elected"
            CharacterRole.MINISTER, CharacterRole.PRIME_MINISTER, CharacterRole.PREFECT -> "sender:official"
            CharacterRole.FOREIGN_LEADER -> "sender:foreign"
            else -> "sender:other"
        }
        ctx.memory.lastRecord(character.id)?.let { last ->
            last.label?.let { variables["lastTopic"] = it; tags += "history:topic" }
            variables["lastDate"] = fr.president.engine.util.Formatting.monthYear(last.time)
            val months = last.time.daysUntil(ctx.now) / DAYS_PER_MONTH
            tags += if (months < RECENT_MONTHS) "history:recent" else "history:old"
        }
        return this
    }

    fun variable(key: String, value: String) = apply { variables[key] = value }
    fun variables(values: Map<String, String>) = apply { variables += values }
    fun tag(tag: String) = apply { tags += tag }
    fun tags(values: Collection<String>) = apply { tags += values }

    fun build() = DialogueContext(variables.toMap(), tags.toSet(), senderId)

    private fun traitTags(c: Character) {
        if (c.trait(Traits.AGGRESSIVENESS) > HIGH) tags += "trait:aggressive"
        if (c.trait(Traits.PRAGMATISM) > HIGH) tags += "trait:pragmatic"
        if (c.trait(Traits.CAUTION) > HIGH) tags += "trait:cautious"
        if (c.trait(Traits.EGO) > HIGH) tags += "trait:proud"
        if (c.trait(Traits.OPENNESS) > HIGH) tags += "trait:warm"
        if (c.trait(Traits.NATIONALISM) > HIGH) tags += "trait:nationalist"
        if (c.trait(Traits.TOUGHNESS) > HIGH) tags += "trait:tough"
    }

    private fun economyTags() {
        val e = ctx.state.playerCountry.economy
        if (e.unemployment > e.naturalUnemployment + CRISIS_GAP) tags += "economy:bad"
        if (e.realGrowth > e.potentialGrowth + BOOM_GAP) tags += "economy:good"
        if (ctx.state.opinion.nationalApproval < UNPOPULAR) tags += "president:unpopular"
        if (ctx.state.opinion.nationalApproval > POPULAR) tags += "president:popular"
    }

    /** Actualité récente : les interlocuteurs y font allusion (« alors que la grève... »). */
    private fun newsTags() {
        val cutoff = ctx.now.plusDays(-NEWS_DAYS)
        // Les nouvelles de l'instant même (l'événement qui motive le courrier) sont exclues.
        // Seule l'actualité nationale (sans lieu précis) est citée, entre guillemets.
        val recent = ctx.state.events.news.lastOrNull {
            it.time >= cutoff && it.time < ctx.now && it.focusId == null && it.headline.length <= MAX_NEWS_LENGTH
        } ?: return
        variables["recentNews"] = "« ${recent.headline} »"
        tags += "news:recent"
        tags += "news:" + recent.category.name.lowercase()
        if (fr.president.engine.military.Geopolitics(ctx).enemiesOf(ctx.state.player.countryId).isNotEmpty()) tags += "world:war"
    }

    private fun seasonTag(month: Int) = when (month) {
        in WINTER_MONTHS -> "season:winter"
        in SPRING_MONTHS -> "season:spring"
        in SUMMER_MONTHS -> "season:summer"
        else -> "season:autumn"
    }

    private companion object {
        const val HIGH = 0.65
        const val NEWS_DAYS = 20L
        const val MAX_NEWS_LENGTH = 90
        const val DAYS_PER_MONTH = 30.0
        const val RECENT_MONTHS = 4
        const val GOOD_RELATION = 0.62
        const val BAD_RELATION = 0.38
        const val CRISIS_GAP = 0.01
        const val BOOM_GAP = 0.005
        const val UNPOPULAR = 0.35
        const val POPULAR = 0.55
        val WINTER_MONTHS = setOf(12, 1, 2)
        val SPRING_MONTHS = 3..5
        val SUMMER_MONTHS = 6..8
    }
}
