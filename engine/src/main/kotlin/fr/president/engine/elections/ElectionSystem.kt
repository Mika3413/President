package fr.president.engine.elections

import fr.president.engine.simulation.Cadence
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.simulation.SimulationSystem

/** Sondages réguliers d'intentions de vote. Les tours de scrutin sont planifiés à part. */
class ElectionSystem : SimulationSystem {
    override val name = "elections"
    override val cadence = Cadence.DAILY

    override fun run(ctx: SimulationContext) {
        val state = ctx.state.elections
        val interval = ctx.db.config.simulation.pollIntervalDays.toDouble()
        val last = state.lastPollAt
        if (last == null || last.daysUntil(ctx.now) >= interval) ElectionService(ctx).poll()
    }
}
