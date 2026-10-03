package fr.president.engine.readout

import fr.president.engine.politics.Character
import fr.president.engine.simulation.SimulationContext

/** Description qualitative des personnages : le joueur découvre les traits progressivement. */
class CharacterReadout(private val ctx: SimulationContext) {

    fun knownTraits(c: Character): List<String> = c.knownTraits.mapNotNull { trait ->
        val labels = ctx.db.readouts.traitLabels[trait] ?: return@mapNotNull null
        val v = c.trait(trait)
        when {
            v >= HIGH -> labels.high
            v <= LOW -> labels.low
            else -> null
        }
    }

    fun relationLabel(value: Double): String = ctx.db.readouts.describe("relation", value).label

    fun summary(c: Character): List<Pair<String, String>> {
        val s = ctx.db.readouts
        val year = ctx.now.toDateTime().year
        return listOf(
            "Âge" to "${c.age(year)} ans",
            "Compétence" to s.describe("skill", c.competence).label,
            "Gestion" to s.describe("skill", c.management).label,
            "Expérience" to s.describe("skill", c.experience).label,
            "Popularité" to s.describe("skill", c.popularity).label,
            "Loyauté" to s.describe("skill", c.loyalty).label,
            "Relation avec vous" to relationLabel(c.relationWithPlayer),
            "Sensibilité" to leaning(c.economicLeaning),
        ) + (if (knownTraits(c).isNotEmpty()) listOf("Tempérament" to knownTraits(c).joinToString(", ")) else emptyList())
    }

    fun leaning(v: Double): String = when {
        v < -FAR -> "très à gauche"
        v < -NEAR -> "à gauche"
        v <= NEAR -> "au centre"
        v <= FAR -> "à droite"
        else -> "très à droite"
    }

    private companion object {
        const val HIGH = 0.65
        const val LOW = 0.35
        const val NEAR = 0.2
        const val FAR = 0.6
    }
}
