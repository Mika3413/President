package fr.president.game.ui.panels

import com.badlogic.gdx.scenes.scene2d.ui.Table
import fr.president.engine.data.Domain
import fr.president.engine.military.OrderService
import fr.president.engine.military.UnitOrder
import fr.president.game.map.MapSelection
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui
import fr.president.game.ui.widgets.IndicatorView

/** Fiche d'une unité et ses ordres. Les ordres ciblés se donnent ensuite d'un toucher sur la carte. */
class UnitSheet(private val ui: Ui, private val nav: Navigator, private val expanded: MutableSet<String>) {
    var message: String? = null

    fun build(into: Table, unitId: String) {
        val session = nav.session
        val unit = session.state.military.units[unitId] ?: return
        val own = unit.countryId == session.state.player.countryId
        message?.let { into.add(ui.label(it, "small", Theme.accent, wrap = true)).growX().padBottom(6f).row() }
        into.add(ui.label(session.militaryReadouts.unitSubtitle(unit), "muted", wrap = true)).growX().row()
        if (!own) into.add(ui.label("Forces de ${session.db.country(unit.countryId).definition.name}", "small", Theme.warning)).row()
        // Bataille en cours dans la zone : rapport de combat.
        session.warfare.battleAt(unit.zoneId)?.takeIf { it.record.active(session.state.time) }?.let { battle(into, it) }
        session.militaryReadouts.unit(unit).forEach { into.add(IndicatorView(ui, it, expanded)).growX().padBottom(6f).row() }
        if (own && !unit.destroyed) orders(into, unitId)
        if (!unit.destroyed) zone(into, unit.zoneId, own)
        val others = session.state.military.units.values.filter { it.zoneId == unit.zoneId && it.id != unit.id && !it.destroyed }
            .filter { it.countryId == unit.countryId || session.military.visibleUnits().contains(it) }
        if (others.isNotEmpty()) {
            into.add(ui.label("Autres unités dans la zone", "bold")).padTop(6f).row()
            others.forEach { o -> into.add(ui.button(o.name, "flat") { nav.select(MapSelection.Unit(o.id)) }).left().row() }
        }
    }

    private val fortifications = fr.president.game.ui.widgets.FortificationView(ui, nav) { result -> message = result; nav.refresh() }

    /** Terrain et ouvrages de la zone ; nos unités peuvent la fortifier (y compris en territoire conquis). */
    private fun zone(into: Table, zoneId: String, own: Boolean) {
        val session = nav.session
        val v = session.warfare.zone(zoneId)
        val key = "zone:$zoneId"
        val open = key in expanded
        val works = if (v.works.isEmpty()) "" else " · ${v.works.size} ouvrage(s)"
        into.add(ui.button((if (open) "▼ " else "▶ ") + "Zone : ${v.terrain}$works", "flat") {
            if (open) expanded.remove(key) else expanded.add(key); nav.refresh()
        }).left().padTop(6f).row()
        if (!open) return
        if (!own) { v.works.forEach { w -> into.add(ui.label("${w.icon} ${w.title}", "small", wrap = true)).growX().row() }; return }
        fortifications.build(into, zoneId, showHeader = false)
    }

    private fun battle(into: Table, b: fr.president.engine.readout.WarfareReadout.BattleView) {
        val box = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(6f, 8f, 6f, 8f); defaults().left() }
        val head = Table()
        head.add(ui.label("⚔ ${b.title}", "bold", Theme.tone(b.tone), wrap = true)).growX().left().minWidth(0f)
        b.ratio?.let { head.add(ui.label(String.format(java.util.Locale.FRENCH, "%.1f : 1", it), "value", Theme.tone(b.tone))).right() }
        box.add(head).growX().row()
        box.add(ui.label("${b.sides} · ${b.status}", "muted", wrap = true)).growX().row()
        box.add(ui.label(b.losses, "small", wrap = true)).growX().row()
        b.modifiers.forEach { box.add(ui.label(it, "muted", wrap = true)).growX().row() }
        into.add(box).growX().padBottom(6f).row()
    }

    private fun orders(into: Table, unitId: String) {
        val session = nav.session
        val unit = session.state.military.units.getValue(unitId)
        val domain = session.db.unitType(unit.type).domain
        if (domain == Domain.STRATEGIC) {
            into.add(ui.label("La force de dissuasion garantit que nul n'attaquera impunément le territoire national.", "muted", wrap = true)).growX().row()
            return
        }
        into.add(ui.label("Ordres", "bold")).padTop(6f).row()
        val targeted = when (domain) {
            Domain.LAND -> listOf(UnitOrder.MOVE to "Déplacer", UnitOrder.ATTACK to "Attaquer")
            Domain.AIR -> listOf(UnitOrder.SUPPORT to "Soutien aérien", UnitOrder.PATROL to "Patrouille", UnitOrder.MOVE to "Redéployer")
            Domain.SEA -> listOf(UnitOrder.MOVE to "Déplacer", UnitOrder.PATROL to "Patrouille", UnitOrder.SUPPORT to "Appui côtier")
            Domain.STRATEGIC -> emptyList()
        }
        val row = Table().apply { defaults().padRight(4f).padBottom(4f) }
        targeted.forEach { (order, label) ->
            row.add(ui.button("$label…", "accent") { nav.startTargeting(unitId, order) })
        }
        into.add(row).left().row()
        if (domain == Domain.LAND) {
            // Opérations spéciales : débarquement pour toutes, parachutage pour les parachutistes.
            val ops = Table().apply { defaults().padRight(4f).padBottom(4f) }
            ops.add(ui.colorButton("⚓ Débarquer…", Theme.catDiplomacy) { nav.startTargeting(unitId, UnitOrder.AMPHIBIOUS) })
            if (unit.type == "AIRBORNE_BRIGADE") ops.add(ui.colorButton("✈ Parachuter…", Theme.catArmy) { nav.startTargeting(unitId, UnitOrder.AIRBORNE) })
            into.add(ops).left().row()
            into.add(ui.label(if (unit.type == "AIRBORNE_BRIGADE") "Parachutage : jusqu'à 1 500 km, avec une escadre de transport. Débarquement : côte ennemie, escorte navale à moins de 600 km."
                else "Débarquement : vers une côte, avec une escorte navale à moins de 600 km de la plage. L'infanterie de marine y excelle.", "muted", wrap = true)).growX().row()
        }
        val immediate = Table().apply { defaults().padRight(4f).padBottom(4f) }
        val simple = if (domain == Domain.LAND) listOf(UnitOrder.DEFEND to "Défendre", UnitOrder.HOLD to "Tenir", UnitOrder.RETREAT to "Repli")
        else listOf(UnitOrder.HOLD to "Rester en position", UnitOrder.RETREAT to "Retour à la base")
        simple.forEach { (order, label) ->
            immediate.add(ui.button(label) {
                val r = session.military.order(unitId, order)
                if (r !is OrderService.Outcome.Refused) fr.president.game.ui.Sfx.play(fr.president.game.ui.Sfx.Kind.MARCH)
                message = if (r is OrderService.Outcome.Refused) r.reason else "Ordre transmis : ${order.label.lowercase()}."
                nav.refresh()
            })
        }
        into.add(immediate).left().row()
    }
}
