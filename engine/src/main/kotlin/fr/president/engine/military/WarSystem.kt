package fr.president.engine.military

import fr.president.engine.data.theCountry
import fr.president.engine.data.ofCountry
import fr.president.engine.data.toCountry

import fr.president.engine.diplomacy.Clause
import fr.president.engine.diplomacy.DiplomacyService
import fr.president.engine.diplomacy.ProposalStatus
import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import fr.president.engine.simulation.Cadence
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.simulation.SimulationSystem
import fr.president.engine.world.GameOver

/**
 * Suivi quotidien des guerres : coûts, lassitude, fin des cessez-le-feu, recherche de la paix
 * par l'IA, capitulation d'un pays dont la capitale est tombée.
 */
class WarSystem : SimulationSystem {
    override val name = "wars"
    override val cadence = Cadence.DAILY

    override fun run(ctx: SimulationContext) {
        val geo = Geopolitics(ctx)
        val p = ctx.db.militaryParameters
        val player = ctx.state.player.countryId
        val military = ctx.state.military
        for (war in military.wars.filter { it.status != WarStatus.ENDED }) {
            if (war.status == WarStatus.CEASEFIRE && war.ceasefireUntil?.let { ctx.now >= it } == true) {
                war.status = WarStatus.ACTIVE
                ctx.notifications.post(NotificationCategory.MILITARY, Urgency.URGENT, "Fin du cessez-le-feu", "Les hostilités reprennent faute de traité de paix.")
            }
            for (country in war.participants) {
                val total = war.casualties[country] ?: 0
                val delta = total - (war.casualtiesCounted[country] ?: 0)
                war.casualtiesCounted[country] = total
                val w = (war.weariness[country] ?: 0.0) + p.warWearinessPerDay + delta * p.warWearinessPerCasualty * sizeFactor(ctx, country)
                war.weariness[country] = w.coerceIn(0.0, 1.0)
            }
            checkCapitulation(ctx, geo, war)
            if (war.status == WarStatus.ACTIVE) seekPeace(ctx, geo, war)
        }
        military.wars.filter { it.status != WarStatus.ENDED && (it.attackers.isEmpty() || it.defenders.isEmpty()) }
            .forEach { it.status = WarStatus.ENDED; it.endedAt = ctx.now }
        val playerWeariness = military.wars.filter { it.status != WarStatus.ENDED }.mapNotNull { it.weariness[player] }.maxOrNull()
        military.warWeariness = playerWeariness ?: (military.warWeariness - p.warWearinessDecayPerDay).coerceAtLeast(0.0)
        upkeep(ctx, geo)
        if (ctx.now.toDateTime().dayOfMonth == 1) mobilizeForeign(ctx, geo)
        capitalCheck(ctx, geo)
    }

    /** Un petit pays ressent davantage chaque perte. */
    private fun sizeFactor(ctx: SimulationContext, country: String): Double =
        (REFERENCE_POPULATION / ctx.state.countries.getValue(country).population.toDouble()).coerceIn(MIN_SIZE_FACTOR, MAX_SIZE_FACTOR)

    private fun upkeep(ctx: SimulationContext, geo: Geopolitics) {
        val player = ctx.state.player.countryId
        val cost = ctx.state.military.units.values.filter { it.countryId == player && !it.destroyed }
            .filter { it.inCombat || it.path.isNotEmpty() || geo.ownerOf(it.zoneId) != player || it.order == UnitOrder.SUPPORT || it.order == UnitOrder.PATROL }
            .sumOf { ctx.db.unitType(it.type).upkeepMillionsPerDayDeployed } / MILLIONS_PER_BILLION
        ctx.state.playerCountry.economy.pendingOneOffBillions += cost
    }

    private fun capitalZone(ctx: SimulationContext, country: String): String? {
        val capital = ctx.db.country(country).definition.strategic.capital ?: return null
        return ctx.db.zones.nearest(capital.lon, capital.lat) { !it.sea && it.owner == country }?.id
    }

    private fun checkCapitulation(ctx: SimulationContext, geo: Geopolitics, war: War) {
        val player = ctx.state.player.countryId
        for (country in war.participants.filter { it != player }) {
            val capital = capitalZone(ctx, country) ?: continue
            val fallen = geo.atWar(country, geo.controllerOf(capital))
            val weariness = war.weariness[country] ?: 0.0
            if (fallen && weariness > CAPITULATION_WEARINESS) {
                val side = war.sideOf(country)
                val leaders = if (side == War.ATTACKER) war.attackers else war.defenders
                if (country == leaders.first()) {
                    WarService(ctx).peace(war, keepOccupied = true, outcome = "${ctx.db.country(country).definition.name} capitule après la chute de sa capitale.")
                    return
                }
                leaders.remove(country)
                ctx.notifications.post(NotificationCategory.MILITARY, Urgency.IMPORTANT, "${ctx.db.country(country).definition.name} se retire du conflit", "Sa capitale est tombée.")
            }
        }
    }

