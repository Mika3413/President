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

    private val terrain = Terrain(ctx)
    private val forts = FortificationService(ctx)
    private val warfare = ctx.db.warfare
    private val modifiers = mutableListOf<String>()
    /** Contexte de la bataille, calculé une fois par heure. */
    private var fortBonus = 0.0
    private var coastalBonus = 0.0
    private var defenderCategory = ""
    private var attackerCategory = ""

    fun resolve() {
        // Le camp qui tient la zone se défend ; l'autre attaque.
        val defendingSide = if (defenders.any { it.countryId == controller } || controller in war.defenders) defenders else attackers
        val attackingSide = if (defendingSide === defenders) attackers else defenders
        (attackingSide + defendingSide).forEach { it.inCombat = true }
        val defendingCamp = defendingSide.flatMap { geo.coBelligerents(it.countryId) + it.countryId }.toSet()
        val attackingCamp = attackingSide.flatMap { geo.coBelligerents(it.countryId) + it.countryId }.toSet()
        prepare(attackingSide, defendingSide, defendingCamp)
        val airA = air.filter { a -> attackingSide.any { geo.coBelligerents(it.countryId).contains(a.countryId) || it.countryId == a.countryId } && inRange(a) }
        val airD = air.filter { a -> defendingSide.any { geo.coBelligerents(it.countryId).contains(a.countryId) || it.countryId == a.countryId } && inRange(a) }
        val powerA = attackingSide.sumOf { power(it, attacking = true) } + airA.sumOf { airPower(it) }
        val powerD = defendingSide.sumOf { power(it, attacking = false) } + airD.sumOf { airPower(it) }
        if (powerA + powerD <= 0.0) return
        val lossA = p.combatLossPerHour * 2 * powerD / (powerA + powerD)
        val lossD = p.combatLossPerHour * 2 * powerA / (powerA + powerD)
        val record = record(attackingSide, defendingSide)
        record.attackerLosses += apply(attackingSide, lossA, powerA / (powerA + powerD))
        record.defenderLosses += apply(defendingSide, lossD, powerD / (powerA + powerD))
        record.attackerPower = powerA
        record.defenderPower = powerD
        record.modifiers.clear(); record.modifiers.addAll(modifiers)
        airLosses(airA, defendingSide + airD, defendingCamp)
        airLosses(airD, attackingSide + airA, attackingCamp)
        forts.batter(zoneId, artilleryShare(attackingSide))
        damageZone()
        checkBreak(attackingSide, defendingSide, record)
    }

    /** Terrain, fortifications, fleuve, débarquement, types en présence : les avantages de chaque camp. */
    private fun prepare(attackingSide: List<UnitState>, defendingSide: List<UnitState>, defendingCamp: Set<String>) {
        modifiers.clear()
        val f = warfare ?: return
        terrain.of(zoneId)?.let { t -> if (t.defense != 1.0) modifiers += "${t.icon} ${t.label} : défense ×${fmt(t.defense)}" }
        val artillery = artilleryShare(attackingSide)
        val line = forts.effect(zoneId, "line", "defense", defendingCamp)
        fortBonus = line * (1 - artillery * f.artilleryVsFortification)
        if (line > 0) modifiers += "▦ Fortifications : défense +${Math.round(fortBonus * 100)} %" + if (artillery > 0.05) " (réduite par l'artillerie)" else ""
        coastalBonus = forts.effect(zoneId, "coastal", "amphibious", defendingCamp)
        defenderCategory = dominant(defendingSide)
        attackerCategory = dominant(attackingSide)
        terrain.matchup(attackerCategory, defenderCategory)?.takeIf { it.label.isNotEmpty() }?.let {
            modifiers += (if (it.factor >= 1) "✦ " else "✕ ") + it.label.replaceFirstChar { c -> c.uppercase() } + " (×${fmt(it.factor)})"
        }
        attackingSide.firstNotNullOfOrNull { u -> u.cameFrom?.let { terrain.riverBetween(it, zoneId) } }?.let { modifiers += "≈ Franchissement du fleuve $it : attaque ×${fmt(f.riverCrossing)}" }
        if (attackingSide.any { u -> u.cameFrom?.let { ctx.db.zones.zones[it]?.sea } == true }) {
            modifiers += "⚓ Débarquement : attaque ×${fmt(f.amphibiousPenalty * (1 - coastalBonus))}" + if (coastalBonus > 0) " (batteries côtières)" else ""
        }
        terrain.of(zoneId)?.let { t ->
            val bad = (attackingSide + defendingSide).map { it.type }.distinct().filter { (t.categories[terrain.category(it)] ?: 1.0) < 1.0 }
            if (bad.isNotEmpty()) modifiers += "${t.icon} Mal à l'aise ici : " + bad.joinToString(", ") { ctx.db.unitType(it).label.lowercase() }
        }
    }

    private fun dominant(side: List<UnitState>): String =
        side.groupBy { terrain.category(it.type) }.maxByOrNull { (_, us) -> us.sumOf { ctx.db.unitType(it.type).attack * it.strength } }?.key.orEmpty()

    private fun artilleryShare(side: List<UnitState>): Double {
        val total = side.sumOf { ctx.db.unitType(it.type).attack * it.strength }
        if (total <= 0) return 0.0
        return side.filter { terrain.category(it.type) == "ARTILLERY" }.sumOf { ctx.db.unitType(it.type).attack * it.strength } / total
    }

    private fun power(u: UnitState, attacking: Boolean): Double {
        val t = ctx.db.unitType(u.type)
        var base = if (attacking) t.attack else t.defense
        if (!attacking && (u.order == UnitOrder.DEFEND || u.order == UnitOrder.HOLD)) base *= p.defendBonus
        if (geo.ownerOf(zoneId) == u.countryId) base *= p.homeTerritoryBonus
        warfare?.let { f ->
            base *= terrain.unitFactor(zoneId, u.type)
            if (attacking) {
                terrain.matchup(terrain.category(u.type), defenderCategory)?.let { base *= it.factor }
                val from = u.cameFrom
                if (from != null && terrain.riverBetween(from, zoneId) != null) base *= f.riverCrossing
                if (from != null && ctx.db.zones.zones[from]?.sea == true) base *= f.amphibiousPenalty * (1 - coastalBonus)
            } else base *= terrain.defenseFactor(zoneId) * (1 + fortBonus)
        }
        val ammo = if (u.ammunition < LOW_AMMO) LOW_AMMO_FACTOR else 1.0
        return base * u.strength * u.readiness.coerceAtLeast(MIN_READINESS) * sqrt(u.morale.coerceAtLeast(MIN_MORALE)) *
            ammo * (1 - FATIGUE_WEIGHT * u.fatigue) * (1 + EXPERIENCE_WEIGHT * u.experience)
    }

    /** Rapport de la bataille en cours dans la zone (créé au premier coup de feu). */
    private fun record(attackingSide: List<UnitState>, defendingSide: List<UnitState>): BattleRecord {
        val battles = ctx.state.military.battles
        // Les combats qui reprennent dans la journée prolongent la même bataille.
        val attackingCountries = attackingSide.map { it.countryId }.toSet()
        val existing = battles.lastOrNull { it.zoneId == zoneId && it.lastAt.daysUntil(ctx.now) < MERGE_DAYS }
            ?.takeIf { r -> r.attackers.any { it in attackingCountries } }?.also { it.outcome = "" }
        val r = existing ?: BattleRecord(ctx.state.newId("btl"), zoneId, ctx.now, ctx.now,
            attackingSide.map { it.countryId }.distinct().toMutableList(), defendingSide.map { it.countryId }.distinct().toMutableList()).also {
            battles += it
            while (battles.size > MAX_BATTLES) battles.removeAt(0)
            announce(it)
        }
        r.lastAt = ctx.now
        r.hours++
        (attackingSide.map { it.countryId } - r.attackers.toSet() - r.defenders.toSet()).distinct().forEach { r.attackers += it }
        (defendingSide.map { it.countryId } - r.defenders.toSet() - r.attackers.toSet()).distinct().forEach { r.defenders += it }
        return r
    }

    private fun announce(r: BattleRecord) {
        val player = ctx.state.player.countryId
        val camp = geo.coBelligerents(player) + player
        if (r.attackers.none { it in camp } && r.defenders.none { it in camp }) return
        ctx.notifications.post(fr.president.engine.notifications.NotificationCategory.MILITARY, fr.president.engine.notifications.Urgency.IMPORTANT,
            BattlePlaces(ctx).title(zoneId) + " : les combats commencent", (listOf(terrain.of(zoneId)?.label?.let { "Terrain : ${it.lowercase()}" }) + modifiers.take(2)).filterNotNull().joinToString(" · ").ifEmpty { "Les combats commencent." }, zoneId, journal = false)
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

    private fun apply(side: List<UnitState>, loss: Double, share: Double): Int {
        val hour = 1.0 / HOURS
        var total = 0
        for (u in side) {
            val t = ctx.db.unitType(u.type)
            val before = u.strength
            u.strength = (u.strength - loss).coerceAtLeast(0.0)
            val casualties = (u.personnel * (before - u.strength) * p.casualtyRateFactor).toInt()
            war.casualties.merge(u.countryId, casualties, Int::plus)
            total += casualties
            if (u.countryId == ctx.state.player.countryId) ctx.state.military.recentCasualties += casualties
            u.ammunition = (u.ammunition - t.ammoPerCombatDay * hour).coerceAtLeast(0.0)
            u.fatigue = (u.fatigue + p.fatiguePerCombatDay * hour).clamp01()
            u.morale = (u.morale + p.moraleSwingPerDay * hour * (share - NEUTRAL) * 2).clamp01()
            u.experience = (u.experience + EXPERIENCE_GAIN * hour).clamp01()
            if (u.strength < DESTROYED) destroy(u)
        }
        return total
    }

    private fun airLosses(airUnits: List<UnitState>, opponents: List<UnitState>, opponentCamp: Set<String>) {
        if (airUnits.isEmpty()) return
        // Défense sol-air des unités en face, plus les batteries fixes qui couvrent la zone.
        val defense = opponents.sumOf { ctx.db.unitType(it.type).airDefense * it.strength } + forts.airDefense(zoneId, opponentCamp)
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

    private fun checkBreak(attackingSide: List<UnitState>, defendingSide: List<UnitState>, record: BattleRecord) {
        val defenderBroken = broken(defendingSide)
        val attackerBroken = broken(attackingSide)
        if (defenderBroken) record.outcome = "Victoire de l'attaquant"
        else if (attackerBroken) record.outcome = "Le défenseur tient bon"
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
        const val MAX_BATTLES = 40
        const val MERGE_DAYS = 1.0
    }

    private fun fmt(v: Double) = String.format(java.util.Locale.FRENCH, "%.2f", v).trimEnd('0').trimEnd(',')
}
