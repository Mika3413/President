package fr.president.game.ui.hud

import com.badlogic.gdx.scenes.scene2d.ui.Table
import fr.president.engine.military.OrderPreview
import fr.president.engine.military.OrderService
import fr.president.engine.session.GameSession
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui
import fr.president.game.ui.blockInput

/**
 * Ordres directs sur la carte, comme dans Supremacy : une unité est sélectionnée, le joueur
 * touche une destination, et une bulle propose les ordres possibles avec la durée du trajet
 * et le rapport de forces. Les ordres impossibles sont grisés avec leur raison.
 */
class QuickOrders(private val ui: Ui, private val session: GameSession, private val onResult: (unitId: String, message: String) -> Unit, private val onInspect: () -> Unit) {
    val root: Table = ui.panelTable().apply { isVisible = false; blockInput() }

    fun show(unitId: String, zoneId: String, x: Float, y: Float, stageWidth: Float, stageHeight: Float) {
        val unit = session.state.military.units[unitId] ?: return
        val preview = session.military.preview.preview(unitId, zoneId) ?: return
        root.clearChildren()
        root.pad(8f, 10f, 8f, 10f)
        root.defaults().left()
        root.add(ui.label(unit.name, "bold")).row()
        root.add(ui.label("Vers : ${preview.place}", "small", if (preview.hostile) Theme.bad else Theme.textMuted)).padBottom(4f).row()
        preview.options.forEach { o -> root.add(optionRow(unitId, zoneId, o)).growX().padBottom(3f).row() }
        val foot = Table().apply { defaults().padRight(4f) }
        foot.add(ui.button("Voir la fiche", "flat") { hide(); onInspect() })
        foot.add(ui.button("✕ Annuler", "flat") { hide() })
        root.add(foot).padTop(2f).row()
        root.pack()
        // Posée près du doigt, mais toujours entière à l'écran.
        root.setPosition((x + OFFSET).coerceAtMost(stageWidth - root.width - MARGIN).coerceAtLeast(MARGIN),
            (y - root.height / 2).coerceIn(MARGIN, (stageHeight - root.height - MARGIN).coerceAtLeast(MARGIN)))
        root.isVisible = true
        root.toFront()
    }

    private fun optionRow(unitId: String, zoneId: String, o: OrderPreview.Option): Table {
        val row = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(4f, 6f, 4f, 6f); defaults().left() }
        if (o.available) {
            val head = Table()
            head.add(ui.colorButton(o.label, if (o.label.startsWith("⚔")) Theme.catArmy else Theme.accentDark) {
                hide()
                val r = session.military.order(unitId, o.order, zoneId)
                if (r !is OrderService.Outcome.Refused) fr.president.game.ui.Sfx.play(fr.president.game.ui.Sfx.Kind.MARCH)
                onResult(unitId, if (r is OrderService.Outcome.Refused) r.reason else "Ordre transmis : ${o.label.drop(2).lowercase()}.")
            }).left()
            o.etaHours?.let { head.add(ui.label("  ◷ ${eta(it)}", "small")).left() }
            row.add(head).left().row()
            o.odds?.let {
                row.add(ui.label("Rapport de forces : ${it.label}", "small", Theme.tone(it.tone))).left().padTop(2f).row()
                // Terrain, fortifications, fleuve, forces et faiblesses : pourquoi ce rapport.
                it.notes.take(MAX_NOTES).forEach { n -> row.add(ui.label(n, "muted", wrap = true)).width(WIDTH).left().row() }
            }
        } else {
            row.add(ui.label(o.label, "small", Theme.textMuted)).left().row()
            o.reason?.let { row.add(ui.label(it, "muted", wrap = true)).width(WIDTH).left().row() }
        }
        return row
    }

    fun hide() {
        root.isVisible = false
    }

    private fun eta(hours: Double): String {
        val h = Math.round(hours).toInt().coerceAtLeast(1)
        return if (h < HOURS_PER_DAY) "$h h" else "${h / HOURS_PER_DAY} j ${h % HOURS_PER_DAY} h"
    }

    private companion object {
        const val WIDTH = 230f
        const val MAX_NOTES = 4
        const val OFFSET = 16f
        const val MARGIN = 6f
        const val HOURS_PER_DAY = 24
    }
}
