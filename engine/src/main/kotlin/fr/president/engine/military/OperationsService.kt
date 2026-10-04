package fr.president.engine.military

import fr.president.engine.data.Domain
import fr.president.engine.effects.EffectSpec
import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import fr.president.engine.readout.Tone
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.stats.JournalService

/**
 * Opérations spéciales du président chef des armées :
 * - parachutage d'une brigade aéroportée derrière les lignes ;
 * - débarquement amphibie sous escorte navale ;
 * - frappes de missiles de croisière sur une zone ennemie ;
 * - cyberattaque contre l'économie et les armées d'un pays (même hors guerre, au risque d'être démasqué).
 */
class OperationsService(private val ctx: SimulationContext) {
    private val geo = Geopolitics(ctx)
    private val zones get() = ctx.db.zones
    private val player get() = ctx.state.player.countryId
    private val units get() = ctx.state.military.units.values

    // ------------------------------------------------------------------ Parachutage
    fun airborne(unitId: String, target: String): OrderService.Outcome {
        val unit = ctx.state.military.units[unitId] ?: return refused("Unité inconnue")
        if (unit.type != AIRBORNE) return refused("Seule une brigade parachutiste peut être larguée.")
        cooldown(unit)?.let { return refused(it) }
        val zone = zones.zones[target] ?: return refused("Zone inconnue")
        if (zone.sea) return refused("Impossible de sauter en mer.")
        if (units.none { it.countryId == unit.countryId && it.type == TRANSPORT && !it.destroyed }) return refused("Il faut une escadre de transport disponible.")
        val distance = zones.distanceKm(unit.zoneId, target)
        if (distance > AIRBORNE_RANGE_KM) return refused("Zone de saut trop lointaine (${distance.toInt()} km, maximum ${AIRBORNE_RANGE_KM.toInt()} km).")
        val controller = geo.controllerOf(target)
        val hostile = geo.atWar(unit.countryId, controller)
        if (!hostile && controller != unit.countryId && !geo.allied(unit.countryId, controller)) return refused("Aucun droit d'opérer sur ce territoire.")
        unit.zoneId = target
        unit.path.clear()
        unit.legProgressKm = 0.0
        unit.order = if (hostile) UnitOrder.ATTACK else UnitOrder.DEFEND
        unit.targetZoneId = target
        unit.fatigue = (unit.fatigue + AIRBORNE_FATIGUE).coerceAtMost(1.0)
        unit.lastOperationAt = ctx.now
        ctx.state.military.stocks.fuel = (ctx.state.military.stocks.fuel - FUEL_COST).coerceAtLeast(0.0)
        report("Opération aéroportée : ${unit.name}", "La brigade a été larguée${if (hostile) " en territoire ennemi" else ""}.", target)
        return OrderService.Outcome.Ok
    }

    // ------------------------------------------------------------------ Débarquement
    fun amphibious(unitId: String, target: String): OrderService.Outcome {
        val unit = ctx.state.military.units[unitId] ?: return refused("Unité inconnue")
        if (ctx.db.unitType(unit.type).domain != Domain.LAND) return refused("Seules les unités terrestres débarquent.")
        cooldown(unit)?.let { return refused(it) }
        val zone = zones.zones[target] ?: return refused("Zone inconnue")
        if (zone.sea || !zone.coastal) return refused("Choisissez une zone côtière.")
        val escort = units.any { it.countryId == unit.countryId && !it.destroyed && ctx.db.unitType(it.type).domain == Domain.SEA &&
            zones.distanceKm(it.zoneId, target) <= ESCORT_KM }
        if (!escort) return refused("Un débarquement exige une escorte navale à moins de ${ESCORT_KM.toInt()} km de la plage.")
        val start = zones.zone(unit.zoneId)
        if (!start.coastal && zones.neighbors(unit.zoneId).none { it.sea }) return refused("L'unité doit d'abord rejoindre un port ou une côte.")
        val path = zones.path(unit.zoneId, target) { it.sea || it.id == target || it.id == unit.zoneId }
            ?: return refused("Aucune route maritime vers cette plage.")
        val hostile = geo.atWar(unit.countryId, geo.controllerOf(target))
        unit.path.clear(); unit.path.addAll(path)
        unit.legProgressKm = 0.0
        unit.order = if (hostile) UnitOrder.ATTACK else UnitOrder.MOVE
        unit.targetZoneId = target
        unit.lastOperationAt = ctx.now
        // L'infanterie de marine est entraînée pour cela ; les autres débarquent dans la douleur.
        unit.morale = (unit.morale + if (unit.type == MARINES) MARINE_MORALE else -OTHER_MORALE).coerceIn(0.0, 1.0)
        report("Débarquement : ${unit.name}", "Les navires appareillent vers la côte.", target)
        return OrderService.Outcome.Ok
    }

