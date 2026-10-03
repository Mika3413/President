package fr.president.engine.simulation

import fr.president.engine.time.WorldTime

/**
 * Boucle de simulation par rattrapage : simulate(from, to) avance l'état heure par heure
 * du monde, en déclenchant les systèmes quotidiens et mensuels aux changements de jour/mois
 * et les actions planifiées à leur instant. Ne dépend jamais du temps réel.
 */
class Simulator(
    private val ctx: SimulationContext,
    private val systems: List<SimulationSystem> = Systems.default(),
) {
    data class Report(val from: WorldTime, val to: WorldTime, val hours: Long, val notifications: Int) {
        val days: Double get() = from.daysUntil(to)
    }

    private val hourly = systems.filter { it.cadence == Cadence.HOURLY }
    private val daily = systems.filter { it.cadence == Cadence.DAILY }
    private val monthly = systems.filter { it.cadence == Cadence.MONTHLY }
    private val dispatcher = ActionDispatcher(ctx)

    fun advanceTo(target: WorldTime): Report {
        val state = ctx.state
        val from = state.time
        val feedBefore = state.notifications.feed.lastOrNull()?.id ?: 0L
        val limit = from.plusDays(ctx.db.config.simulation.maxCatchUpDays.toLong())
        val end = if (target > limit) limit.also { ctx.log("simulator", "Rattrapage limité à ${ctx.db.config.simulation.maxCatchUpDays} jours") } else target
        var hours = 0L
        while (state.time < end && state.player.gameOver == null) {
            val nextHour = WorldTime((state.time.hourIndex + 1) * WorldTime.SECONDS_PER_HOUR)
            if (nextHour > end) {
                state.time = end
                break
            }
            step(nextHour)
            hours++
        }
        val newNotifications = state.notifications.feed.count { it.id > feedBefore }
        return Report(from, state.time, hours, newNotifications)
    }

    private fun step(next: WorldTime) {
        val previous = ctx.state.time
        ctx.state.time = next
        ctx.scheduler.takeDue(next).forEach { dispatcher.dispatch(it) }
        hourly.forEach { it.run(ctx) }
        if (next.dayIndex != previous.dayIndex) daily.forEach { it.run(ctx) }
        if (next.monthIndex != previous.monthIndex) monthly.forEach { it.run(ctx) }
    }
}
