package fr.president.engine.military

import fr.president.engine.data.Domain
import fr.president.engine.effects.EffectSpec
import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import fr.president.engine.simulation.Cadence
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.simulation.SimulationSystem
import fr.president.engine.util.clamp01
import kotlinx.serialization.Serializable

/** Une zone occupée vit : la population accepte plus ou moins l'occupant, une résistance se forme. */
@Serializable
class OccupationState(
    /** Acceptation de l'occupant par la population (0 : hostile, 1 : résignée). */
    var morale: Double = 0.2,
    /** Force de la résistance armée (0 : aucune, 1 : insurrection). */
    var resistance: Double = 0.0,
    var since: fr.president.engine.time.WorldTime? = null,
    /** Dernière action d'administration ou de répression (délai entre deux). */
    var lastAction: fr.president.engine.time.WorldTime? = null,
)

/**
 * Chaque jour, dans chaque zone occupée : la résistance grandit (plus vite en ville, en forêt
 * et en montagne, et quand la population est hostile), la garnison la contient ; les partisans
 * harcèlent l'occupant, coupent son ravitaillement, et sans garnison peuvent libérer la zone.
 * Les unités encerclées perdent courage et finissent par se rendre.
 */
class OccupationSystem : SimulationSystem {
    override val name = "occupation"
    override val cadence = Cadence.DAILY

    override fun run(ctx: SimulationContext) {
        val military = ctx.state.military
        military.occupation.keys.retainAll(military.occupied.keys)
        if (military.occupied.isEmpty() && military.units.values.none { it.isolatedDays > 0 }) return
        val geo = Geopolitics(ctx)
        val service = OccupationService(ctx)
        for ((zoneId, occupier) in military.occupied.toMap()) {
            val o = military.occupation.getOrPut(zoneId) { OccupationState(since = ctx.now) }
            service.day(zoneId, occupier, o, geo)
        }
        encirclement(ctx, geo)
    }

    /** Unités coupées de tout ravitaillement et cernées : le moral s'effondre, puis la reddition. */
    private fun encirclement(ctx: SimulationContext, geo: Geopolitics) {
        val player = ctx.state.player.countryId
        for (u in ctx.state.military.units.values.filter { !it.destroyed && ctx.db.unitType(it.type).domain == Domain.LAND }) {
            val surrounded = !u.supplied && ctx.db.zones.neighbors(u.zoneId).filter { !it.sea }.all { n ->
                geo.atWar(u.countryId, geo.controllerOf(n.id)) || ctx.state.military.units.values.any { e -> e.zoneId == n.id && !e.destroyed && geo.atWar(e.countryId, u.countryId) }
            }
            if (!surrounded) { u.isolatedDays = 0; continue }
            u.isolatedDays++
            u.morale = (u.morale - POCKET_MORALE).coerceAtLeast(0.0)
            if (u.isolatedDays == 1 && (u.countryId == player || geo.atWar(player, u.countryId))) {
                val place = BattlePlaces(ctx).name(u.zoneId)
                ctx.notifications.post(NotificationCategory.MILITARY, if (u.countryId == player) Urgency.URGENT else Urgency.IMPORTANT,
                    if (u.countryId == player) "${u.name} encerclée ($place)" else "Une unité ennemie est prise dans une poche ($place)",
                    if (u.countryId == player) "Plus aucun ravitaillement ne passe. Percez l'encerclement ou repliez-vous avant qu'elle ne se rende."
                    else "Coupée de ses arrières, elle s'affaiblit chaque jour : serrez la poche.", u.zoneId)
            }
            if (u.isolatedDays >= SURRENDER_DAYS && u.morale < SURRENDER_MORALE) {
                u.destroyed = true
                val prisoners = (u.personnel * u.strength).toInt()
                geo.activeWars().firstOrNull { u.countryId in it.participants }?.casualties?.merge(u.countryId, prisoners, Int::plus)
                if (u.countryId == player || geo.atWar(player, u.countryId)) {
                    ctx.notifications.post(NotificationCategory.MILITARY, if (u.countryId == player) Urgency.URGENT else Urgency.IMPORTANT,
                        "${u.name} se rend", "Après ${u.isolatedDays} jours d'encerclement, ${fr.president.engine.util.Formatting.integer(prisoners.toDouble())} soldats sont faits prisonniers.", u.zoneId)
                }
            }
        }
    }

