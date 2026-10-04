package fr.president.engine.crisis

import fr.president.engine.effects.EffectSpec
import fr.president.engine.events.EventDefinition
import fr.president.engine.events.ScopeRef
import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import fr.president.engine.readout.Tone
import fr.president.engine.simulation.Cadence
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.simulation.SimulationSystem
import fr.president.engine.stats.JournalService

/**
 * Applique chaque jour les mesures actives et lève celles qui arrivent à échéance.
 * Une mesure contraignante s'use : la population la respecte de moins en moins, elle protège
 * moins et coûte davantage en popularité. Un régime d'exception doit être prorogé par le
 * Parlement après 12 jours, et le Conseil d'État peut suspendre une mesure disproportionnée.
 */
class MeasureSystem : SimulationSystem {
    override val name = "measures"
    override val cadence = Cadence.DAILY

    override fun run(ctx: SimulationContext) {
        val state = ctx.state.measures
        if (state.active.isEmpty()) return
        val defs = definitions(ctx)
        for (m in state.active.toList()) {
            val def = defs[m.id] ?: continue
            wear(ctx, def, m)
            // Plus la lassitude est forte, plus les contraintes pèsent sur l'opinion.
            val weariness = 2 - m.compliance
            def.daily.forEach { spec ->
                val resolved = resolve(spec, m.department)
                val scaled = if (isOpinion(resolved.target) && resolved.amount < 0) resolved.copy(amount = resolved.amount * weariness) else resolved
                ctx.effects.trigger(scaled, null, emptyMap(), "measure:${def.id}")
            }
            if (def.costPerMonthBillions > 0) {
                ctx.effects.trigger(EffectSpec("budget.oneOff", def.costPerMonthBillions / DAYS_PER_MONTH), null, emptyMap(), "measure:${def.id}")
            }
            val commands = MeasureCommands(ctx)
            when {
                m.endsAt != null && ctx.now >= m.endsAt -> commands.end(m, expired = true)
                parliamentVoteDue(ctx, def, m) -> parliamentVote(ctx, def, m, commands)
                def.contestable && ctx.rng.chance(suspensionRisk(ctx, def, m)) -> suspend(ctx, def, m, commands)
            }
        }
    }

    /** Le respect s'érode chaque jour ; deux seuils déclenchent une alerte. */
    private fun wear(ctx: SimulationContext, def: MeasureDef, m: ActiveMeasure) {
        if (def.fatigue <= 0) return
        val before = m.compliance
        m.compliance = (m.compliance - def.fatigue / DAYS_PER_MONTH).coerceAtLeast(MIN_COMPLIANCE)
        val where = place(ctx, m.department)
        if (before >= WEARY && m.compliance < WEARY) {
            notify(ctx, "${def.label}$where : la lassitude gagne", "La mesure est de moins en moins respectée (${percent(m.compliance)}). Elle protège moins et coûte davantage en popularité.", m.department)
        }
        if (before >= EXHAUSTED && m.compliance < EXHAUSTED) {
            notify(ctx, "${def.label}$where : la population n'en peut plus", "Seuls ${percent(m.compliance)} des Français respectent encore la mesure. Des rassemblements de protestation s'organisent.", m.department)
            ctx.effects.trigger(EffectSpec("chain.city_demonstration", PROTEST_CHANCE, delayDays = PROTEST_DELAY), null, emptyMap(), "measure:${def.id}")
        }
    }

    private fun parliamentVoteDue(ctx: SimulationContext, def: MeasureDef, m: ActiveMeasure) =
        def.emergency && !m.extended && m.startedAt.daysUntil(ctx.now) >= EMERGENCY_DAYS &&
            (m.endsAt == null || ctx.now.daysUntil(m.endsAt) > 0.5)

    private fun parliamentVote(ctx: SimulationContext, def: MeasureDef, m: ActiveMeasure, commands: MeasureCommands) {
        val chance = extensionChance(ctx, m)
        val journal = JournalService(ctx)
        if (ctx.rng.chance(chance)) {
            m.extended = true
            ctx.effects.trigger(EffectSpec("government.parliamentSupport", -VOTE_COST), null, emptyMap(), "measure:${def.id}")
            journal.add("Parlement", "Prorogation votée : ${def.label}", Tone.NEUTRAL)
            ctx.notifications.post(NotificationCategory.POLITICS, Urgency.INFO, "Le Parlement proroge : ${def.label.lowercase()}",
                "Les députés ont voté la prolongation du régime d'exception. La mesure peut durer.", m.department, journal = false)
        } else {
            commands.end(m, expired = false, reason = "rejet du Parlement")
            ctx.effects.trigger(EffectSpec("government.parliamentSupport", -VOTE_DEFEAT), null, emptyMap(), "measure:${def.id}")
            ctx.effects.trigger(EffectSpec("president.popularity", -VOTE_DEFEAT), null, emptyMap(), "measure:${def.id}")
            ctx.notifications.post(NotificationCategory.POLITICS, Urgency.IMPORTANT, "Le Parlement refuse de proroger : ${def.label.lowercase()}",
                "Faute de majorité, le régime d'exception prend fin. La mesure est levée.", m.department)
        }
    }

