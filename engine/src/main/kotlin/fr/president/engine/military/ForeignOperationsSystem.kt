package fr.president.engine.military

import fr.president.engine.diplomacy.RelationCalculator
import fr.president.engine.politics.Traits
import fr.president.engine.simulation.Cadence
import fr.president.engine.simulation.ScheduledAction
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.simulation.SimulationSystem

/**
 * Ce que font les pays IA au-delà des combats terrestres, de façon réaliste :
 * - en guerre, ils frappent les concentrations ennemies à portée de leurs avions et navires et
 *   mènent des cyberattaques ;
 * - en paix, un pays hostile et dirigé par un chef agressif peut lancer contre la France des
 *   attaques hybrides (cyberattaque, sabotage) qui restent sous le seuil de la guerre : la
 *   dissuasion nucléaire française décourage toute attaque directe.
 */
class ForeignOperationsSystem : SimulationSystem {
    override val name = "foreign-operations"
    override val cadence = Cadence.DAILY

    override fun run(ctx: SimulationContext) {
        val geo = Geopolitics(ctx)
        val ops = OperationsService(ctx)
        val player = ctx.state.player.countryId
        for (war in geo.activeWars()) {
            for (actor in war.participants.filter { it != player && it in ctx.state.countries }) {
                for (enemy in geo.enemiesOf(actor)) {
                    if (ctx.rng.chance(STRIKE_CHANCE)) ops.aiStrike(actor, enemy)
                    if (ctx.rng.chance(CYBER_CHANCE)) ops.aiCyber(actor, enemy)
                }
            }
        }
        hybridThreats(ctx, geo, player)
    }

    private fun hybridThreats(ctx: SimulationContext, geo: Geopolitics, player: String) {
        val relations = RelationCalculator(ctx)
        val scheduled = ctx.state.scheduler.actions.any { it is ScheduledAction.EventLaunch && it.definitionId in HYBRID_EVENTS }
        if (scheduled) return
        for (country in ctx.state.countries.keys.filter { it != player }) {
            if (geo.atWar(country, player)) continue
            val last = ctx.state.localActions["$KEY$country"]
            if (last != null && last.daysUntil(ctx.now) < COOLDOWN_DAYS) continue
            val relation = relations.score(country, player)
            if (relation > HOSTILE) continue
            val leader = ctx.state.characters[ctx.state.countries.getValue(country).leaderId] ?: continue
            val aggressiveness = leader.trait(Traits.AGGRESSIVENESS)
            if (aggressiveness < MIN_AGGRESSIVENESS) continue
            // Plus le pays est hostile et son dirigeant agressif, plus l'attaque est probable.
            val chance = BASE_CHANCE * aggressiveness * (1 + (HOSTILE - relation) * HOSTILITY_WEIGHT)
            if (!ctx.rng.chance(chance)) continue
            val event = HYBRID_EVENTS[ctx.rng.nextInt(HYBRID_EVENTS.size)]
            ctx.state.localActions["$KEY$country"] = ctx.now
            ctx.scheduler.schedule(ScheduledAction.EventLaunch(ctx.now.plusHours(1), event, country))
            ctx.log("ai.hybrid", "$country prépare une attaque hybride ($event)")
            return
        }
    }

    private companion object {
        const val STRIKE_CHANCE = 0.12
        const val CYBER_CHANCE = 0.04
        const val KEY = "hybrid|"
        const val COOLDOWN_DAYS = 120.0
        const val HOSTILE = 0.3
        const val MIN_AGGRESSIVENESS = 0.45
        const val BASE_CHANCE = 0.004
        const val HOSTILITY_WEIGHT = 4.0
        val HYBRID_EVENTS = listOf("hybrid_cyber", "hybrid_sabotage")
    }
}