    private companion object {
        const val POCKET_MORALE = 0.04
        const val SURRENDER_DAYS = 10
        const val SURRENDER_MORALE = 0.15
    }
}

/** Règles de l'occupation et actions possibles (administrer, ratisser, soutenir la résistance). */
class OccupationService(private val ctx: SimulationContext) {
    private val player get() = ctx.state.player.countryId
    private val geo get() = Geopolitics(ctx)

    fun state(zoneId: String): OccupationState? = ctx.state.military.occupation[zoneId]

    fun day(zoneId: String, occupier: String, o: OccupationState, geo: Geopolitics) {
        val owner = geo.ownerOf(zoneId)
        val terrain = Terrain(ctx).of(zoneId)?.id
        val cover = when (terrain) { "URBAN" -> 1.5; "FOREST", "MOUNTAIN" -> 1.4; "HILLS" -> 1.15; "DESERT" -> 0.8; else -> 1.0 }
        val garrison = ctx.state.military.units.values.filter { it.zoneId == zoneId && it.countryId == occupier && !it.destroyed && ctx.db.unitType(it.type).domain == Domain.LAND }
        val suppression = garrison.sumOf { it.strength } * SUPPRESSION
        val fighting = garrison.any { it.inCombat }
        o.resistance = (o.resistance + GROWTH * cover * (1 - o.morale) - suppression).clamp01()
        o.morale = (o.morale + (if (garrison.isNotEmpty() && !fighting) ACCEPTANCE else -ACCEPTANCE) - o.resistance * RESISTANCE_DRAG).coerceIn(0.0, MAX_MORALE)
        if (o.resistance < ACTIVE) return
        // Harcèlement : la garnison saigne, son ravitaillement est saboté.
        garrison.forEach {
            it.strength = (it.strength - HARASS * o.resistance).coerceAtLeast(MIN_STRENGTH)
            it.morale = (it.morale - HARASS * o.resistance).coerceAtLeast(0.0)
        }
        val weekly = ctx.now.dayIndex % 7 == (zoneId.hashCode() and 0x7fffffff).toLong() % 7
        if (weekly && (occupier == player || owner == player)) {
            ctx.notifications.post(NotificationCategory.MILITARY, Urgency.INFO,
                if (occupier == player) "Attaque de partisans (${BattlePlaces(ctx).name(zoneId)})" else "La Résistance frappe (${BattlePlaces(ctx).name(zoneId)})",
                if (occupier == player) "Embuscades et sabotages contre notre garnison : la population reste hostile."
                else "Nos compatriotes harcèlent l'occupant : convois détruits, voies ferrées sabotées.", zoneId)
        }
        // Sans garnison, une résistance puissante libère la zone.
        if (garrison.isEmpty() && o.resistance >= UPRISING && geo.atWar(owner, occupier)) {
            Capture(ctx, geo).take(zoneId, owner)
            ctx.notifications.news(NotificationCategory.MILITARY, "Soulèvement : ${BattlePlaces(ctx).name(zoneId)} se libère de l'occupation", zoneId)
        }
    }

    /** Une zone où la résistance est forte ne transmet plus le ravitaillement de l'occupant. */
    fun sabotaged(zoneId: String, country: String): Boolean {
        val o = state(zoneId) ?: return false
        return ctx.state.military.occupied[zoneId] == country && o.resistance >= SABOTAGE
    }

