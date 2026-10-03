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
        session.militaryReadouts.unit(unit).forEach { into.add(IndicatorView(ui, it, expanded)).growX().padBottom(6f).row() }
        if (own && !unit.destroyed) orders(into, unitId)
        val others = session.state.military.units.values.filter { it.zoneId == unit.zoneId && it.id != unit.id && !it.destroyed }
            .filter { it.countryId == unit.countryId || session.military.visibleUnits().contains(it) }
        if (others.isNotEmpty()) {
            into.add(ui.label("Autres unités dans la zone", "bold")).padTop(6f).row()
            others.forEach { o -> into.add(ui.button(o.name, "flat") { nav.select(MapSelection.Unit(o.id)) }).left().row() }
        }
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
        val immediate = Table().apply { defaults().padRight(4f).padBottom(4f) }
        val simple = if (domain == Domain.LAND) listOf(UnitOrder.DEFEND to "Défendre", UnitOrder.HOLD to "Tenir", UnitOrder.RETREAT to "Repli")
        else listOf(UnitOrder.HOLD to "Rester en position", UnitOrder.RETREAT to "Retour à la base")
        simple.forEach { (order, label) ->
            immediate.add(ui.button(label) {
                val r = session.military.order(unitId, order)
                message = if (r is OrderService.Outcome.Refused) r.reason else "Ordre transmis : ${order.label.lowercase()}."
                nav.refresh()
            })
        }
        into.add(immediate).left().row()
    }
}
