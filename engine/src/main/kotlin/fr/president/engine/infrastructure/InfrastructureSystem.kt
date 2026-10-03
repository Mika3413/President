package fr.president.engine.infrastructure

import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import fr.president.engine.simulation.Cadence
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.simulation.SimulationSystem
import fr.president.engine.time.WorldTime

/**
 * Vieillissement quotidien des infrastructures selon l'entretien décidé.
 * Les incidents eux-mêmes sont des événements dont la probabilité dépend de l'état (voir EventSystem).
 */
class InfrastructureSystem : SimulationSystem {
    override val name = "infrastructure"
    override val cadence = Cadence.DAILY

    override fun run(ctx: SimulationContext) {
        var extraMaintenanceBillions = 0.0
        for (infra in ctx.state.infrastructure.values) {
            if (infra.closed) continue
            val type = ctx.db.infrastructureTypes[infra.type] ?: continue
            val def = ctx.catalog.item(infra.id) ?: continue
            // Entretien normal (1.0) : usure nominale ; entretien renforcé : l'état peut s'améliorer.
            val dailyWear = type.degradationPerYear / WorldTime.DAYS_PER_YEAR
            infra.condition = (infra.condition - dailyWear * (WEAR_PIVOT - infra.maintenanceLevel)).coerceIn(0.0, 1.0)
            extraMaintenanceBillions += (infra.maintenanceLevel - 1.0) * def.maintenanceCostMillions /
                MILLIONS_PER_BILLION / WorldTime.DAYS_PER_YEAR
            checkRestart(ctx, infra, def.name)
        }
        ctx.state.playerCountry.economy.pendingOneOffBillions += extraMaintenanceBillions
    }

    private fun checkRestart(ctx: SimulationContext, infra: InfrastructureState, name: String) {
        val until = infra.offlineUntil ?: return
        if (ctx.now >= until) {
            infra.offlineUntil = null
            ctx.notifications.post(
                NotificationCategory.ENERGY, Urgency.INFO,
                "$name remis en service", "L'installation fonctionne de nouveau normalement.", infra.id,
            )
        }
    }

    private companion object {
        /** Avec un entretien à 2.0, l'usure s'annule ; au-delà, l'état s'améliore. */
        const val WEAR_PIVOT = 2.0
        const val MILLIONS_PER_BILLION = 1000.0
    }
}
