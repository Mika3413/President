package fr.president.engine.readout

import fr.president.engine.military.BattlePlaces
import fr.president.engine.military.BattleRecord
import fr.president.engine.military.FortLevelDef
import fr.president.engine.military.FortTypeDef
import fr.president.engine.military.Fortification
import fr.president.engine.military.FortificationService
import fr.president.engine.military.Geopolitics
import fr.president.engine.military.Terrain
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.util.Formatting

/** Ce que l'interface montre des ouvrages militaires, du terrain et des batailles. */
class WarfareReadout(private val ctx: SimulationContext) {
    private val forts get() = FortificationService(ctx)
    private val geo get() = Geopolitics(ctx)

    data class WorkRow(val work: Fortification, val icon: String, val title: String, val detail: String, val tone: Tone)
    data class BuildOption(val def: FortTypeDef, val level: FortLevelDef, val upgrade: Boolean, val days: Long, val effects: String, val blocker: String?)
    data class ZoneView(val zoneId: String, val place: String, val terrain: String, val terrainHint: String, val controller: String, val works: List<WorkRow>, val options: List<BuildOption>,
        val occupation: OccupationView? = null)
    data class OccupationAction(val id: String, val label: String, val description: String)
    data class OccupationView(val title: String, val morale: Double, val resistance: Double, val text: String, val actions: List<OccupationAction>, val blocker: String?, val weOccupy: Boolean)
    data class BattleView(val record: BattleRecord, val title: String, val status: String, val sides: String, val losses: String, val ratio: Double?, val modifiers: List<String>, val tone: Tone, val ours: Boolean)

    /** Zones de théâtre d'un département (au moins une : la plus proche de ses villes). */
    fun zonesOfDepartment(code: String): List<String> {
        val own = ctx.db.zones.ofDepartment(code).map { it.id }
        if (own.isNotEmpty()) return own
        val cities = ctx.playerData.territory?.cities.orEmpty().filter { it.department == code }
        if (cities.isEmpty()) return emptyList()
        val lon = cities.map { it.lon }.average()
        val lat = cities.map { it.lat }.average()
        val player = ctx.state.player.countryId
        return listOfNotNull(ctx.db.zones.nearest(lon, lat) { !it.sea && it.owner == player }?.id)
    }

    fun zone(zoneId: String): ZoneView {
        val t = Terrain(ctx).of(zoneId)
        val controller = geo.controllerOf(zoneId)
        val works = forts.at(zoneId).map { row(it) }
        val options = forts.types().mapNotNull { def ->
            val level = forts.nextLevel(def.id, zoneId) ?: return@mapNotNull null
            if (def.coastalOnly && ctx.db.zones.zones[zoneId]?.coastal != true) return@mapNotNull null
            BuildOption(def, level, (forts.work(zoneId, def.id)?.level ?: 0) > 0, forts.days(level), effects(level), forts.blocker(def.id, zoneId))
        }
        return ZoneView(zoneId, BattlePlaces(ctx).name(zoneId), t?.let { "${it.icon} ${it.label}" } ?: "", t?.hint.orEmpty(),
            ctx.db.countries[controller]?.definition?.name ?: controller, works, options, occupation(zoneId))
    }

    /** Zone occupée : la population, la résistance, et ce que l'on peut faire (occupant ou pays occupé). */
    fun occupation(zoneId: String): OccupationView? {
        val service = fr.president.engine.military.OccupationService(ctx)
        val o = service.state(zoneId) ?: return null
        val occupier = ctx.state.military.occupied[zoneId] ?: return null
        val owner = geo.ownerOf(zoneId)
        val player = ctx.state.player.countryId
        fun name(c: String) = ctx.db.countries[c]?.definition?.name ?: c
        val weOccupy = occupier == player
        val text = when {
            o.resistance >= 0.8 -> "Insurrection : sans garnison, la zone se libérera."
            o.resistance >= 0.5 -> "Résistance armée : convois sabotés, le ravitaillement de l'occupant ne passe plus."
            o.resistance >= 0.25 -> "Des partisans harcèlent la garnison."
            else -> "La population se tient tranquille pour l'instant."
        }
        val actions = when {
            weOccupy -> listOf(
                OccupationAction("administer", "Administration civile et aide (0,2 Md€)", "La population se calme ; la résistance recrute moins."),
                OccupationAction("sweep", "Opération de ratissage", "Désorganise la résistance, mais la population nous hait davantage et nos alliés s'inquiètent."),
            )
            owner == player -> listOf(OccupationAction("support", "Soutenir la Résistance (0,1 Md€)", "Armes, radios, argent : nos compatriotes harcèlent l'occupant et peuvent libérer la zone."))
            else -> emptyList()
        }
        return OccupationView("Occupée par ${name(occupier)}" + if (owner != occupier) " (territoire ${fr.president.engine.data.CountryNames(ctx.db.country(owner).definition).of})" else "",
            o.morale, o.resistance, text, actions, if (actions.isEmpty()) null else service.actionBlocker(zoneId), weOccupy)
    }

    fun occupationAction(zoneId: String, id: String): Result<String> {
        val service = fr.president.engine.military.OccupationService(ctx)
        return when (id) {
            "administer" -> service.administer(zoneId)
            "sweep" -> service.sweep(zoneId)
            else -> service.supportResistance(zoneId)
        }
    }

