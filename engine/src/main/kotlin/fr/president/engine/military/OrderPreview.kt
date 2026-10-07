package fr.president.engine.military

import fr.president.engine.data.Domain
import fr.president.engine.readout.Tone
import fr.president.engine.simulation.SimulationContext
import kotlin.math.sqrt

/**
 * Aperçu d'un ordre avant de le donner, comme dans Supremacy : est-ce possible, combien de
 * temps faudra-t-il, et quel est le rapport de forces sur place (d'après notre renseignement).
 * Rien n'est modifié : l'unité est remise exactement dans son état.
 */
class OrderPreview(private val ctx: SimulationContext) {
    private val geo = Geopolitics(ctx)
    private val zones get() = ctx.db.zones

    data class Odds(val own: Double, val enemy: Double, val label: String, val tone: Tone, val notes: List<String> = emptyList())

    data class Option(
        val order: UnitOrder,
        val label: String,
        val available: Boolean,
        val reason: String? = null,
        val etaHours: Double? = null,
        val odds: Odds? = null,
    )

    data class Preview(val place: String, val controller: String, val hostile: Boolean, val options: List<Option>)

    fun preview(unitId: String, zoneId: String): Preview? {
        val unit = ctx.state.military.units[unitId] ?: return null
        val type = ctx.db.unitType(unit.type)
        val zone = zones.zones[zoneId] ?: return null
        val controller = geo.controllerOf(zoneId)
        val hostile = !zone.sea && geo.atWar(unit.countryId, controller)
        val orders = when (type.domain) {
            Domain.LAND -> buildList {
                if (hostile) add(UnitOrder.ATTACK to "⚔ Attaquer") else add(UnitOrder.MOVE to "▶ Déplacer")
                if (!zone.sea && zone.coastal) add(UnitOrder.AMPHIBIOUS to "⚓ Débarquer")
                if (unit.type == AIRBORNE && !zone.sea) add(UnitOrder.AIRBORNE to "✈ Parachuter")
            }
            Domain.AIR -> listOf(UnitOrder.SUPPORT to "✈ Soutien aérien", UnitOrder.PATROL to "◎ Patrouille", UnitOrder.MOVE to "▶ Redéployer")
            Domain.SEA -> listOf(UnitOrder.MOVE to "▶ Naviguer", UnitOrder.PATROL to "◎ Patrouille", UnitOrder.SUPPORT to "✹ Appui côtier")
            Domain.STRATEGIC -> emptyList()
        }
        val options = orders.map { (order, label) -> option(unit, order, label, zoneId) }
        return Preview(describe(zoneId), controller, hostile, options)
    }

    private fun option(unit: UnitState, order: UnitOrder, label: String, zoneId: String): Option {
        val saved = Snapshot(unit)
        val stocks = ctx.state.military.stocks.fuel
        val outcome = when (order) {
            // Les opérations spéciales notifient et déplacent : on les évalue sans les exécuter.
            UnitOrder.AIRBORNE -> OperationsService(ctx).airborneCheck(unit, zoneId)?.let { OrderService.Outcome.Refused(it) } ?: OrderService.Outcome.Ok
            UnitOrder.AMPHIBIOUS -> OperationsService(ctx).amphibiousCheck(unit, zoneId).let { (path, reason) ->
                if (path == null) OrderService.Outcome.Refused(reason ?: "Impossible") else { unit.path.clear(); unit.path.addAll(path); OrderService.Outcome.Ok }
            }
            else -> OrderService(ctx).issue(unit.id, order, zoneId)
        }
        val eta = if (outcome is OrderService.Outcome.Ok) eta(unit, order, zoneId) else null
        saved.restore(unit)
        ctx.state.military.stocks.fuel = stocks
        val odds = if (order == UnitOrder.ATTACK || order == UnitOrder.AMPHIBIOUS || order == UnitOrder.AIRBORNE || order == UnitOrder.SUPPORT) odds(unit, zoneId) else null
        return when (outcome) {
            is OrderService.Outcome.Ok -> Option(order, label, true, null, eta, odds)
            is OrderService.Outcome.Refused -> Option(order, label, false, outcome.reason)
        }
    }

