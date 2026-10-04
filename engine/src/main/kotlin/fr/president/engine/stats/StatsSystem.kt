package fr.president.engine.stats

import fr.president.engine.diplomacy.RelationCalculator
import fr.president.engine.simulation.Cadence
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.simulation.SimulationSystem
import fr.president.engine.util.History

/** Relève chaque semaine les chiffres clés pour tracer les courbes du mandat. */
class StatsSystem : SimulationSystem {
    override val name = "stats"
    override val cadence = Cadence.DAILY

    override fun run(ctx: SimulationContext) {
        val stats = ctx.state.stats
        val day = ctx.now.dayIndex
        if (stats.lastRecordDay != Long.MIN_VALUE && day - stats.lastRecordDay < DAYS_PER_POINT) return
        stats.lastRecordDay = day
        record(ctx)
    }

    companion object {
        const val DAYS_PER_POINT = 7
        /** Cinq ans et quelques semaines : un mandat entier tient dans une courbe. */
        const val CAPACITY = 270

        fun record(ctx: SimulationContext) {
            val s = ctx.state
            val e = s.playerCountry.economy
            fun put(key: String, value: Double) = s.stats.series.getOrPut(key) { History(CAPACITY) }.push(value)
            put("approval", s.opinion.nationalApproval)
            put("unemployment", e.unemployment)
            put("growth", e.realGrowth)
            put("inflation", e.inflation)
            put("deficit", e.deficitRatio)
            put("debt", e.debtRatio)
            put("parliament", s.government.parliamentSupport)
            put("readiness", s.military.overallReadiness)
            s.opinion.groups.forEach { (id, g) -> put("group.$id", g.effective) }
            val player = s.player.countryId
            val relations = RelationCalculator(ctx)
            s.countries.keys.filter { it != player }.forEach { put("relation.$it", relations.score(it, player)) }
        }
    }
}