    fun row(w: Fortification): WorkRow {
        val def = forts.def(w.type)
        val levelLabel = def?.levels?.getOrNull((if (w.level > 0) w.level else w.building) - 1)?.label ?: def?.label.orEmpty()
        val detail = buildList {
            if (w.level > 0) add("niveau ${w.level}/${def?.levels?.size ?: 1}")
            w.readyAt?.let { add("chantier : ${Formatting.integer(ctx.now.daysUntil(it).coerceAtLeast(0.0))} j restant(s)") }
            if (w.level > 0) add("état ${Math.round(w.condition * 100)} %")
            if (w.countryId != ctx.state.player.countryId) add(ctx.db.countries[w.countryId]?.definition?.name ?: w.countryId)
        }.joinToString(" · ")
        val tone = when {
            w.readyAt != null -> Tone.NEUTRAL
            w.condition < 0.5 -> Tone.BAD
            w.condition < 0.85 -> Tone.WARNING
            else -> Tone.GOOD
        }
        return WorkRow(w, def?.icon ?: "▦", "${def?.label ?: w.type} — $levelLabel", detail, tone)
    }

    /** Effets d'un niveau d'ouvrage, en clair. */
    fun effects(level: FortLevelDef): String = level.effects.mapNotNull { (k, v) ->
        when (k) {
            "defense" -> "défenseurs +${pct(v)}"
            "airDefense" -> "défense aérienne ${Formatting.integer(v)}"
            "radiusKm" -> "rayon ${Formatting.integer(v)} km"
            "interception" -> "missiles interceptés ${pct(v)}"
            "radar" -> "défense sol-air +${pct(v)}"
            "intel" -> "détection +${Formatting.integer(v)} zones"
            "airRange" -> "rayon d'action +${pct(v)}"
            "rearm" -> "réarmement +${pct(v)}"
            "recovery" -> "récupération +${pct(v)}"
            "production" -> "production d'unités −${pct(v)}"
            "training" -> "nouvelles unités mieux entraînées"
            "supply" -> "ravitaillement +${Formatting.integer(v)} zone(s)"
            "resupply" -> "recomplètement +${pct(v)}"
            "amphibious" -> "débarquements ennemis −${pct(v)}"
            "naval" -> "navires ennemis au large −${pct(v)}/jour"
            else -> null
        }
    }.joinToString(" · ")

    /** Ouvrages du joueur : achevés puis chantiers. */
    fun ownWorks(): List<Pair<String, WorkRow>> = forts.of(ctx.state.player.countryId)
        .sortedWith(compareBy({ it.readyAt == null }, { it.type }))
        .map { BattlePlaces(ctx).name(it.zoneId) to row(it) }

    /** Batailles en cours d'abord, puis les dernières terminées ; celles de notre camp en tête. */
    fun battles(limit: Int = 8): List<BattleView> {
        val player = ctx.state.player.countryId
        val camp = geo.coBelligerents(player) + player
        return ctx.state.military.battles.asReversed()
            .sortedWith(compareBy({ !it.active(ctx.now) }, { b -> !(b.attackers + b.defenders).any { it in camp } }))
            .take(limit).map { view(it, camp) }
    }

    fun battleAt(zoneId: String): BattleView? {
        val player = ctx.state.player.countryId
        return ctx.state.military.battles.lastOrNull { it.zoneId == zoneId }?.let { view(it, geo.coBelligerents(player) + player) }
    }

    private fun view(b: BattleRecord, camp: Set<String>): BattleView {
        fun names(list: List<String>) = list.joinToString(", ") { ctx.db.countries[it]?.definition?.name ?: it }
        val active = b.active(ctx.now)
        val ours = (b.attackers + b.defenders).any { it in camp }
        val weAttack = b.attackers.any { it in camp }
        val ratio = (if (b.defenderPower > 0 && b.attackerPower > 0) (if (weAttack || !ours) b.attackerPower / b.defenderPower else b.defenderPower / b.attackerPower) else null)
            ?.takeIf { it in 0.05..50.0 }
        val days = b.hours / 24
        val duration = if (days >= 1) "$days j ${b.hours % 24} h" else "${b.hours} h"
        val status = if (active) "En cours depuis $duration" else "${b.outcome.ifEmpty { "Combats interrompus" }} · $duration"
        val won = when (b.outcome) {
            "Victoire de l'attaquant" -> weAttack
            "Le défenseur tient bon" -> ours && !weAttack
            else -> null
        }
        val tone = when {
            active && ratio != null && ours -> if (ratio >= 1.3) Tone.GOOD else if (ratio >= 0.8) Tone.WARNING else Tone.BAD
            won == true -> Tone.GOOD
            won == false && ours -> Tone.BAD
            else -> Tone.NEUTRAL
        }
        return BattleView(b, BattlePlaces(ctx).title(b.zoneId), status, "${names(b.attackers)} ⚔ ${names(b.defenders)}",
            "Pertes : ${Formatting.integer(b.attackerLosses.toDouble())} (attaquants) · ${Formatting.integer(b.defenderLosses.toDouble())} (défenseurs)",
            ratio, b.modifiers.toList(), tone, ours)
    }

    private fun pct(v: Double) = "${Math.round(v * 100)} %"
}
