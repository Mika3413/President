package fr.president.engine.opinion

import fr.president.engine.simulation.Cadence
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.simulation.SimulationSystem
import fr.president.engine.util.approach
import fr.president.engine.util.clamp01
import fr.president.engine.util.perStepRate

/**
 * Opinion publique par groupe social, puis par territoire.
 * Chaque groupe réagit différemment aux facteurs (chômage, prix, impôts, services...).
 */
class OpinionSystem : SimulationSystem {
    override val name = "opinion"
    override val cadence = Cadence.DAILY

    override fun run(ctx: SimulationContext) {
        val def = ctx.playerData.socialGroups ?: return
        val opinion = ctx.state.opinion
        val scores = OpinionFactors(ctx).compute(def.factors)
        opinion.factorScores.putAll(scores)

        val rate = perStepRate(def.adjustmentMonthly, DAYS_PER_MONTH)
        val shockKeep = 1.0 - perStepRate(def.shockDecayMonthly, DAYS_PER_MONTH)
        opinion.honeymoon *= 1.0 - perStepRate(def.honeymoonDecayMonthly, DAYS_PER_MONTH)

        for (group in def.groups) {
            val state = opinion.groups.getOrPut(group.id) { GroupOpinion(group.baseApproval) }
            val fundamentals = group.sensitivities.entries.sumOf { (factor, weight) -> weight * (scores[factor] ?: 0.0) }
            val target = (group.baseApproval + fundamentals + opinion.honeymoon).clamp01()
            state.approval = approach(state.approval, target, rate)
            state.shock *= shockKeep
        }
        opinion.nationalApproval = national(ctx)
        updateTerritories(ctx, rate)
        if (ctx.now.toDateTime().dayOfMonth == 1) opinion.history.push(opinion.nationalApproval)
    }

    private fun national(ctx: SimulationContext): Double {
        val def = ctx.playerData.socialGroups!!
        val opinion = ctx.state.opinion
        return def.partitions.map { p ->
            def.groups.filter { it.partition == p.id }
                .sumOf { it.populationShare * (opinion.groups[it.id]?.effective ?: it.baseApproval) }
        }.average()
    }

    private fun updateTerritories(ctx: SimulationContext, rate: Double) {
        val def = ctx.playerData.socialGroups!!
        val territory = ctx.state.territory
        // Recalculée à chaque pas : la composition sociale évolue (vieillissement) et
        // aucun état caché ne doit échapper à la sauvegarde.
        val local = LocalComposition(def, territory.departments.values)
        val nationalUnemployment = ctx.state.playerCountry.economy.unemployment
        val localKeep = 1.0 - perStepRate(def.localShockDecayMonthly, DAYS_PER_MONTH)
        val presidentLeaning = ctx.state.characters.getValue(ctx.state.player.presidentId).economicLeaning
        val totalPopulation = territory.departments.values.sumOf { it.population }.toDouble()
        val meanDistance = territory.departments.values.sumOf { kotlin.math.abs(it.politicalLeaning - presidentLeaning) * it.population } / totalPopulation
        for (dept in territory.departments.values) {
            val shares = local.shares(dept)
            val base = def.partitions.map { p ->
                def.groups.filter { it.partition == p.id }
                    .sumOf { (shares[it.id] ?: 0.0) * (ctx.state.opinion.groups[it.id]?.effective ?: it.baseApproval) }
            }.average()
            val unemploymentEffect = def.localUnemploymentWeight * (nationalUnemployment - dept.unemployment)
            dept.localShock *= localKeep
            // Un territoire politiquement éloigné du président lui est structurellement moins favorable.
            val politicalEffect = def.localLeaningWeight * (meanDistance - kotlin.math.abs(dept.politicalLeaning - presidentLeaning))
            val target = (base + unemploymentEffect + politicalEffect + dept.localShock).clamp01()
            dept.approval = approach(dept.approval, target, rate.coerceAtLeast(MIN_LOCAL_RATE))
        }
        for (region in territory.regions.values) {
            val depts = territory.departments.values.filter { it.region == region.code }
            val population = depts.sumOf { it.population }.toDouble()
            region.approval = depts.sumOf { it.approval * it.population } / population
        }
    }

    private companion object {
        const val DAYS_PER_MONTH = 30.0
        /** Les territoires suivent l'opinion nationale avec au moins cette vitesse quotidienne. */
        const val MIN_LOCAL_RATE = 0.1
    }
}
