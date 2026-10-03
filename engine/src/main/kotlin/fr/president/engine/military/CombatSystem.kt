package fr.president.engine.military

import fr.president.engine.data.Domain
import fr.president.engine.simulation.Cadence
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.simulation.SimulationSystem
import fr.president.engine.util.clamp01
import kotlin.math.sqrt

/**
 * Résolution horaire des combats. Dans chaque zone où se trouvent des forces de pays en guerre,
 * les puissances s'affrontent (disponibilité, effectifs, moral, ravitaillement, fatigue,
 * expérience, posture défensive, soutien aérien et naval). Les pertes sont proportionnelles
 * au rapport de forces ; le camp qui craque se replie, et la zone peut changer de mains.
 */
class CombatSystem : SimulationSystem {
    override val name = "combat"
    override val cadence = Cadence.HOURLY

    override fun run(ctx: SimulationContext) {
        val units = ctx.state.military.units.values.filter { !it.destroyed }
        units.forEach { it.inCombat = false }
        val geo = Geopolitics(ctx)
        if (geo.activeWars().isEmpty()) return
        val byZone = units.filter { ctx.db.unitType(it.type).domain.let { d -> d == Domain.LAND || d == Domain.SEA } }.groupBy { it.zoneId }
        val support = units.filter { (it.order == UnitOrder.SUPPORT || it.order == UnitOrder.PATROL) && it.targetZoneId != null }
            .groupBy { it.targetZoneId!! }
        for ((zoneId, present) in byZone) {
            val countries = present.map { it.countryId }.distinct()
            if (countries.size < 2) continue
            val war = geo.activeWars().firstOrNull { w -> countries.any { it in w.attackers } && countries.any { it in w.defenders } } ?: continue
            val attackers = present.filter { it.countryId in war.attackers }
            val defenders = present.filter { it.countryId in war.defenders }
            if (attackers.isEmpty() || defenders.isEmpty()) continue
            Battle(ctx, geo, war, zoneId, attackers, defenders, support[zoneId].orEmpty()).resolve()
        }
    }
}