    /** Durée estimée du trajet, avec les mêmes règles que le déplacement réel. */
    private fun eta(unit: UnitState, order: UnitOrder, zoneId: String): Double {
        val type = ctx.db.unitType(unit.type)
        if (order == UnitOrder.AIRBORNE) return AIRBORNE_HOURS
        val path = unit.path.ifEmpty { listOf(zoneId) }
        var from = unit.zoneId
        var hours = 0.0
        for (z in path) {
            val zone = zones.zones[z] ?: break
            var speed = type.speedKmPerDay / HOURS_PER_DAY
            if (type.domain == Domain.LAND && zone.sea) speed = SEA_KMH
            if (type.domain == Domain.LAND && geo.atWar(unit.countryId, geo.ownerOf(z))) speed *= HOSTILE_FACTOR
            hours += zones.distanceKm(from, z) / speed.coerceAtLeast(MIN_SPEED)
            from = z
        }
        return hours
    }

    /** Temps restant avant l'arrivée d'une unité en mouvement (heures), ou null si elle est à l'arrêt. */
    fun remainingHours(unit: UnitState): Double? {
        if (unit.path.isEmpty()) return null
        val saved = unit.path.toList()
        val hours = eta(unit, unit.order, saved.last())
        val type = ctx.db.unitType(unit.type)
        val done = unit.legProgressKm / (type.speedKmPerDay / HOURS_PER_DAY).coerceAtLeast(MIN_SPEED)
        return (hours - done).coerceAtLeast(0.0)
    }

    /** Rapport de forces dans une bataille en cours (nous contre l'ennemi), ou null. */
    fun battleRatio(zoneId: String, side: String): Double? {
        val here = ctx.state.military.units.values.filter { it.zoneId == zoneId && !it.destroyed }
        val own = here.filter { it.countryId == side || it.countryId in geo.coBelligerents(side) }.sumOf { power(it, true) }
        val enemy = here.filter { geo.atWar(side, it.countryId) }.sumOf { power(it, false) }
        return if (own <= 0 || enemy <= 0) null else own / enemy
    }

    /** Rapport de forces : notre unité (et nos unités déjà sur place) contre l'ennemi repéré, terrain et ouvrages compris. */
    private fun odds(unit: UnitState, zoneId: String): Odds {
        val visible = Intelligence(ctx).visibleUnits(unit.countryId)
        val enemies = visible.filter { it.zoneId == zoneId && !it.destroyed && geo.atWar(unit.countryId, it.countryId) }
        val friends = ctx.state.military.units.values.filter { it.zoneId == zoneId && it.countryId == unit.countryId && !it.destroyed }
        val terrain = Terrain(ctx)
        val forts = FortificationService(ctx)
        val notes = mutableListOf<String>()
        val t = terrain.of(zoneId)
        val enemyCamp = enemies.flatMap { geo.coBelligerents(it.countryId) + it.countryId }.toSet() + geo.controllerOf(zoneId)
        val line = forts.effect(zoneId, "line", "defense", enemyCamp)
        val defenseFactor = terrain.defenseFactor(zoneId) * (1 + line)
        if (t != null) notes += "${t.icon} ${t.label}" + (if (t.defense != 1.0) " (défense ×${fmt(t.defense)})" else "") + if (t.hint.isNotEmpty()) " — ${t.hint}" else ""
        if (line > 0) notes += "▦ Zone fortifiée : défense +${Math.round(line * 100)} % (l'artillerie la réduit)"
        val enemyCategory = enemies.groupBy { terrain.category(it.type) }.maxByOrNull { (_, us) -> us.size }?.key.orEmpty()
        val matchup = terrain.matchup(terrain.category(unit.type), enemyCategory)
        if (matchup != null && matchup.label.isNotEmpty()) notes += (if (matchup.factor >= 1) "✦ " else "✕ ") + matchup.label.replaceFirstChar { it.uppercase() }
        val unitFactor = terrain.unitFactor(zoneId, unit.type)
        if (unitFactor < 1.0) notes += "✕ ${ctx.db.unitType(unit.type).label} mal adaptée à ce terrain (×${fmt(unitFactor)})"
        else if (unitFactor > 1.0) notes += "✦ ${ctx.db.unitType(unit.type).label} à l'aise sur ce terrain (×${fmt(unitFactor)})"
        val river = (unit.path.dropLast(1).lastOrNull() ?: unit.zoneId).let { terrain.riverBetween(it, zoneId) }
        val riverFactor = if (river != null) ctx.db.warfare?.riverCrossing ?: 1.0 else 1.0
        if (river != null) notes += "≈ Il faudra franchir le fleuve $river (attaque ×${fmt(riverFactor)})"
        val own = (power(unit, true) * unitFactor * (matchup?.factor ?: 1.0) + friends.sumOf { power(it, true) * terrain.unitFactor(zoneId, it.type) }) * riverFactor
        val enemy = enemies.sumOf { power(it, false) * terrain.unitFactor(zoneId, it.type) * if (geo.ownerOf(zoneId) == it.countryId) HOME_BONUS else 1.0 } * defenseFactor
        if (enemy <= 0.0) return Odds(own, 0.0, "Aucune défense repérée", Tone.GOOD, notes)
        val ratio = own / enemy
        val (label, tone) = when {
            ratio >= CRUSHING -> "Écrasant (${fmt(ratio)} contre 1)" to Tone.GOOD
            ratio >= FAVORABLE -> "Favorable (${fmt(ratio)} contre 1)" to Tone.GOOD
            ratio >= UNCERTAIN -> "Incertain (${fmt(ratio)} contre 1)" to Tone.WARNING
            else -> "Défavorable (${fmt(ratio)} contre 1)" to Tone.BAD
        }
        return Odds(own, enemy, label, tone, notes)
    }

