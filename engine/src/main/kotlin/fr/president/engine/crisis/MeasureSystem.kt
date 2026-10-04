package fr.president.engine.crisis

import fr.president.engine.effects.EffectSpec
import fr.president.engine.events.EventDefinition
import fr.president.engine.events.ScopeRef
import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import fr.president.engine.simulation.Cadence
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.simulation.SimulationSystem

/** Applique chaque jour les mesures actives et lève celles qui arrivent à échéance. */
class MeasureSystem : SimulationSystem {
    override val name = "measures"
    override val cadence = Cadence.DAILY

    override fun run(ctx: SimulationContext) {
        val state = ctx.state.measures
        if (state.active.isEmpty()) return
        val defs = definitions(ctx)
        for (m in state.active.toList()) {
            val def = defs[m.id] ?: continue
            def.daily.forEach { ctx.effects.trigger(resolve(it, m.department), null, emptyMap(), "measure:${def.id}") }
            if (def.costPerMonthBillions > 0) {
                ctx.effects.trigger(EffectSpec("budget.oneOff", def.costPerMonthBillions / DAYS_PER_MONTH), null, emptyMap(), "measure:${def.id}")
            }
            if (m.endsAt != null && ctx.now >= m.endsAt) MeasureCommands(ctx).end(m, expired = true)
        }
    }

    companion object {
        const val DAYS_PER_MONTH = 30.0

        fun definitions(ctx: SimulationContext): Map<String, MeasureDef> =
            ctx.playerData.measures?.measures.orEmpty().associateBy { it.id }

        fun resolve(spec: EffectSpec, department: String?): EffectSpec =
            if (department != null && spec.target.startsWith("local.")) spec.copy(target = "dept.$department." + spec.target.removePrefix("local.")) else spec

        /** Multiplicateur de probabilité d'un événement selon les mesures actives (nationales ou du même département). */
        fun probabilityFactor(ctx: SimulationContext, def: EventDefinition, scope: ScopeRef?): Double =
            impacts(ctx, def, scope).fold(1.0) { acc, i -> acc * i.probability }

        fun intensityFactor(ctx: SimulationContext, def: EventDefinition, scope: ScopeRef?): Double =
            impacts(ctx, def, scope).fold(1.0) { acc, i -> acc * i.intensity }

        private fun impacts(ctx: SimulationContext, def: EventDefinition, scope: ScopeRef?): List<EventImpact> {
            val active = ctx.state.measures.active
            if (active.isEmpty()) return emptyList()
            val defs = definitions(ctx)
            val dept = scope?.id?.let { id -> fr.president.engine.events.SenderResolver(ctx).departmentOf(scope) ?: id }
            // Une mesure locale freine aussi un peu un événement national (épidémie...), pas celui d'un autre département.
            return active.filter { it.department == null || dept == null || it.department == dept }
                .flatMap { m -> defs[m.id]?.affects.orEmpty().filter { it.event == def.id } }
        }

        fun notify(ctx: SimulationContext, title: String, body: String, focus: String?) {
            ctx.notifications.post(NotificationCategory.SECURITY, Urgency.IMPORTANT, title, body, focus, journal = false)
        }
    }
}
