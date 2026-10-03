package fr.president.engine.military

import fr.president.engine.data.Domain
import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import fr.president.engine.simulation.Cadence
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.simulation.SimulationSystem

/**
 * Déplacement horaire des unités le long de leur itinéraire, consommation de carburant,
 * arrêt devant l'ennemi (sauf ordre d'attaque) et prise des zones non défendues.
 */
class MovementSystem : SimulationSystem {
    override val name = "movement"
    override val cadence = Cadence.HOURLY

    override fun run(ctx: SimulationContext) {
        val geo = Geopolitics(ctx)
        val zones = ctx.db.zones
        for (unit in ctx.state.military.units.values) {
            if (unit.destroyed || unit.path.isEmpty()) continue
            if (unit.inCombat && unit.order != UnitOrder.RETREAT) continue
            val type = ctx.db.unitType(unit.type)
            val next = unit.path.first()
            val nextZone = zones.zones[next] ?: run { unit.path.clear(); return@run null } ?: continue
            if (type.domain == Domain.LAND && !nextZone.sea && next != unit.targetZoneId) {
                // Avant d'entrer dans une zone tenue par l'ennemi sans ordre d'attaque : arrêt.
                val hostile = geo.atWar(unit.countryId, geo.controllerOf(next))
                if (hostile && unit.order != UnitOrder.ATTACK) {
                    stop(ctx, unit, "Progression stoppée : zone tenue par l'ennemi.")
                    continue
                }
            }
            val leg = zones.distanceKm(unit.zoneId, next).coerceAtLeast(MIN_LEG_KM)
            var speed = type.speedKmPerDay / HOURS
            if (type.domain == Domain.LAND && nextZone.sea) speed = SEA_TRANSPORT_KMH
            // Progression prudente en territoire hostile (reconnaissance, résistance, mines).
            if (type.domain == Domain.LAND && geo.atWar(unit.countryId, geo.ownerOf(next))) speed *= HOSTILE_TERRITORY_FACTOR
            if (unit.fuel < LOW_FUEL) speed *= OUT_OF_FUEL_FACTOR
            if (!unit.supplied) speed *= UNSUPPLIED_FACTOR
            unit.legProgressKm += speed
            unit.fuel = (unit.fuel - type.fuelPer100Km * speed / KM_PER_FUEL_UNIT).coerceAtLeast(0.0)
            if (unit.legProgressKm >= leg) arrive(ctx, geo, unit, type.domain, next)
        }
    }

    private fun arrive(ctx: SimulationContext, geo: Geopolitics, unit: UnitState, domain: Domain, zoneId: String) {
        unit.zoneId = zoneId
        unit.path.removeAt(0)
        unit.legProgressKm = 0.0
        if (domain == Domain.LAND) Capture(ctx, geo).tryCapture(unit, zoneId)
        if (unit.path.isEmpty()) {
            unit.order = when (unit.order) {
                UnitOrder.ATTACK -> UnitOrder.DEFEND
                UnitOrder.SUPPORT, UnitOrder.PATROL -> unit.order
                else -> UnitOrder.HOLD
            }
            if (unit.countryId == ctx.state.player.countryId && unit.order != UnitOrder.PATROL) {
                ctx.notifications.post(NotificationCategory.MILITARY, Urgency.INFO, "${unit.name} en position",
                    "L'unité a atteint son objectif.", unit.id)
            }
        }
    }

    private fun stop(ctx: SimulationContext, unit: UnitState, reason: String) {
        unit.path.clear()
        unit.order = UnitOrder.DEFEND
        if (unit.countryId == ctx.state.player.countryId) {
            ctx.notifications.post(NotificationCategory.MILITARY, Urgency.IMPORTANT, "${unit.name} s'arrête", reason, unit.id)
        }
    }

    private companion object {
        const val HOURS = 24.0
        const val MIN_LEG_KM = 1.0
        const val SEA_TRANSPORT_KMH = 16.0
        const val LOW_FUEL = 0.05
        const val HOSTILE_TERRITORY_FACTOR = 0.25
        const val OUT_OF_FUEL_FACTOR = 0.2
        const val UNSUPPLIED_FACTOR = 0.7
        /** Une unité de carburant = autonomie pour 100 km × fuelPer100Km. */
        const val KM_PER_FUEL_UNIT = 100.0
    }
}

/** Prise ou libération d'une zone terrestre. */
class Capture(private val ctx: SimulationContext, private val geo: Geopolitics) {
    fun tryCapture(unit: UnitState, zoneId: String) {
        val zone = ctx.db.zones.zone(zoneId)
        if (zone.sea) return
        val controller = geo.controllerOf(zoneId)
        if (controller == unit.countryId || !geo.atWar(unit.countryId, controller)) return
        val defended = ctx.state.military.units.values.any {
            !it.destroyed && it.zoneId == zoneId && geo.atWar(it.countryId, unit.countryId) &&
                ctx.db.unitType(it.type).domain == fr.president.engine.data.Domain.LAND
        }
        if (defended) return
        take(zoneId, unit.countryId)
    }

    fun take(zoneId: String, by: String) {
        val owner = geo.ownerOf(zoneId)
        val previous = geo.controllerOf(zoneId)
        if (owner == by || owner in geo.coBelligerents(by)) ctx.state.military.occupied.remove(zoneId)
        else ctx.state.military.occupied[zoneId] = by
        val player = ctx.state.player.countryId
        val zone = ctx.db.zones.zone(zoneId)
        val capital = ctx.db.country(owner).definition.strategic.capital
        val isCapital = capital != null && ctx.db.zones.nearest(capital.lon, capital.lat) { !it.sea && it.owner == owner }?.id == zoneId
        val place = zone.department?.let { d -> ctx.playerData.territory?.departments?.firstOrNull { it.code == d }?.name }
            ?: ctx.db.country(owner).definition.name
        when {
            owner == player && by != player -> {
                ctx.notifications.post(NotificationCategory.MILITARY, Urgency.URGENT, "Territoire national envahi",
                    "Les forces de ${ctx.db.country(by).definition.name} ont pris le contrôle d'une zone ($place).", zoneId)
                zone.department?.let { ctx.effects.apply("dept.$it.approval", INVASION_SHOCK) }
                ctx.state.opinion.groups.values.forEach { it.shock += RALLY_ON_INVASION }
            }
            previous == player || by == player || owner == player ->
                ctx.notifications.post(NotificationCategory.MILITARY, if (isCapital) Urgency.URGENT else Urgency.IMPORTANT,
                    if (by == player) "Zone prise ($place)" else "Zone perdue ($place)",
                    if (isCapital) "La capitale est tombée." else "Le front a bougé.", zoneId)
            isCapital -> ctx.notifications.post(NotificationCategory.MILITARY, Urgency.IMPORTANT,
                "${ctx.db.country(owner).definition.name} : la capitale est tombée",
                "Les forces de ${ctx.db.country(by).definition.name} contrôlent ${capital?.name}.", zoneId)
        }
    }

    private companion object {
        const val INVASION_SHOCK = -0.15
        const val RALLY_ON_INVASION = 0.01
    }
}
