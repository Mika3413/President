package fr.president.engine.readout

import fr.president.engine.military.Geopolitics
import fr.president.engine.military.Intelligence
import fr.president.engine.military.UnitState
import fr.president.engine.military.War
import fr.president.engine.military.WarStatus
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.util.Formatting

/** Fiches militaires lisibles : unité, forces, guerres, logistique. */
class MilitaryReadouts(private val ctx: SimulationContext) {
    private val scales = ctx.db.readouts
    private val geo = Geopolitics(ctx)
    private val player get() = ctx.state.player.countryId

    fun place(zoneId: String): String {
        val zone = ctx.db.zones.zones[zoneId] ?: return "?"
        if (zone.sea) return "En mer"
        val owner = geo.ownerOf(zoneId)
        val name = zone.department?.takeIf { owner == player }?.let { d -> ctx.playerData.territory?.departments?.firstOrNull { it.code == d }?.name }
            ?: ctx.db.countries[owner]?.definition?.name ?: owner
        val controller = geo.controllerOf(zoneId)
        return if (controller != owner) "$name (occupé par ${ctx.db.countries[controller]?.definition?.name ?: controller})" else name
    }

    fun unitTitle(u: UnitState) = u.name

    fun unitSubtitle(u: UnitState): String {
        val type = ctx.db.unitType(u.type)
        val status = when {
            u.inCombat -> "au combat"
            u.path.isNotEmpty() -> "en mouvement"
            else -> u.order.label.lowercase()
        }
        return "${type.label} · ${place(u.zoneId)} · $status"
    }

    fun unit(u: UnitState): List<Indicator> {
        val own = u.countryId == player
        val strength = if (own) u.strength else Intelligence(ctx).estimatedStrength(player, u)
        val s = scales.describe("strength", strength)
        val list = mutableListOf(
            Indicator("Disponibilité", Formatting.percent(u.readiness), scales.describe("readiness", u.readiness).tone,
                scales.describe("readiness", u.readiness).label.lowercase().replaceFirstChar { it.uppercase() }),
            Indicator("Effectifs", s.label, s.tone, if (own) "" else "Estimation de nos services de renseignement.",
                listOf("Personnels estimés" to Formatting.integer((ctx.db.unitType(u.type).personnel * strength).toLong()))),
        )
        if (own) {
            list += Indicator("Moral", scales.describe("morale", u.morale).label, scales.describe("morale", u.morale).tone)
            list += Indicator("Munitions", scales.describe("stock", u.ammunition).label, scales.describe("stock", u.ammunition).tone)
            list += Indicator("Carburant", scales.describe("stock", u.fuel).label, scales.describe("stock", u.fuel).tone)
            list += Indicator("Fatigue", scales.describe("fatigue", u.fatigue).label, scales.describe("fatigue", u.fatigue).tone)
            list += Indicator("Ravitaillement", if (u.supplied) "assuré" else "COUPÉ", if (u.supplied) Tone.GOOD else Tone.BAD,
                if (u.supplied) "" else "L'unité est isolée : ses stocks et son moral s'épuisent.",
                listOf(
                    "Moral" to Formatting.percent(u.morale), "Munitions" to Formatting.percent(u.ammunition),
                    "Carburant" to Formatting.percent(u.fuel), "Fatigue" to Formatting.percent(u.fatigue),
                    "Expérience" to Formatting.percent(u.experience), "Effectifs" to Formatting.percent(u.strength),
                ) + u.equipment.map { (k, v) -> k to v.toString() })
        }
        return list
    }

    fun war(w: War): Indicator {
        val ours = w.sideOf(player)
        val status = when (w.status) {
            WarStatus.ACTIVE -> "EN COURS"
            WarStatus.CEASEFIRE -> "CESSEZ-LE-FEU"
            WarStatus.ENDED -> "TERMINÉE"
        }
        val held = { c: String -> ctx.state.military.occupied.count { (z, o) -> o == c && geo.ownerOf(z) in w.participants } }
        val details = w.participants.map { c ->
            val name = ctx.db.country(c).definition.name
            name to "pertes ${Formatting.integer((w.casualties[c] ?: 0).toLong())} · ${scales.describe("weariness", w.weariness[c] ?: 0.0).label.lowercase()} · ${held(c)} zone(s) tenues"
        }
        val title = "${w.attackers.joinToString { ctx.db.country(it).definition.name }} contre ${w.defenders.joinToString { ctx.db.country(it).definition.name }}"
        val tone = when { w.status == WarStatus.ENDED -> Tone.NEUTRAL; ours != War.NONE -> Tone.BAD; else -> Tone.WARNING }
        return Indicator(title, status, tone, w.outcome.ifBlank { w.cause }, details)
    }

    fun logistics(): List<Indicator> {
        val s = ctx.state.military.stocks
        val ind = { label: String, v: Double -> scales.describe("stock", v).let { Indicator(label, it.label.uppercase(), it.tone, "", listOf("Niveau" to Formatting.percent(v))) } }
        return listOf(
            ind("Munitions (stocks nationaux)", s.ammunition),
            ind("Carburant (stocks nationaux)", s.fuel),
            ind("Pièces détachées", s.spareParts),
            Indicator("Économie de guerre", if (s.warEconomy) "ACTIVE" else "Non", if (s.warEconomy) Tone.WARNING else Tone.NEUTRAL,
                "Accélère la production de munitions au prix d'un coût budgétaire mensuel."),
            Indicator("Réserves", if (s.reservists > 0) "MOBILISÉES" else "Disponibles", Tone.NEUTRAL,
                "La mobilisation crée des brigades de réserve en quelques semaines."),
        )
    }
}
