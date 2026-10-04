package fr.president.engine.events

import fr.president.engine.simulation.Cadence
import fr.president.engine.simulation.ScheduledAction
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.simulation.SimulationSystem
import fr.president.engine.time.WorldTime
import fr.president.engine.util.lerp
import fr.president.engine.util.inverseLerp

/**
 * Tirage quotidien des événements. Rien n'est purement aléatoire : la probabilité de chaque
 * événement est le produit de sa probabilité de base et de modificateurs liés à l'état du monde
 * (entretien d'une centrale, chômage local, saison, opinion...).
 */
class EventSystem : SimulationSystem {
    override val name = "events"
    override val cadence = Cadence.DAILY

    override fun run(ctx: SimulationContext) {
        for (def in ctx.db.events) {
            if (onCooldown(def.cooldownDays, ctx.state.events.lastFired[def.id], ctx.now)) continue
            val fired = candidates(ctx, def).firstOrNull { scope ->
                eligible(ctx, def, scope) && ctx.rng.chance(probability(ctx, def, scope))
            } ?: continue
            // Réservation immédiate (pas de doublon), survenue à une heure tirée dans la journée.
            ctx.state.events.lastFired[def.id] = ctx.now
            fired.id?.let { ctx.state.events.lastFiredScope["${def.id}:$it"] = ctx.now }
            val hour = FIRST_HOUR + ctx.rng.nextInt(LAST_HOUR - FIRST_HOUR)
            ctx.scheduler.schedule(ScheduledAction.EventLaunch(ctx.now.plusHours(hour.toLong()), def.id, fired.id))
        }
    }

    fun probability(ctx: SimulationContext, def: EventDefinition, scope: ScopeRef): Double {
        var p = def.baseDailyProbability
        for (m in def.modifiers) {
            val value = ctx.variables.resolve(m.variable, scope) ?: continue
            p *= lerp(m.factorAtFrom, m.factorAtTo, inverseLerp(m.from, m.to, value))
        }
        // Prévention et mesures de crise en vigueur.
        p *= fr.president.engine.crisis.MeasureSystem.probabilityFactor(ctx, def, scope)
        return p.coerceIn(0.0, MAX_DAILY_PROBABILITY)
    }

    /**
     * Risque quotidien d'un événement (toutes cibles confondues) et la cible la plus exposée.
     * Sert à l'affichage des risques ; ne tire rien au hasard.
     */
    fun dailyRisk(ctx: SimulationContext, def: EventDefinition): Pair<Double, ScopeRef?> {
        if (onCooldown(def.cooldownDays, ctx.state.events.lastFired[def.id], ctx.now)) return 0.0 to null
        var none = 1.0
        var best: ScopeRef? = null
        var bestP = 0.0
        // Sans mélange : l'affichage des risques ne doit pas consommer le hasard de la simulation.
        for (scope in candidates(ctx, def, shuffle = false)) {
            if (!eligible(ctx, def, scope)) continue
            val p = probability(ctx, def, scope)
            none *= 1 - p
            if (p > bestP) { bestP = p; best = scope }
        }
        return (1 - none) to best
    }

    private fun eligible(ctx: SimulationContext, def: EventDefinition, scope: ScopeRef): Boolean {
        if (scope.id != null &&
            onCooldown(def.scopeCooldownDays, ctx.state.events.lastFiredScope["${def.id}:${scope.id}"], ctx.now)
        ) return false
        if (scope.type == EventScope.INFRASTRUCTURE && ctx.state.infrastructure[scope.id]?.isOperational(ctx.now) == false) return false
        return def.conditions.all { c ->
            val v = ctx.variables.resolve(c.variable, scope) ?: return@all false
            (c.min == null || v >= c.min) && (c.max == null || v <= c.max) && (c.oneOf.isEmpty() || v in c.oneOf)
        }
    }

    /** Cibles possibles, dans un ordre mélangé pour ne favoriser aucun territoire. */
    private fun candidates(ctx: SimulationContext, def: EventDefinition, shuffle: Boolean = true): List<ScopeRef> {
        val ids: List<String?> = when (def.scope) {
            EventScope.NATIONAL -> listOf(null)
            EventScope.DEPARTMENT -> ctx.state.territory.departments.keys.toList()
            EventScope.CITY -> ctx.playerData.territory!!.cities.filter { it.rank <= def.maxCityRank }.map { it.id }
            EventScope.INFRASTRUCTURE -> ctx.state.infrastructure.values
                .filter { def.infraTypes.isEmpty() || it.type in def.infraTypes }.filter { !it.closed }.map { it.id }
            EventScope.MINISTER -> ctx.state.government.ministers.values.toList()
            EventScope.FOREIGN_COUNTRY -> ctx.state.countries.keys.filter { it != ctx.state.player.countryId }
        }
        return (if (shuffle) shuffled(ids, ctx) else ids).map { ScopeRef(def.scope, it) }
    }

    private fun <T> shuffled(items: List<T>, ctx: SimulationContext): List<T> {
        val list = items.toMutableList()
        for (i in list.size - 1 downTo 1) {
            val j = ctx.rng.nextInt(i + 1)
            val tmp = list[i]; list[i] = list[j]; list[j] = tmp
        }
        return list
    }

    private fun onCooldown(days: Double, last: WorldTime?, now: WorldTime): Boolean =
        last != null && days > 0 && last.daysUntil(now) < days

    private companion object {
        const val MAX_DAILY_PROBABILITY = 0.5
        const val FIRST_HOUR = 6
        const val LAST_HOUR = 23
    }
}
