package fr.president.engine.military

import fr.president.engine.data.Domain
import fr.president.engine.simulation.Cadence
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.simulation.SimulationSystem

/**
 * Conduite des opérations par l'IA (pays étrangers en guerre). Chaque jour :
 * les unités usées se replient, les fronts menacés sont renforcés, et une offensive est lancée
 * sur la zone ennemie la plus faible lorsque le rapport de forces local est favorable.
 * L'IA ne voit que ce que son renseignement lui montre.
 */
class MilitaryAiSystem : SimulationSystem {
    override val name = "military-ai"
    override val cadence = Cadence.DAILY

    override fun run(ctx: SimulationContext) {
        val geo = Geopolitics(ctx)
        if (geo.activeWars().isEmpty()) return
        val player = ctx.state.player.countryId
        val orders = OrderService(ctx)
        val intel = Intelligence(ctx)
        for (country in geo.activeWars().flatMap { it.participants }.distinct().filter { it != player }) {
            val units = ctx.state.military.units.values.filter { it.countryId == country && !it.destroyed }
            if (units.isEmpty()) continue
            val enemies = geo.enemiesOf(country)
            val visibleEnemies = intel.visibleUnits(country).filter { it.countryId in enemies }
            val enemyPower = visibleEnemies.groupBy { it.zoneId }.mapValues { (_, us) -> us.sumOf { power(ctx, it) } }
            for (u in units) {
                val type = ctx.db.unitType(u.type)
                when (type.domain) {
                    Domain.LAND -> land(ctx, geo, orders, u, enemies, enemyPower)
                    Domain.AIR -> air(ctx, orders, u, enemies)
                    Domain.SEA -> if (u.path.isEmpty() && u.order == UnitOrder.HOLD) sea(ctx, orders, u, enemies, geo)
                    Domain.STRATEGIC -> Unit
                }
            }
        }
    }

    private fun land(ctx: SimulationContext, geo: Geopolitics, orders: OrderService, u: UnitState, enemies: Set<String>, enemyPower: Map<String, Double>) {
        if (u.inCombat) return
        if (u.strength < REST_STRENGTH || u.ammunition < REST_AMMO) {
            if (u.order != UnitOrder.RETREAT) orders.issue(u.id, UnitOrder.RETREAT)
            return
        }
        if (u.path.isNotEmpty()) return
        // Zones ennemies voisines : cible la plus faible si le rapport de forces est favorable.
        val adjacent = ctx.db.zones.neighbors(u.zoneId).filter { !it.sea && geo.controllerOf(it.id) in enemies }
        val own = power(ctx, u) + ctx.state.military.units.values.filter { it.zoneId == u.zoneId && it.countryId == u.countryId && it !== u && !it.destroyed }.sumOf { power(ctx, it) }
        val target = adjacent.minByOrNull { enemyPower[it.id] ?: 0.0 }
        if (target != null && own > (enemyPower[target.id] ?: 0.0) * ATTACK_RATIO) {
            orders.issue(u.id, UnitOrder.ATTACK, target.id)
            return
        }
        if (adjacent.isNotEmpty()) {
            if (u.order != UnitOrder.DEFEND) orders.issue(u.id, UnitOrder.DEFEND)
            return
        }
        // Sans contact : marche vers la zone de front la plus proche.
        val front = frontZones(ctx, geo, u.countryId, enemies).minByOrNull { ctx.db.zones.distanceKm(u.zoneId, it) }
        if (front != null && front != u.zoneId && ctx.rng.chance(ADVANCE_CHANCE)) orders.issue(u.id, UnitOrder.MOVE, front)
    }

    private fun air(ctx: SimulationContext, orders: OrderService, u: UnitState, enemies: Set<String>) {
        val battles = ctx.state.military.units.values.filter { it.inCombat && it.countryId !in enemies && !it.destroyed }.map { it.zoneId }.distinct()
        val target = battles.minByOrNull { ctx.db.zones.distanceKm(u.zoneId, it) } ?: return
        if (u.targetZoneId != target) orders.issue(u.id, UnitOrder.SUPPORT, target)
    }

    private fun sea(ctx: SimulationContext, orders: OrderService, u: UnitState, enemies: Set<String>, geo: Geopolitics) {
        val coast = enemies.flatMap { geo.territoryOf(it) }.map { ctx.db.zones.zone(it) }.filter { it.coastal }
            .minByOrNull { ctx.db.zones.distanceKm(u.zoneId, it.id) } ?: return
        orders.issue(u.id, UnitOrder.PATROL, coast.id)
    }

    /** Zones amies au contact de l'ennemi. */
    private fun frontZones(ctx: SimulationContext, geo: Geopolitics, country: String, enemies: Set<String>): List<String> {
        val friends = geo.coBelligerents(country) + country
        return (geo.territoryOf(country) + ctx.state.military.occupied.filter { it.value == country }.keys)
            .filter { z -> geo.controllerOf(z) in friends && ctx.db.zones.neighbors(z).any { !it.sea && geo.controllerOf(it.id) in enemies } }
    }

    private fun power(ctx: SimulationContext, u: UnitState): Double {
        val t = ctx.db.unitType(u.type)
        return (t.attack + t.defense) / 2 * u.strength * u.readiness
    }

    private companion object {
        const val REST_STRENGTH = 0.4
        const val REST_AMMO = 0.15
        const val ATTACK_RATIO = 1.3
        const val ADVANCE_CHANCE = 0.5
    }
}