    /** Recours devant le Conseil d'État : plus probable si la menace a reculé ou si la mesure est mal respectée. */
    private fun suspend(ctx: SimulationContext, def: MeasureDef, m: ActiveMeasure, commands: MeasureCommands) {
        commands.end(m, expired = false, reason = "suspendue par le Conseil d'État")
        ctx.effects.trigger(EffectSpec("president.popularity", -SUSPENSION_COST), null, emptyMap(), "measure:${def.id}")
        ctx.notifications.post(NotificationCategory.POLITICS, Urgency.IMPORTANT, "Le Conseil d'État suspend : ${def.label.lowercase()}${place(ctx, m.department)}",
            "Saisi en référé par des associations, le juge estime la mesure disproportionnée au regard de la menace actuelle.", m.department)
    }

    companion object {
        const val DAYS_PER_MONTH = 30.0
        const val EMERGENCY_DAYS = 12.0
        const val MIN_COMPLIANCE = 0.25
        const val WEARY = 0.6
        const val EXHAUSTED = 0.4
        private const val PROTEST_CHANCE = 0.6
        private const val PROTEST_DELAY = 5.0
        private const val VOTE_COST = 0.005
        private const val VOTE_DEFEAT = 0.01
        private const val SUSPENSION_COST = 0.004
        private const val BASE_SUSPENSION = 0.002
        private const val RECENT_THREAT_DAYS = 60.0
        private const val NO_THREAT_FACTOR = 4.0
        private const val THREAT_FACTOR = 0.5

        fun definitions(ctx: SimulationContext): Map<String, MeasureDef> =
            ctx.playerData.measures?.measures.orEmpty().associateBy { it.id }

        fun resolve(spec: EffectSpec, department: String?): EffectSpec =
            if (department != null && spec.target.startsWith("local.")) spec.copy(target = "dept.$department." + spec.target.removePrefix("local.")) else spec

        private fun isOpinion(target: String) = target.startsWith("opinion.") || target.endsWith(".approval")

        /** Chances que le Parlement proroge le régime d'exception. */
        fun extensionChance(ctx: SimulationContext, m: ActiveMeasure): Double =
            (EXT_BASE + ctx.state.government.parliamentSupport * EXT_SUPPORT - (1 - m.compliance) * EXT_WEARINESS).coerceIn(EXT_MIN, EXT_MAX)

        /** Risque quotidien de suspension par le Conseil d'État. */
        fun suspensionRisk(ctx: SimulationContext, def: MeasureDef, m: ActiveMeasure): Double {
            if (!def.contestable) return 0.0
            val threatened = def.affects.any { i ->
                ctx.state.events.lastFired[i.event]?.let { it.daysUntil(ctx.now) < RECENT_THREAT_DAYS } == true
            }
            return BASE_SUSPENSION * (if (threatened) THREAT_FACTOR else NO_THREAT_FACTOR) * (1 + (1 - m.compliance) * 2)
        }

        private const val EXT_BASE = 0.2
        private const val EXT_SUPPORT = 1.0
        private const val EXT_WEARINESS = 0.6
        private const val EXT_MIN = 0.05
        private const val EXT_MAX = 0.95

        /** Multiplicateur de probabilité d'un événement selon les mesures actives (nationales ou du même département). */
        fun probabilityFactor(ctx: SimulationContext, def: EventDefinition, scope: ScopeRef?): Double =
            impacts(ctx, def, scope).fold(1.0) { acc, (i, c) -> acc * effective(i.probability, c) }

        fun intensityFactor(ctx: SimulationContext, def: EventDefinition, scope: ScopeRef?): Double =
            impacts(ctx, def, scope).fold(1.0) { acc, (i, c) -> acc * effective(i.intensity, c) }

        /** Une mesure mal respectée ne protège qu'en partie. */
        private fun effective(factor: Double, compliance: Double) = if (factor < 1) 1 - (1 - factor) * compliance else factor

        private fun impacts(ctx: SimulationContext, def: EventDefinition, scope: ScopeRef?): List<Pair<EventImpact, Double>> {
            val active = ctx.state.measures.active
            if (active.isEmpty()) return emptyList()
            val defs = definitions(ctx)
            val dept = scope?.id?.let { id -> fr.president.engine.events.SenderResolver(ctx).departmentOf(scope) ?: id }
            // Une mesure locale freine aussi un peu un événement national (épidémie...), pas celui d'un autre département.
            return active.filter { it.department == null || dept == null || it.department == dept }
                .flatMap { m -> defs[m.id]?.affects.orEmpty().filter { it.event == def.id }.map { it to m.compliance } }
        }

        fun notify(ctx: SimulationContext, title: String, body: String, focus: String?) {
            ctx.notifications.post(NotificationCategory.SECURITY, Urgency.IMPORTANT, title, body, focus, journal = false)
        }

        private fun place(ctx: SimulationContext, code: String?) =
            code?.let { c -> ctx.playerData.territory?.departments?.firstOrNull { it.code == c }?.name }?.let { " ($it)" } ?: ""

        private fun percent(v: Double) = "${Math.round(v * 100)} %"
    }
}