    fun actionBlocker(zoneId: String): String? {
        val o = state(zoneId) ?: return "Zone non occupée."
        val occupier = ctx.state.military.occupied[zoneId]
        if (occupier != player && geo.ownerOf(zoneId) != player) return "Cette zone ne nous concerne pas."
        o.lastAction?.let { if (it.daysUntil(ctx.now) < ACTION_COOLDOWN) return "Une action a déjà été menée il y a moins de ${ACTION_COOLDOWN.toInt()} jours." }
        return null
    }

    /** Occupant : administration civile, aide humanitaire, reconstruction. */
    fun administer(zoneId: String): Result<String> = runCatching {
        actionBlocker(zoneId)?.let { error(it) }
        require(ctx.state.military.occupied[zoneId] == player) { "Seulement dans une zone que nous occupons." }
        val o = state(zoneId)!!
        o.morale = (o.morale + ADMIN_MORALE).coerceAtMost(MAX_MORALE)
        o.resistance = (o.resistance - ADMIN_RESISTANCE).coerceAtLeast(0.0)
        o.lastAction = ctx.now
        ctx.effects.trigger(EffectSpec("budget.oneOff", ADMIN_COST, days = 30.0), null, emptyMap(), "occupation:$zoneId")
        "Administration civile et aide humanitaire : la population se calme un peu (${fr.president.engine.util.Formatting.billions(ADMIN_COST)})."
    }

    /** Occupant : ratissage. Efficace contre la résistance, mais la population et nos alliés s'en souviennent. */
    fun sweep(zoneId: String): Result<String> = runCatching {
        actionBlocker(zoneId)?.let { error(it) }
        require(ctx.state.military.occupied[zoneId] == player) { "Seulement dans une zone que nous occupons." }
        val o = state(zoneId)!!
        o.resistance = (o.resistance - SWEEP_RESISTANCE).coerceAtLeast(0.0)
        o.morale = (o.morale - SWEEP_MORALE).coerceAtLeast(0.0)
        o.lastAction = ctx.now
        ctx.effects.trigger(EffectSpec("alliance.EU.DISAGREEMENT", SWEEP_EU), null, emptyMap(), "occupation:$zoneId")
        ctx.effects.trigger(EffectSpec("opinion.group.left_voters", SWEEP_OPINION), null, emptyMap(), "occupation:$zoneId")
        "Opération de ratissage : la résistance est désorganisée, mais la population nous hait davantage et nos alliés s'inquiètent."
    }

    /** Territoire national occupé : armer et financer la Résistance. */
    fun supportResistance(zoneId: String): Result<String> = runCatching {
        actionBlocker(zoneId)?.let { error(it) }
        require(geo.ownerOf(zoneId) == player && ctx.state.military.occupied[zoneId] != null) { "Seulement dans une zone du territoire national occupée." }
        val o = state(zoneId)!!
        o.resistance = (o.resistance + SUPPORT_RESISTANCE).coerceAtMost(1.0)
        o.lastAction = ctx.now
        ctx.effects.trigger(EffectSpec("budget.oneOff", SUPPORT_COST, days = 30.0), null, emptyMap(), "resistance:$zoneId")
        "Armes, radios et argent parachutés : la Résistance gagne en force."
    }

    private companion object {
        const val GROWTH = 0.012
        const val SUPPRESSION = 0.008
        const val ACCEPTANCE = 0.002
        const val RESISTANCE_DRAG = 0.004
        const val MAX_MORALE = 0.85
        const val ACTIVE = 0.25
        const val HARASS = 0.006
        const val MIN_STRENGTH = 0.05
        const val UPRISING = 0.8
        const val SABOTAGE = 0.5
        const val ACTION_COOLDOWN = 20.0
        const val ADMIN_MORALE = 0.15
        const val ADMIN_RESISTANCE = 0.05
        const val ADMIN_COST = 0.2
        const val SWEEP_RESISTANCE = 0.35
        const val SWEEP_MORALE = 0.12
        const val SWEEP_EU = -0.02
        const val SWEEP_OPINION = -0.005
        const val SUPPORT_RESISTANCE = 0.2
        const val SUPPORT_COST = 0.1
    }
}
