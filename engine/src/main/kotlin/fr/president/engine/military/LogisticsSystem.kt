package fr.president.engine.military

import fr.president.engine.data.Domain
import fr.president.engine.simulation.Cadence
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.simulation.SimulationSystem
import fr.president.engine.util.clamp01

/**
 * Chaînes de ravitaillement, toutes les 6 heures : une unité est ravitaillée si elle peut
 * être rejointe depuis le territoire national par des zones amies (portée limitée,
 * allongée par les brigades logistiques). Ravitaillée, elle se recomplète en puisant dans
 * les stocks nationaux ; isolée, elle s'use.
 */
class LogisticsSystem : SimulationSystem {
    override val name = "logistics"
    override val cadence = Cadence.HOURLY

    override fun run(ctx: SimulationContext) {
        if (ctx.now.hourIndex % PERIOD_HOURS != 0L) return
        val geo = Geopolitics(ctx)
        val p = ctx.db.militaryParameters
        val fraction = PERIOD_HOURS / HOURS_PER_DAY
        val byCountry = ctx.state.military.units.values.filter { !it.destroyed }.groupBy { it.countryId }
        val forts = FortificationService(ctx)
        for ((country, units) in byCountry) {
            val atWar = geo.isAtWar(country)
            val reach: Map<String, Int>? = if (atWar) supplyReach(ctx, geo, country, units) else null
            for (unit in units) {
                val type = ctx.db.unitType(unit.type)
                unit.supplied = when {
                    type.domain == Domain.STRATEGIC -> true
                    reach == null -> peacefulSupply(geo, country, unit)
                    type.domain == Domain.SEA -> true
                    else -> unit.zoneId in reach
                }
                // Dépôt ou base aérienne sur place : on se recomplète plus vite ; caserne : on récupère plus vite.
                val owners = setOf(country)
                val boost = forts.effect(unit.zoneId, "depot", "resupply", owners) +
                    if (type.domain == Domain.AIR) forts.effect(unit.zoneId, "airfield", "rearm", owners) else 0.0
                if (unit.supplied) resupply(ctx, unit, p.resupplyPerDay * fraction * (1 + boost), country)
                else decay(unit, p.unsuppliedDecayPerDay * fraction)
                val resting = !unit.inCombat && unit.path.isEmpty()
                if (resting) {
                    val recovery = 1 + forts.effect(unit.zoneId, "barracks", "recovery", owners)
                    unit.fatigue = (unit.fatigue - p.fatigueRecoveryPerDay * fraction * recovery).coerceAtLeast(0.0)
                    if (recovery > 1) unit.morale = (unit.morale + BARRACKS_MORALE * fraction * recovery).coerceAtMost(1.0)
                    if (unit.supplied) unit.strength = (unit.strength + REPLACEMENT_PER_DAY * fraction).coerceAtMost(1.0)
                }
            }
        }
    }

    private fun peacefulSupply(geo: Geopolitics, country: String, unit: UnitState): Boolean {
        val c = geo.controllerOf(unit.zoneId)
        return c == country || c == "" || geo.allied(country, c) || geo.hasPassage(country, c)
    }

    private fun supplyReach(ctx: SimulationContext, geo: Geopolitics, country: String, units: List<UnitState>): Map<String, Int> {
        val p = ctx.db.militaryParameters
        val friends = geo.coBelligerents(country) + country
        // Les dépôts bâtis en zone conquise deviennent eux aussi des points de départ du ravitaillement.
        val depots = FortificationService(ctx).depots(country)
        val sources = (geo.territoryOf(country) + friends.flatMap { geo.territoryOf(it) })
            .filter { geo.controllerOf(it) in friends } + depots.map { it.zoneId }
        // Seules les zones proches des unités comptent : on part de la frontière utile.
        val bonus = (units.maxOfOrNull { ctx.db.unitType(it.type).logistics } ?: 0) +
            (depots.maxOfOrNull { FortificationService(ctx).value(it, "supply").toInt() } ?: 0)
        // Là où les partisans sabotent routes et voies ferrées, le ravitaillement de l'occupant ne passe plus.
        val occupation = OccupationService(ctx)
        return ctx.db.zones.within(sources, p.supplyRangeZones + bonus) { z ->
            (z.sea || geo.controllerOf(z.id) in friends || geo.allied(country, geo.controllerOf(z.id))) && !occupation.sabotaged(z.id, country)
        }
    }

    private fun resupply(ctx: SimulationContext, unit: UnitState, rate: Double, country: String) {
        val isPlayer = country == ctx.state.player.countryId
        val stocks = ctx.state.military.stocks
        val ammoNeed = (1.0 - unit.ammunition).coerceAtLeast(0.0) * rate
        val fuelNeed = (1.0 - unit.fuel).coerceAtLeast(0.0) * rate
        val ammoGain = if (isPlayer) minOf(ammoNeed, stocks.ammunition / UNIT_SHARE) else ammoNeed
        val fuelGain = if (isPlayer) minOf(fuelNeed, stocks.fuel / UNIT_SHARE) else fuelNeed
        unit.ammunition = (unit.ammunition + ammoGain).clamp01()
        unit.fuel = (unit.fuel + fuelGain).clamp01()
        if (isPlayer) {
            stocks.ammunition = (stocks.ammunition - ammoGain * UNIT_SHARE).coerceAtLeast(0.0)
            stocks.fuel = (stocks.fuel - fuelGain * UNIT_SHARE).coerceAtLeast(0.0)
        }
    }

    private fun decay(unit: UnitState, rate: Double) {
        unit.ammunition = (unit.ammunition - rate).coerceAtLeast(0.0)
        unit.fuel = (unit.fuel - rate).coerceAtLeast(0.0)
        unit.morale = (unit.morale - rate / 2).coerceAtLeast(0.0)
    }

    private companion object {
        const val PERIOD_HOURS = 6L
        const val HOURS_PER_DAY = 24.0
        const val REPLACEMENT_PER_DAY = 0.01
        const val BARRACKS_MORALE = 0.03
        /** Part des stocks nationaux nécessaire pour recompléter entièrement une unité. */
        const val UNIT_SHARE = 0.02
    }
}
