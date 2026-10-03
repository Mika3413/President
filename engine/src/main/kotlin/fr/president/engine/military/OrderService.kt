package fr.president.engine.military

import fr.president.engine.data.Domain
import fr.president.engine.data.UnitTypeDef
import fr.president.engine.simulation.SimulationContext

/**
 * Ordres donnés aux unités (par le joueur ou l'IA) : calcul de l'itinéraire selon le domaine
 * (terre, mer, air), le droit de passage et l'état de guerre.
 */
class OrderService(private val ctx: SimulationContext) {
    private val geo = Geopolitics(ctx)
    private val zones get() = ctx.db.zones

    sealed class Outcome {
        data object Ok : Outcome()
        data class Refused(val reason: String) : Outcome()
    }

    fun issue(unitId: String, order: UnitOrder, targetZone: String? = null): Outcome {
        val unit = ctx.state.military.units[unitId] ?: return Outcome.Refused("Unité inconnue")
        if (unit.destroyed) return Outcome.Refused("Unité détruite")
        val type = ctx.db.unitType(unit.type)
        if (type.domain == Domain.STRATEGIC) return Outcome.Refused("La force de dissuasion reste sous le seul contrôle du président et ne se déploie pas.")
        return when (order) {
            UnitOrder.HOLD, UnitOrder.DEFEND -> {
                unit.path.clear(); unit.legProgressKm = 0.0
                unit.order = order; unit.targetZoneId = unit.zoneId
                Outcome.Ok
            }
            UnitOrder.RETREAT -> retreat(unit, type)
            UnitOrder.SUPPORT, UnitOrder.PATROL -> project(unit, type, order, targetZone ?: return Outcome.Refused("Choisissez une zone"))
            UnitOrder.MOVE, UnitOrder.ATTACK -> move(unit, type, order, targetZone ?: return Outcome.Refused("Choisissez une destination"))
        }
    }

    private fun move(unit: UnitState, type: UnitTypeDef, order: UnitOrder, target: String): Outcome {
        val zone = zones.zones[target] ?: return Outcome.Refused("Zone inconnue")
        val attacking = order == UnitOrder.ATTACK
        when (type.domain) {
            Domain.AIR -> {
                // Une escadre se redéploie sur une base amie : elle doit atterrir en territoire contrôlé.
                if (zone.sea || !friendly(unit.countryId, target)) return Outcome.Refused("Une escadre doit se baser dans une zone amie.")
                unit.path.clear(); unit.path.add(target)
            }
            Domain.SEA -> {
                if (!zone.sea && !(zone.coastal && friendly(unit.countryId, target))) return Outcome.Refused("Un groupe naval ne peut rejoindre que la mer ou un port ami.")
                val path = zones.path(unit.zoneId, target) { it.sea || it.id == target || it.id == unit.zoneId }
                    ?: return Outcome.Refused("Aucune route maritime vers cette zone.")
                unit.path.clear(); unit.path.addAll(path)
            }
            Domain.LAND -> {
                if (zone.sea) return Outcome.Refused("Une unité terrestre ne peut pas s'arrêter en mer.")
                if (!geo.canEnter(unit.countryId, zone, attacking)) {
                    return Outcome.Refused(
                        if (geo.atWar(unit.countryId, geo.controllerOf(target))) "Pour entrer en territoire ennemi, donnez un ordre d'attaque."
                        else "Aucun droit de passage sur ce territoire.",
                    )
                }
                val path = landPath(unit.countryId, unit.zoneId, target, attacking)
                    ?: return Outcome.Refused("Aucun itinéraire praticable vers cette zone.")
                unit.path.clear(); unit.path.addAll(path)
            }
            Domain.STRATEGIC -> return Outcome.Refused("Impossible")
        }
        unit.legProgressKm = 0.0
        unit.order = order
        unit.targetZoneId = target
        return Outcome.Ok
    }

    /** Itinéraire terrestre ; la traversée maritime est possible (transport) mais pénalisée. */
    fun landPath(country: String, from: String, to: String, attacking: Boolean): List<String>? =
        zones.path(from, to) { z ->
            if (z.sea) true else geo.canEnter(country, z, attacking && (z.id == to || geo.atWar(country, geo.controllerOf(z.id))))
        }

    private fun project(unit: UnitState, type: UnitTypeDef, order: UnitOrder, target: String): Outcome {
        val zone = zones.zones[target] ?: return Outcome.Refused("Zone inconnue")
        if (type.domain == Domain.LAND) return move(unit, type, if (order == UnitOrder.SUPPORT) UnitOrder.MOVE else UnitOrder.DEFEND, target)
        if (type.domain == Domain.SEA && !zone.sea) {
            // Soutien naval à une zone côtière : le groupe se place sur la mer voisine.
            val sea = zones.neighbors(target).firstOrNull { it.sea } ?: return Outcome.Refused("Zone non côtière.")
            val r = move(unit, type, UnitOrder.MOVE, sea.id)
            if (r is Outcome.Ok) { unit.order = order; unit.targetZoneId = target }
            return r
        }
        val distance = zones.distanceKm(unit.zoneId, target)
        if (type.domain == Domain.AIR && distance > effectiveRange(unit, type)) {
            return Outcome.Refused("Hors de portée (${distance.toInt()} km pour un rayon de ${effectiveRange(unit, type).toInt()} km). Redéployez l'escadre plus près.")
        }
        unit.path.clear()
        unit.order = order
        unit.targetZoneId = target
        return Outcome.Ok
    }

    /** Le ravitaillement en vol allonge le rayon d'action des escadres. */
    fun effectiveRange(unit: UnitState, type: UnitTypeDef): Double {
        val tankers = ctx.state.military.units.values.count { it.countryId == unit.countryId && it.type == TANKER && !it.destroyed }
        return type.rangeKm * (1.0 + TANKER_BONUS * minOf(tankers, MAX_TANKERS))
    }

    private fun retreat(unit: UnitState, type: UnitTypeDef): Outcome {
        val home = unit.homeZoneId.ifBlank { unit.zoneId }
        if (type.domain == Domain.AIR) {
            unit.order = UnitOrder.HOLD; unit.targetZoneId = null; unit.path.clear()
            if (unit.zoneId != home) unit.path.add(home)
            return Outcome.Ok
        }
        val path = if (type.domain == Domain.SEA) zones.path(unit.zoneId, home) { it.sea || it.id == home }
        else landPath(unit.countryId, unit.zoneId, home, false)
        unit.path.clear()
        path?.let { unit.path.addAll(it) }
        unit.legProgressKm = 0.0
        unit.order = UnitOrder.RETREAT
        unit.targetZoneId = home
        return Outcome.Ok
    }

    private fun friendly(country: String, zoneId: String): Boolean {
        val c = geo.controllerOf(zoneId)
        return c == country || c in geo.coBelligerents(country) || geo.allied(country, c)
    }

    private companion object {
        const val TANKER = "TANKER_WING"
        const val TANKER_BONUS = 0.4
        const val MAX_TANKERS = 2
    }
}
