package fr.president.engine.government

import fr.president.engine.data.GovernmentDefinition
import fr.president.engine.politics.Character
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.util.clamp01

/** Efficacité réelle d'un ministère, selon son ministre et la priorité donnée par le président. */
class MinistryEffectiveness(private val ctx: SimulationContext) {
    private val definition: GovernmentDefinition = ctx.playerData.government!!

    fun of(ministryId: String): Double {
        val minister = ministerOf(ministryId) ?: return VACANT
        val w = definition.effectivenessWeights
        val base = w.competence * minister.competence + w.management * minister.management +
            w.experience * minister.experience + w.loyalty * minister.loyalty
        val totalWeight = w.competence + w.management + w.experience + w.loyalty
        val priority = when (ctx.state.government.priorities[ministryId] ?: Priority.NORMAL) {
            Priority.HIGH -> w.highPriorityBonus
            Priority.LOW -> -w.lowPriorityPenalty
            Priority.NORMAL -> 0.0
        }
        return (base / totalWeight + priority).clamp01()
    }

    fun forDomain(domain: String): Double {
        val ministry = definition.ministries.firstOrNull { it.domain == domain } ?: return NEUTRAL
        return of(ministry.id)
    }

    fun ministerOf(ministryId: String): Character? {
        val id = if (definition.ministries.firstOrNull { it.id == ministryId }?.isPrimeMinister == true) {
            ctx.state.government.primeMinisterId
        } else {
            ctx.state.government.ministers[ministryId]
        }
        return id?.let { ctx.state.characters[it] }
    }

    private companion object {
        /** Un ministère sans titulaire est géré par intérim, avec une efficacité réduite. */
        const val VACANT = 0.3
        const val NEUTRAL = 0.5
    }
}