private class Battle(
    private val ctx: SimulationContext,
    private val geo: Geopolitics,
    private val war: War,
    private val zoneId: String,
    private val attackers: List<UnitState>,
    private val defenders: List<UnitState>,
    private val air: List<UnitState>,
) {
    private val p = ctx.db.militaryParameters
    private val controller = geo.controllerOf(zoneId)

    fun resolve() {
        // Le camp qui tient la zone se défend ; l'autre attaque.
        val defendingSide = if (defenders.any { it.countryId == controller } || controller in war.defenders) defenders else attackers
        val attackingSide = if (defendingSide === defenders) attackers else defenders
        (attackingSide + defendingSide).forEach { it.inCombat = true }
        val airA = air.filter { a -> attackingSide.any { geo.coBelligerents(it.countryId).contains(a.countryId) || it.countryId == a.countryId } && inRange(a) }
        val airD = air.filter { a -> defendingSide.any { geo.coBelligerents(it.countryId).contains(a.countryId) || it.countryId == a.countryId } && inRange(a) }
        val powerA = attackingSide.sumOf { power(it, attacking = true) } + airA.sumOf { airPower(it) }
        val powerD = defendingSide.sumOf { power(it, attacking = false) } + airD.sumOf { airPower(it) }
        if (powerA + powerD <= 0.0) return
        val lossA = p.combatLossPerHour * 2 * powerD / (powerA + powerD)
        val lossD = p.combatLossPerHour * 2 * powerA / (powerA + powerD)
        apply(attackingSide, lossA, powerA / (powerA + powerD))
        apply(defendingSide, lossD, powerD / (powerA + powerD))
        airLosses(airA, defendingSide + airD)
        airLosses(airD, attackingSide + airA)
        damageZone()
        checkBreak(attackingSide, defendingSide)
    }

    private fun power(u: UnitState, attacking: Boolean): Double {
        val t = ctx.db.unitType(u.type)
        var base = if (attacking) t.attack else t.defense
        if (!attacking && (u.order == UnitOrder.DEFEND || u.order == UnitOrder.HOLD)) base *= p.defendBonus
        if (geo.ownerOf(zoneId) == u.countryId) base *= p.homeTerritoryBonus
        val ammo = if (u.ammunition < LOW_AMMO) LOW_AMMO_FACTOR else 1.0
        return base * u.strength * u.readiness.coerceAtLeast(MIN_READINESS) * sqrt(u.morale.coerceAtLeast(MIN_MORALE)) *
            ammo * (1 - FATIGUE_WEIGHT * u.fatigue) * (1 + EXPERIENCE_WEIGHT * u.experience)
    }

    private fun airPower(u: UnitState): Double {
        val t = ctx.db.unitType(u.type)
        if (u.ammunition < LOW_AMMO) return 0.0
        return t.attack * u.strength * u.readiness * AIR_SUPPORT_FACTOR
    }

    private fun inRange(u: UnitState): Boolean {
        val t = ctx.db.unitType(u.type)
        if (t.domain != Domain.AIR) return t.domain == Domain.SEA && ctx.db.zones.distanceKm(u.zoneId, zoneId) <= t.rangeKm
        return ctx.db.zones.distanceKm(u.zoneId, zoneId) <= OrderService(ctx).effectiveRange(u, t)
    }

    private fun apply(side: List<UnitState>, loss: Double, share: Double) {
        val hour = 1.0 / HOURS
        for (u in side) {
            val t = ctx.db.unitType(u.type)
            val before = u.strength
            u.strength = (u.strength - loss).coerceAtLeast(0.0)
            val casualties = (u.personnel * (before - u.strength) * p.casualtyRateFactor).toInt()
            war.casualties.merge(u.countryId, casualties, Int::plus)
            if (u.countryId == ctx.state.player.countryId) ctx.state.military.recentCasualties += casualties
            u.ammunition = (u.ammunition - t.ammoPerCombatDay * hour).coerceAtLeast(0.0)
            u.fatigue = (u.fatigue + p.fatiguePerCombatDay * hour).clamp01()
            u.morale = (u.morale + p.moraleSwingPerDay * hour * (share - NEUTRAL) * 2).clamp01()
            u.experience = (u.experience + EXPERIENCE_GAIN * hour).clamp01()
            if (u.strength < DESTROYED) destroy(u)
        }
    }

    private fun airLosses(airUnits: List<UnitState>, opponents: List<UnitState>) {
        if (airUnits.isEmpty()) return
        val defense = opponents.sumOf { ctx.db.unitType(it.type).airDefense * it.strength }
        val own = airUnits.sumOf { ctx.db.unitType(it.type).attack * it.strength }
        if (own <= 0) return
        val loss = p.combatLossPerHour * defense / (defense + own) * AIR_LOSS_FACTOR
        airUnits.forEach { a ->
            a.strength = (a.strength - loss).coerceAtLeast(0.0)
            a.ammunition = (a.ammunition - ctx.db.unitType(a.type).ammoPerCombatDay / HOURS).coerceAtLeast(0.0)
            if (a.strength < DESTROYED) destroy(a)
        }
    }

    private fun damageZone() {
        val d = (ctx.state.military.damage[zoneId] ?: 0.0) + p.zoneDamagePerCombatDay / HOURS
        ctx.state.military.damage[zoneId] = d.clamp01()
        ctx.db.zones.zone(zoneId).department?.let { dept ->
            ctx.effects.apply("dept.$dept.approval", DEPARTMENT_SHOCK_PER_HOUR)
            ctx.state.playerCountry.economy.pendingOutputShock += OUTPUT_LOSS_PER_HOUR
        }
    }

    private fun checkBreak(attackingSide: List<UnitState>, defendingSide: List<UnitState>) {
        val defenderBroken = broken(defendingSide)
        val attackerBroken = broken(attackingSide)
        when {
            defenderBroken -> {
                defendingSide.forEach { retreat(it) }
                attackingSide.firstOrNull { !it.destroyed }?.let { winner ->
                    if (ctx.db.unitType(winner.type).domain == Domain.LAND) Capture(ctx, geo).take(zoneId, winner.countryId)
                }
            }
            attackerBroken -> attackingSide.forEach { retreat(it) }
        }
    }

    private fun broken(side: List<UnitState>): Boolean {
        val alive = side.filter { !it.destroyed }
        if (alive.isEmpty()) return true
        return alive.map { it.strength }.average() < p.retreatStrength || alive.map { it.morale }.average() < p.retreatMorale
    }

    /** Repli vers une zone amie adjacente ; encerclée, l'unité se rend. */
    private fun retreat(u: UnitState) {
        if (u.destroyed) return
        val friends = geo.coBelligerents(u.countryId) + u.countryId
        val escape = ctx.db.zones.neighbors(zoneId).filter { z ->
            val t = ctx.db.unitType(u.type).domain
            (if (t == Domain.SEA) z.sea else !z.sea) && geo.controllerOf(z.id) in friends &&
                ctx.state.military.units.values.none { e -> e.zoneId == z.id && !e.destroyed && geo.atWar(e.countryId, u.countryId) }
        }.minByOrNull { ctx.db.zones.distanceKm(it.id, u.homeZoneId.ifBlank { it.id }) }
        if (escape == null && ctx.db.unitType(u.type).domain == Domain.LAND) {
            destroy(u, surrendered = true)
            return
        }
        u.path.clear()
        escape?.let { u.path.add(it.id) }
        u.order = UnitOrder.RETREAT
        u.targetZoneId = escape?.id
        u.morale = (u.morale - RETREAT_MORALE_LOSS).clamp01()
    }

    private fun destroy(u: UnitState, surrendered: Boolean = false) {
        if (u.destroyed) return
        val casualties = (u.personnel * u.strength * if (surrendered) 1.0 else p.casualtyRateFactor).toInt()
        u.destroyed = true
        u.strength = 0.0
        war.casualties.merge(u.countryId, casualties, Int::plus)
        val player = ctx.state.player.countryId
        if (u.countryId == player || geo.atWar(player, u.countryId)) {
            ctx.notifications.post(
                fr.president.engine.notifications.NotificationCategory.MILITARY,
                if (u.countryId == player) fr.president.engine.notifications.Urgency.URGENT else fr.president.engine.notifications.Urgency.IMPORTANT,
                if (surrendered) "${u.name} encerclée et capturée" else "${u.name} détruite",
                if (u.countryId == player) "Une perte lourde pour nos armées." else "Une unité ennemie a été mise hors de combat.", zoneId,
            )
        }
    }

    private companion object {
        const val HOURS = 24.0
        const val NEUTRAL = 0.5
        const val LOW_AMMO = 0.1
        const val LOW_AMMO_FACTOR = 0.3
        const val MIN_READINESS = 0.2
        const val MIN_MORALE = 0.05
        const val FATIGUE_WEIGHT = 0.5
        const val EXPERIENCE_WEIGHT = 0.3
        const val EXPERIENCE_GAIN = 0.01
        const val AIR_SUPPORT_FACTOR = 0.8
        const val AIR_LOSS_FACTOR = 0.6
        const val DESTROYED = 0.05
        const val RETREAT_MORALE_LOSS = 0.1
        const val DEPARTMENT_SHOCK_PER_HOUR = -0.002
        const val OUTPUT_LOSS_PER_HOUR = -0.00002
    }
}