    private fun power(u: UnitState, attacking: Boolean): Double {
        val t = ctx.db.unitType(u.type)
        val base = if (attacking) t.attack else t.defense * if (u.order == UnitOrder.DEFEND || u.order == UnitOrder.HOLD) DEFEND_BONUS else 1.0
        return base * u.strength * u.readiness.coerceAtLeast(MIN_READINESS) * sqrt(u.morale.coerceAtLeast(MIN_MORALE))
    }

    /** « près de Berlin — Allemagne » : la ville la plus proche et le pays qui tient la zone. */
    fun describe(zoneId: String): String {
        val zone = zones.zone(zoneId)
        val name = { c: String -> ctx.db.countries[c]?.definition?.name ?: c }
        if (zone.sea) return "En mer"
        val city = WorldCities(ctx).all().minByOrNull { haversine(zone.lat, zone.lon, it.lat, it.lon) }
        val near = city?.takeIf { haversine(zone.lat, zone.lon, it.lat, it.lon) < NEAR_KM }?.name
        val holder = geo.controllerOf(zoneId)
        return (near?.let { "Près de $it — " } ?: "") + name(holder)
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2).let { it * it } + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2).let { it * it }
        return 2 * EARTH_KM * Math.asin(sqrt(a))
    }

    private fun fmt(v: Double) = String.format(java.util.Locale.FRENCH, "%.1f", v)

    /** État d'une unité que l'aperçu peut toucher, pour le rétablir à l'identique. */
    private class Snapshot(u: UnitState) {
        val path = u.path.toList()
        val order = u.order
        val target = u.targetZoneId
        val progress = u.legProgressKm
        val zone = u.zoneId
        fun restore(u: UnitState) {
            u.path.clear(); u.path.addAll(path)
            u.order = order; u.targetZoneId = target; u.legProgressKm = progress; u.zoneId = zone
        }
    }

    private companion object {
        const val AIRBORNE = "AIRBORNE_BRIGADE"
        const val AIRBORNE_HOURS = 6.0
        const val HOURS_PER_DAY = 24.0
        const val SEA_KMH = 16.0
        const val HOSTILE_FACTOR = 0.25
        const val MIN_SPEED = 0.1
        const val HOME_BONUS = 1.2
        const val DEFEND_BONUS = 1.3
        const val MIN_READINESS = 0.2
        const val MIN_MORALE = 0.1
        const val CRUSHING = 2.0
        const val FAVORABLE = 1.3
        const val UNCERTAIN = 0.8
        const val NEAR_KM = 120.0
        const val EARTH_KM = 6371.0
    }
}