    /** L'IA lasse propose un cessez-le-feu ou une paix ; entre IA, l'accord est évalué directement. */
    private fun seekPeace(ctx: SimulationContext, geo: Geopolitics, war: War) {
        val player = ctx.state.player.countryId
        for (country in war.participants.filter { it != player }) {
            val weariness = war.weariness[country] ?: 0.0
            if (weariness < PEACE_WEARINESS) continue
            val enemy = (if (war.sideOf(country) == War.ATTACKER) war.defenders else war.attackers).firstOrNull() ?: continue
            val pending = ctx.state.diplomacy.proposals.any { it.status == ProposalStatus.PENDING && it.from == country }
            if (pending) continue
            val winning = zonesHeld(ctx, geo, war, country) > zonesHeld(ctx, geo, war, enemy)
            val clause = Clause(PEACE, country, mapOf("keepOccupied" to if (winning) 1.0 else 0.0))
            if (enemy == player) {
                if (ctx.rng.chance(DAILY_PROPOSAL_CHANCE)) DiplomacyService(ctx).aiPropose(country, listOf(clause), 1, "mettre fin à la guerre")
            } else {
                // Le camp perdant n'obtient la paix qu'en cédant les zones occupées ;
                // le vainqueur accepte s'il est lui-même usé ou s'il y gagne des territoires.
                val enemyWeariness = war.weariness[enemy] ?: 0.0
                val enemyGains = zonesHeld(ctx, geo, war, enemy) > 0
                val accepted = if (winning) enemyWeariness > PEACE_WEARINESS / 2 else enemyWeariness > WINNER_WEARINESS || enemyGains
                if (accepted) {
                    val annex = winning || enemyGains
                    val terms = if (annex) "avec cession des territoires occupés" else "sur la base des frontières d'avant-guerre"
                    WarService(ctx).peace(war, keepOccupied = annex,
                        outcome = "Traité de paix entre ${ctx.db.theCountry(country)} et ${ctx.db.theCountry(enemy)}, $terms.")
                    return
                }
            }
        }
    }

    /** Un pays IA en guerre mobilise ses réserves chaque mois, selon sa population. */
    private fun mobilizeForeign(ctx: SimulationContext, geo: Geopolitics) {
        val player = ctx.state.player.countryId
        val setup = MilitarySetup(ctx)
        for (country in geo.activeWars().flatMap { it.participants }.distinct().filter { it != player }) {
            if (ctx.db.zones.ownedBy(country).isEmpty()) continue
            val weariness = geo.activeWars().filter { country in it.participants }.maxOf { it.weariness[country] ?: 0.0 }
            if (weariness > MOBILIZATION_WEARINESS) continue
            val count = (ctx.state.countries.getValue(country).population / POPULATION_PER_RESERVE).toInt().coerceIn(1, MAX_RESERVES_PER_MONTH)
            val capital = ctx.db.country(country).definition.strategic.capital ?: continue
            val zone = setup.zoneFor(country, capital.lon, capital.lat, false)
            if (geo.controllerOf(zone) != country) continue
            repeat(count) { setup.createUnit(country, ctx.db.unitType(RESERVE), zone, RESERVE_READINESS) }
            ctx.log("war", "$country mobilise $count brigades de réserve")
        }
    }

    fun zonesHeld(ctx: SimulationContext, geo: Geopolitics, war: War, country: String): Int =
        ctx.state.military.occupied.count { (zone, occupier) -> occupier == country && geo.ownerOf(zone) in war.participants }

    private fun capitalCheck(ctx: SimulationContext, geo: Geopolitics) {
        val player = ctx.state.player.countryId
        val capital = capitalZone(ctx, player) ?: return
        val military = ctx.state.military
        if (geo.controllerOf(capital) != player) {
            military.capitalOccupiedDays++
            if (military.capitalOccupiedDays >= CAPITAL_LOSS_DAYS) {
                ctx.state.player.gameOver = GameOver(ctx.now, "La capitale est occupée depuis ${military.capitalOccupiedDays} jours : le gouvernement capitule.")
            }
        } else military.capitalOccupiedDays = 0
    }

    companion object {
        const val PEACE = "PEACE_TREATY"
        private const val MILLIONS_PER_BILLION = 1000.0
        private const val REFERENCE_POPULATION = 60_000_000.0
        private const val MIN_SIZE_FACTOR = 0.3
        private const val MAX_SIZE_FACTOR = 5.0
        private const val CAPITULATION_WEARINESS = 0.5
        private const val PEACE_WEARINESS = 0.65
        private const val DAILY_PROPOSAL_CHANCE = 0.1
        private const val WINNER_WEARINESS = 0.35
        private const val MOBILIZATION_WEARINESS = 0.7
        private const val POPULATION_PER_RESERVE = 15_000_000.0
        private const val MAX_RESERVES_PER_MONTH = 4
        private const val RESERVE = "RESERVE_BRIGADE"
        private const val RESERVE_READINESS = 0.5
        private const val CAPITAL_LOSS_DAYS = 30
    }
}