    // ------------------------------------------------------------------ Frappes
    /** Zone à frapper : la concentration ennemie la plus forte à portée, sinon la plus proche. */
    fun strikeTarget(country: String): String? {
        val launchers = launchers()
        val inRange = { z: String -> launchers.any { zones.distanceKm(it.zoneId, z) <= STRIKE_RANGE_KM } }
        return units.filter { it.countryId == country && !it.destroyed && !zones.zone(it.zoneId).sea }
            .groupBy { it.zoneId }.entries.filter { inRange(it.key) }
            .maxByOrNull { e -> e.value.sumOf { it.strength } }?.key
    }

    fun strikeBlocker(country: String): String? {
        if (!geo.atWar(player, country)) return "Seulement contre un pays avec lequel nous sommes en guerre."
        if (launchers().isEmpty()) return "Il faut une escadre de chasse ou un groupe naval disponible."
        wait(STRIKE_KEY, STRIKE_COOLDOWN)?.let { return it }
        if (ctx.state.military.stocks.ammunition < STRIKE_AMMO) return "Stocks de munitions insuffisants."
        if (strikeTarget(country) == null) return "Aucune cible ennemie à portée de nos lanceurs."
        return null
    }

    fun strike(country: String): Result<String> = runCatching {
        strikeBlocker(country)?.let { error(it) }
        val target = strikeTarget(country)!!
        val hit = units.filter { it.zoneId == target && it.countryId == country && !it.destroyed }
        hit.forEach {
            it.strength = (it.strength - STRIKE_STRENGTH).coerceAtLeast(MIN_STRENGTH)
            it.readiness = (it.readiness - STRIKE_READINESS).coerceAtLeast(0.0)
            it.morale = (it.morale - STRIKE_MORALE).coerceAtLeast(0.0)
        }
        ctx.state.countries[country]?.economy?.let { it.pendingOutputShock -= STRIKE_ECONOMY }
        ctx.state.military.stocks.ammunition -= STRIKE_AMMO
        ctx.effects.trigger(EffectSpec("budget.oneOff", STRIKE_COST, days = 1.0), null, emptyMap(), "op:strike")
        mark(STRIKE_KEY)
        val name = countryName(country)
        // Des frappes ne sont jamais parfaitement précises : un drame civil reste possible.
        val collateral = ctx.rng.nextDouble() < COLLATERAL_CHANCE
        if (collateral) {
            ctx.effects.trigger(EffectSpec("opinion.national", -0.004), null, emptyMap(), "op:strike")
            ctx.effects.trigger(EffectSpec("alliance.EU.DISAGREEMENT", -0.01), null, emptyMap(), "op:strike")
        }
        val text = "Frappes sur les forces de $name : ${hit.size} unité(s) touchée(s)" + if (collateral) ", mais des victimes civiles sont signalées." else "."
        report("Frappes de missiles : $name", text, target, if (collateral) Tone.WARNING else Tone.GOOD)
        text
    }

    // ------------------------------------------------------------------ Cyber
    fun cyberBlocker(country: String): String? {
        if (country == player) return "Impossible."
        return wait(CYBER_KEY + country, CYBER_COOLDOWN)
    }

