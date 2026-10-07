package fr.president.game.ui.widgets

import com.badlogic.gdx.scenes.scene2d.ui.Table
import fr.president.engine.util.Formatting
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui
import fr.president.game.ui.onClick
import fr.president.game.ui.panels.Navigator

/**
 * Défense d'une zone : terrain, ouvrages déjà bâtis (état, chantier en cours) et ce que l'on
 * peut y construire, avec le coût, le délai et l'effet. Sert dans la fiche d'un département
 * et dans celle d'une unité (pour fortifier une zone conquise).
 */
class FortificationView(private val ui: Ui, private val nav: Navigator, private val onMessage: (String) -> Unit) {
    private val session get() = nav.session

    fun build(into: Table, zoneId: String, showHeader: Boolean = true) {
        val v = session.warfare.zone(zoneId)
        if (showHeader) {
            val head = Table()
            head.add(ui.label(v.place, "bold")).left().growX()
            head.add(ui.label(v.terrain, "small", Theme.accent)).right()
            into.add(head).growX().row()
            if (v.terrainHint.isNotEmpty()) into.add(ui.label(v.terrainHint, "muted", wrap = true)).growX().padBottom(4f).row()
        }
        if (v.works.isEmpty()) into.add(ui.label("Aucun ouvrage militaire dans cette zone.", "muted", wrap = true)).growX().padBottom(4f).row()
        v.works.forEach { w ->
            val row = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(4f, 8f, 4f, 8f); defaults().left() }
            row.add(ui.label(w.icon, "value", Theme.tone(w.tone))).width(ICON).left()
            val text = Table()
            text.add(ui.label(w.title, "small", wrap = true)).growX().left().row()
            text.add(ui.label(w.detail, "muted", wrap = true)).growX().left()
            row.add(text).growX().minWidth(0f)
            into.add(row).growX().padBottom(3f).row()
        }
        val player = session.state.player.countryId
        if (session.military.geo.controllerOf(zoneId) != player) {
            into.add(ui.label("Zone tenue par ${v.controller} : seules nos troupes peuvent y bâtir une fois la zone conquise.", "muted", wrap = true)).growX().padTop(4f).row()
            return
        }
        into.add(ui.label("Construire", "bold", Theme.catArmy)).left().padTop(6f).row()
        v.options.forEach { o ->
            val card = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(5f, 8f, 5f, 8f); defaults().left() }
            val head = Table()
            head.add(ui.label(o.def.icon, "value", if (o.blocker == null) Theme.catArmy else Theme.textMuted)).width(ICON).left()
            head.add(ui.label((if (o.upgrade) "Améliorer : " else "") + o.level.label, "small", if (o.blocker == null) Theme.text else Theme.textMuted, wrap = true)).growX().left().minWidth(0f)
            val cost = Table()
            cost.add(ui.label(Formatting.billions(o.level.costBillions), "small", Theme.warning)).right().row()
            cost.add(ui.label("${o.days} j", "muted")).right()
            head.add(cost).right().padLeft(6f)
            card.add(head).growX().row()
            card.add(ui.label(o.def.label + " — " + o.effects, "muted", wrap = true)).growX().row()
            if (o.blocker == null) {
                card.add(ui.colorButton("▶ Lancer le chantier", Theme.catArmy) {
                    onMessage(session.military.fortifications.build(o.def.id, zoneId).fold({ it }, { it.message ?: "Impossible." }))
                }).left().padTop(3f).row()
            } else card.add(ui.label("↻ ${o.blocker}", "small", Theme.warning, wrap = true)).growX().padTop(2f).row()
            into.add(card).growX().padBottom(3f).row()
        }
    }

    /** Liste compacte de nos ouvrages (panneau Armées) : toucher une ligne centre la carte. */
    fun ownList(into: Table) {
        val works = session.warfare.ownWorks()
        if (works.isEmpty()) {
            into.add(ui.label("Aucun ouvrage. Fortifiez une zone depuis la fiche d'un département (onglet « Défense ») ou d'une unité.", "muted", wrap = true)).growX().row()
            return
        }
        works.forEach { (place, w) ->
            val row = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(4f, 8f, 4f, 8f); defaults().left() }
            row.add(ui.label(w.icon, "value", Theme.tone(w.tone))).width(ICON).left()
            val text = Table()
            text.add(ui.label("${w.title} — $place", "small", wrap = true)).growX().left().row()
            text.add(ui.label(w.detail, "muted", wrap = true)).growX().left()
            row.add(text).growX().minWidth(0f)
            row.onClick { nav.focusOn(w.work.zoneId) }
            into.add(row).growX().padBottom(3f).row()
        }
    }

    private companion object {
        const val ICON = 26f
    }
}
