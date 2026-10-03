package fr.president.engine.ai

import fr.president.engine.diplomacy.Demand
import fr.president.engine.diplomacy.Perception
import fr.president.engine.diplomacy.RelationCalculator
import fr.president.engine.diplomacy.SanctionsService
import fr.president.engine.diplomacy.UltimatumService
import fr.president.engine.military.Geopolitics
import fr.president.engine.military.WarService
import fr.president.engine.politics.Traits
import fr.president.engine.simulation.SimulationContext

/**
 * Décisions stratégiques d'un gouvernement IA : sanctionner un agresseur, et — rarement —
 * entrer en guerre contre un voisin hostile qu'il croit plus faible. La dissuasion nucléaire
 * (du voisin ou de ses alliés) l'en dissuade. L'IA peut se tromper sur le rapport de forces.
 */
class WarDecisions(private val ctx: SimulationContext) {
    private val geo = Geopolitics(ctx)
    private val relations = RelationCalculator(ctx)

    fun run(country: String) {
        sanctionAggressors(country)
        considerWar(country)
    }

    private fun sanctionAggressors(country: String) {
        val sanctions = SanctionsService(ctx)
        for (war in geo.activeWars()) {
            val aggressor = war.attackers.first()
            if (country in war.participants || sanctions.isSanctioning(country, aggressor)) continue
            val victim = war.defenders.first()
            if (relations.score(country, aggressor) < SANCTION_RELATION && relations.score(country, victim) > relations.score(country, aggressor) + SANCTION_GAP) {
                sanctions.impose(country, aggressor, announce = false)
                ctx.log("ai.war", "$country sanctionne l'agresseur $aggressor")
            }
        }
    }

    private fun considerWar(country: String) {
        if (geo.isAtWar(country)) return
        val leader = ctx.state.characters.getValue(ctx.state.countries.getValue(country).leaderId)
        val aggressiveness = leader.trait(Traits.AGGRESSIVENESS)
        if (aggressiveness < MIN_AGGRESSIVENESS) return
        val perception = Perception(ctx)
        val ownPower = geo.landPower(country)
        for (target in geo.landNeighbors(country)) {
            if (!ctx.state.countries.containsKey(target)) continue
            val relation = relations.score(country, target)
            if (relation > HOSTILE_RELATION) continue
            // Dissuasion : la cible ou l'un de ses alliés défensifs dispose de l'arme nucléaire.
            val protectors = geo.defensivePartners(target) + target
            if (protectors.any { geo.isNuclear(it) }) {
                ctx.log("ai.war", "$country renonce à attaquer $target : dissuasion nucléaire")
                continue
            }
            val estimated = perception.estimate(country, target, "landPower", geo.landPower(target) + geo.defensivePartners(target).sumOf { geo.landPower(it) } * ALLY_SHARE, geo.landPower(target) + 1)
            val ratio = ownPower / (estimated.coerceAtLeast(1.0))
            ctx.log("ai.war", "$country évalue une guerre contre $target : relation %.2f, rapport perçu %.2f".format(relation, ratio))
            if (ratio < POWER_RATIO) continue
            if (!ctx.rng.chance(WAR_CHANCE * aggressiveness)) continue
            val result = UltimatumService(ctx).send(country, target, Demand.STOP_THREATS)
            if (!result.accepted) {
                WarService(ctx).declare(country, target, "${ctx.db.country(country).definition.name} attaque ${ctx.db.country(target).definition.name} après le rejet de son ultimatum.")
            }
            return
        }
    }

    private companion object {
        const val SANCTION_RELATION = 0.4
        const val SANCTION_GAP = 0.15
        const val MIN_AGGRESSIVENESS = 0.6
        const val HOSTILE_RELATION = 0.18
        const val ALLY_SHARE = 0.5
        const val POWER_RATIO = 1.6
        const val WAR_CHANCE = 0.04
    }
}
