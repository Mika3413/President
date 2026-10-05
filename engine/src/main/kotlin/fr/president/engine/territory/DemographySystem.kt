package fr.president.engine.territory

import fr.president.engine.simulation.Cadence
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.simulation.SimulationSystem

/** Évolution mensuelle de la population : naissances, décès, solde migratoire, vieillissement. */
class DemographySystem : SimulationSystem {
    override val name = "demography"
    override val cadence = Cadence.MONTHLY

    override fun run(ctx: SimulationContext) {
        val def = ctx.playerData.definition.demography ?: return
        val country = ctx.state.playerCountry
        val d = ctx.state.demography
        val pop = country.population.toDouble()
        // Natalité et mortalité suivent la fécondité et l'espérance de vie (voir SocietySystem).
        val society = ctx.state.society
        val births = pop * def.birthRate * (society.fertility / SocietyState.FERTILITY_START) / MONTHS
        val deaths = pop * def.deathRate * (1 + (SocietyState.LIFE_START - society.lifeExpectancy) * MORTALITY_PER_YEAR).coerceAtLeast(0.5) / MONTHS
        val migration = pop * def.netMigrationRate * d.immigrationFactor / MONTHS
        val growth = (births - deaths + migration) / pop
        country.population = (pop + births - deaths + migration).toLong()
        val share = { v: Double -> (v * MONTHS).toLong() }
        d.birthsLastYear = share(births); d.deathsLastYear = share(deaths); d.netMigrationLastYear = share(migration)
        for (dept in ctx.state.territory.departments.values) {
            dept.population = (dept.population * (1 + growth)).toLong()
            dept.seniorShare = (dept.seniorShare + def.agingPerYear / MONTHS).coerceAtMost(MAX_SENIOR)
        }
        for (city in ctx.state.territory.cities.values) city.population = (city.population * (1 + growth)).toLong()
        // La population active soutient la croissance potentielle.
        country.economy.potentialGrowth += (growth * MONTHS - REFERENCE_GROWTH) * LABOUR_FORCE_WEIGHT / MONTHS
    }

    private companion object {
        const val MONTHS = 12.0
        const val MORTALITY_PER_YEAR = 0.08
        const val MAX_SENIOR = 0.45
        const val REFERENCE_GROWTH = 0.0035
        const val LABOUR_FORCE_WEIGHT = 0.1
    }
}
