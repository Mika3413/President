package fr.president.engine.economy

import fr.president.engine.government.MinistryEffectiveness
import fr.president.engine.simulation.Cadence
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.simulation.SimulationSystem
import fr.president.engine.util.approach
import fr.president.engine.util.clamp01

/**
 * Qualité des services publics : dépend du financement réel (inflation comprise)
 * et de l'efficacité du ministre en charge. Évolue lentement.
 */
class ServiceQualitySystem : SimulationSystem {
    override val name = "services"
    override val cadence = Cadence.MONTHLY

    override fun run(ctx: SimulationContext) {
        val p = ctx.db.economyParameters
        val country = ctx.state.playerCountry
        val effectiveness = MinistryEffectiveness(ctx)
        for ((domain, quality) in country.services.toMap()) {
            val start = ctx.state.opinion.startValues["$START_PREFIX$domain"] ?: quality
            val funding = BudgetCalculator.realFundingRatio(country.economy, domain) ?: 1.0
            val minister = effectiveness.forDomain(domain)
            val target = (start + p.serviceQualityFundingSensitivity * (funding - 1.0) +
                p.serviceQualityMinisterSensitivity * (minister - NEUTRAL)).clamp01()
            country.services[domain] = approach(quality, target, p.serviceQualityAdjustmentMonthly)
        }
    }

    companion object {
        const val START_PREFIX = "quality.start."
        private const val NEUTRAL = 0.5
    }
}