    fun cyber(country: String): Result<String> = runCatching {
        cyberBlocker(country)?.let { error(it) }
        val atWar = geo.atWar(player, country)
        ctx.state.countries[country]?.economy?.let { it.pendingOutputShock -= CYBER_ECONOMY }
        units.filter { it.countryId == country && !it.destroyed }.forEach { it.readiness = (it.readiness - CYBER_READINESS).coerceAtLeast(0.0) }
        ctx.effects.trigger(EffectSpec("budget.oneOff", CYBER_COST, days = 1.0), null, emptyMap(), "op:cyber")
        mark(CYBER_KEY + country)
        val name = countryName(country)
        val detected = !atWar && ctx.rng.nextDouble() < DETECTION_CHANCE
        if (detected) {
            ctx.effects.trigger(EffectSpec("memory.$country.DISAGREEMENT", -0.06), null, emptyMap(), "op:cyber")
            ctx.effects.trigger(EffectSpec("alliance.EU.DISAGREEMENT", -0.01), null, emptyMap(), "op:cyber")
        }
        val text = if (detected) "Cyberattaque contre $name : réussie, mais nos services ont été démasqués. Crise diplomatique."
        else "Cyberattaque contre $name : réseaux électriques et logistique perturbés."
        report("Cyberattaque : $name", text, null, if (detected) Tone.WARNING else Tone.GOOD)
        text
    }

    // ------------------------------------------------------------------ Outils
    private fun launchers(): List<UnitState> = units.filter { u ->
        u.countryId == player && !u.destroyed && u.type in LAUNCHERS && u.readiness > MIN_READINESS
    }

    private fun cooldown(unit: UnitState): String? {
        val last = unit.lastOperationAt ?: return null
        val left = OPERATION_COOLDOWN - last.daysUntil(ctx.now)
        return if (left > 0) "L'unité se remet de sa dernière opération (encore ${left.toInt() + 1} jours)." else null
    }

    private fun wait(key: String, days: Int): String? {
        val last = ctx.state.localActions[key] ?: return null
        val left = days - last.daysUntil(ctx.now)
        return if (left > 0) "Possible à nouveau dans ${left.toInt() + 1} jour(s)." else null
    }

    private fun mark(key: String) {
        ctx.state.localActions[key] = ctx.now
    }

    private fun report(title: String, body: String, zone: String?, tone: Tone = Tone.NEUTRAL) {
        ctx.notifications.post(NotificationCategory.MILITARY, Urgency.IMPORTANT, title, body, zone, journal = false)
        JournalService(ctx).add("Opération", "$title — $body", tone)
    }

    private fun countryName(id: String) = ctx.db.countries[id]?.definition?.name ?: id

    private fun refused(reason: String) = OrderService.Outcome.Refused(reason)

    private companion object {
        const val AIRBORNE = "AIRBORNE_BRIGADE"
        const val MARINES = "MARINE_BRIGADE"
        const val TRANSPORT = "TRANSPORT_WING"
        val LAUNCHERS = setOf("FIGHTER_WING", "SURFACE_GROUP", "CARRIER_GROUP")
        const val AIRBORNE_RANGE_KM = 1500.0
        const val AIRBORNE_FATIGUE = 0.2
        const val FUEL_COST = 0.02
        const val ESCORT_KM = 600.0
        const val MARINE_MORALE = 0.05
        const val OTHER_MORALE = 0.08
        const val OPERATION_COOLDOWN = 20.0
        const val STRIKE_RANGE_KM = 1800.0
        const val STRIKE_KEY = "op|strike"
        const val STRIKE_COOLDOWN = 5
        const val STRIKE_AMMO = 0.03
        const val STRIKE_STRENGTH = 0.06
        const val STRIKE_READINESS = 0.08
        const val STRIKE_MORALE = 0.05
        const val STRIKE_ECONOMY = 0.002
        const val STRIKE_COST = 0.2
        const val MIN_STRENGTH = 0.05
        const val COLLATERAL_CHANCE = 0.15
        const val MIN_READINESS = 0.2
        const val CYBER_KEY = "op|cyber|"
        const val CYBER_COOLDOWN = 21
        const val CYBER_ECONOMY = 0.003
        const val CYBER_READINESS = 0.03
        const val CYBER_COST = 0.05
        const val DETECTION_CHANCE = 0.35
    }
}
