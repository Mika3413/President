package fr.president.engine.military

import fr.president.engine.economy.BudgetCalculator
import fr.president.engine.government.MinistryEffectiveness
import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import fr.president.engine.simulation.Cadence
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.simulation.SimulationSystem
import fr.president.engine.util.approach
import fr.president.engine.util.clamp01

/**
 * Entretien mensuel des forces : la disponibilité, les stocks et le moral suivent
 * le financement réel de la défense et l'efficacité du ministère des Armées.
 */
class MilitarySystem : SimulationSystem {
    override val name = "military"
    override val cadence = Cadence.MONTHLY

    override fun run(ctx: SimulationContext) {
        val units = ctx.state.military.units.values
        if (units.isEmpty()) return
        val funding = BudgetCalculator.realFundingRatio(ctx.state.playerCountry.economy, DEFENSE_DOMAIN) ?: 1.0
        val ministry = MinistryEffectiveness(ctx).forDomain(DEFENSE_DOMAIN)
        val readinessTarget = (BASE_READINESS + FUNDING_WEIGHT * (funding - 1.0) + MINISTRY_WEIGHT * (ministry - NEUTRAL)).clamp01()
        val supplyTarget = (BASE_SUPPLY + FUNDING_WEIGHT * (funding - 1.0)).clamp01()
        for (unit in units) {
            unit.readiness = approach(unit.readiness, readinessTarget - unit.fatigue * FATIGUE_PENALTY, ADJUSTMENT)
            unit.ammunition = approach(unit.ammunition, supplyTarget, ADJUSTMENT)
            unit.fuel = approach(unit.fuel, supplyTarget, ADJUSTMENT)
            unit.fatigue = approach(unit.fatigue, REST_FATIGUE, ADJUSTMENT)
            unit.morale = approach(unit.morale, (readinessTarget + ctx.state.opinion.nationalApproval) / 2, ADJUSTMENT)
        }
        val totalPersonnel = units.sumOf { it.personnel }.toDouble()
        val previous = ctx.state.military.overallReadiness
        ctx.state.military.overallReadiness = units.sumOf { it.readiness * it.personnel } / totalPersonnel
        val criticalAmmo = units.filter { it.ammunition < CRITICAL_STOCK }
        if (criticalAmmo.isNotEmpty() && previous >= ctx.state.military.overallReadiness) {
            ctx.notifications.post(
                NotificationCategory.MILITARY, Urgency.IMPORTANT,
                "Stocks de munitions critiques",
                "${criticalAmmo.size} unité(s) signalent un manque critique de munitions.",
                criticalAmmo.first().baseId,
            )
        }
    }

    private companion object {
        const val DEFENSE_DOMAIN = "defense"
        const val BASE_READINESS = 0.68
        const val BASE_SUPPLY = 0.6
        const val FUNDING_WEIGHT = 0.8
        const val MINISTRY_WEIGHT = 0.3
        const val NEUTRAL = 0.5
        const val ADJUSTMENT = 0.15
        const val FATIGUE_PENALTY = 0.2
        const val REST_FATIGUE = 0.15
        const val CRITICAL_STOCK = 0.25
    }
}
