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
        val player = ctx.state.player.countryId
        regenerateStocks(ctx)
        foreignMaintenance(ctx)
        val units = ctx.state.military.units.values.filter { it.countryId == player && !it.destroyed && !it.inCombat }
        if (units.isEmpty()) return
        val funding = BudgetCalculator.realFundingRatio(ctx.state.playerCountry.economy, DEFENSE_DOMAIN) ?: 1.0
        val ministry = MinistryEffectiveness(ctx).forDomain(DEFENSE_DOMAIN)
        val readinessTarget = (BASE_READINESS + FUNDING_WEIGHT * (funding - 1.0) + MINISTRY_WEIGHT * (ministry - NEUTRAL)).clamp01()
        for (unit in units) {
            unit.readiness = approach(unit.readiness, readinessTarget - unit.fatigue * FATIGUE_PENALTY, ADJUSTMENT)
            unit.fatigue = approach(unit.fatigue, REST_FATIGUE, ADJUSTMENT)
            unit.morale = approach(unit.morale, (readinessTarget + ctx.state.opinion.nationalApproval) / 2, ADJUSTMENT)
        }
        val totalPersonnel = units.sumOf { it.personnel * it.strength }.coerceAtLeast(1.0)
        val previous = ctx.state.military.overallReadiness
        ctx.state.military.overallReadiness = units.sumOf { it.readiness * it.personnel * it.strength } / totalPersonnel
        val criticalAmmo = units.filter { it.ammunition < CRITICAL_STOCK }
        if (criticalAmmo.isNotEmpty() && previous >= ctx.state.military.overallReadiness) {
            ctx.notifications.post(
                NotificationCategory.MILITARY, Urgency.IMPORTANT,
                "Stocks de munitions critiques",
                "${criticalAmmo.size} unité(s) signalent un manque critique de munitions.",
                criticalAmmo.first().id,
            )
        }
    }

    /** Production nationale de munitions et de carburant, accrue en économie de guerre. */
    private fun regenerateStocks(ctx: SimulationContext) {
        val p = ctx.db.militaryParameters
        val s = ctx.state.military.stocks
        val funding = BudgetCalculator.realFundingRatio(ctx.state.playerCountry.economy, DEFENSE_DOMAIN) ?: 1.0
        val regen = p.stockRegenPerMonth * funding + if (s.warEconomy) p.warEconomyRegenBonus else 0.0
        s.ammunition = (s.ammunition + regen).coerceAtMost(1.0)
        s.fuel = (s.fuel + regen).coerceAtMost(1.0)
        s.spareParts = (s.spareParts + regen).coerceAtMost(1.0)
        if (s.warEconomy) ctx.state.playerCountry.economy.pendingOneOffBillions += p.warEconomyCostBillionsPerMonth
        if (s.ammunition < CRITICAL_STOCK) {
            ctx.notifications.post(NotificationCategory.MILITARY, Urgency.URGENT, "Stocks nationaux de munitions au plus bas",
                "Achetez des munitions ou passez en économie de guerre.")
        }
    }

    /** Les armées étrangères entretiennent leur disponibilité selon leur budget. */
    private fun foreignMaintenance(ctx: SimulationContext) {
        val target = ctx.db.militaryParameters.aiUnitStartReadiness
        ctx.state.military.units.values.filter { it.countryId != ctx.state.player.countryId && !it.destroyed && !it.inCombat }
            .forEach { it.readiness = approach(it.readiness, target, ADJUSTMENT) }
    }

    private companion object {
        const val DEFENSE_DOMAIN = "defense"
        const val BASE_READINESS = 0.68
        const val FUNDING_WEIGHT = 0.8
        const val MINISTRY_WEIGHT = 0.3
        const val NEUTRAL = 0.5
        const val ADJUSTMENT = 0.15
        const val FATIGUE_PENALTY = 0.2
        const val REST_FATIGUE = 0.15
        const val CRITICAL_STOCK = 0.25
    }
}
