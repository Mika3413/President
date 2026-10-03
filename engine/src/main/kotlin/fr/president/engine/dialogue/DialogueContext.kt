package fr.president.engine.dialogue

import fr.president.engine.politics.Character
import fr.president.engine.politics.Traits
import fr.president.engine.simulation.SimulationContext

/** Variables et étiquettes servant à composer un message. */
data class DialogueContext(
    val variables: Map<String, String>,
    val tags: Set<String>,
)

/** Construit le contexte de dialogue à partir de l'expéditeur, de la relation et de l'historique. */
class DialogueContextBuilder(private val ctx: SimulationContext) {
    private val variables = mutableMapOf<String, String>()
    private val tags = mutableSetOf<String>()

    init {
        val president = ctx.state.characters.getValue(ctx.state.player.presidentId)
        val institutions = ctx.playerData.definition.institutions
        variables["honorific"] = if (president.female) institutions.honorificFemale else institutions.honorificMale
        variables["president"] = president.fullName
        variables["country"] = ctx.playerData.definition.name
        tags += if (president.female) "president:female" else "president:male"
        tags += seasonTag(ctx.now.month)
        economyTags()
    }

    fun sender(character: Character?, title: String): DialogueContextBuilder {
        variables["senderTitle"] = title
        if (character == null) return this
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
        return this
    }

    fun variable(key: String, value: String) = apply { variables[key] = value }
    fun tag(tag: String) = apply { tags += tag }
    fun tags(values: Collection<String>) = apply { tags += values }

    fun build() = DialogueContext(variables.toMap(), tags.toSet())

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

    private fun seasonTag(month: Int) = when (month) {
        in WINTER_MONTHS -> "season:winter"
        in SPRING_MONTHS -> "season:spring"
        in SUMMER_MONTHS -> "season:summer"
        else -> "season:autumn"
    }

    private companion object {
        const val HIGH = 0.65
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
