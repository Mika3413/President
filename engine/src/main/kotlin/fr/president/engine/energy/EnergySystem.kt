package fr.president.engine.energy

import fr.president.engine.simulation.Cadence
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.simulation.SimulationSystem
import fr.president.engine.util.approach

/**
 * Bilan électrique mensuel : production des centrales (selon leur état et leur disponibilité),
 * demande (selon l'activité économique), engagements internationaux, puis prix de l'énergie.
 */
class EnergySystem : SimulationSystem {
    override val name = "energy"
    override val cadence = Cadence.MONTHLY

    override fun run(ctx: SimulationContext) {
        val energyData = ctx.playerData.energy ?: return
        val state = ctx.state.energy
        val economy = ctx.state.playerCountry.economy
        val params = ctx.db.economyParameters

        state.productionTWh = production(ctx)
        val realActivity = economy.gdpBillions / (economy.reference.gdpBillions * economy.priceLevel)
        state.demandTWh = energyData.electricityDemandTWh * Math.pow(realActivity, DEMAND_ELASTICITY)
        state.netExportTWh = state.productionTWh - state.demandTWh
        state.margin = (state.netExportTWh - state.committedExportTWh) / state.demandTWh

        val referenceMargin = ctx.state.opinion.startValues[REFERENCE_MARGIN_KEY] ?: state.margin
        val priceTarget = (1.0 + params.energyPriceMarginSensitivity * (referenceMargin - state.margin))
            .coerceIn(MIN_PRICE_INDEX, MAX_PRICE_INDEX)
        state.priceIndex = approach(state.priceIndex, priceTarget, PRICE_ADJUSTMENT)
        economy.energyPriceIndex = state.priceIndex
    }

    companion object {
        const val REFERENCE_MARGIN_KEY = "energy.referenceMargin"
        private const val HOURS_PER_YEAR = 8766.0
        private const val MWH_PER_TWH = 1_000_000.0
        private const val DEMAND_ELASTICITY = 0.5
        private const val MIN_PRICE_INDEX = 0.7
        private const val MAX_PRICE_INDEX = 3.0
        private const val PRICE_ADJUSTMENT = 0.5
        /** Une installation dégradée subit plus d'arrêts : disponibilité = base + part * état. */
        private const val AVAILABILITY_BASE = 0.55
        private const val AVAILABILITY_CONDITION_SHARE = 0.45

        fun production(ctx: SimulationContext): Double {
            val energyData = ctx.playerData.energy ?: return 0.0
            var mwh = 0.0
            for (def in energyData.items) {
                val infra = ctx.state.infrastructure[def.id] ?: continue
                if (!infra.isOperational(ctx.now)) continue
                val availability = AVAILABILITY_BASE + AVAILABILITY_CONDITION_SHARE * infra.condition
                mwh += def.capacityMW * def.capacityFactor * availability * HOURS_PER_YEAR
            }
            for (aggregate in energyData.nationalGeneration) {
                val capacity = aggregate.capacityMW + (ctx.state.energy.extraCapacityMW[aggregate.id] ?: 0.0)
                mwh += capacity * aggregate.capacityFactor * HOURS_PER_YEAR
            }
            return mwh / MWH_PER_TWH
        }
    }
}
