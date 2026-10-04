package fr.president.game.ui.hud

import com.badlogic.gdx.scenes.scene2d.ui.Table
import fr.president.engine.readout.AdvisorReadout
import fr.president.engine.session.GameSession
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui
import fr.president.game.ui.onClick
import fr.president.game.ui.panels.Navigator
import fr.president.game.ui.panels.PanelId

/** Encart « À faire » : les conseils du moment, chacun mène directement au bon écran. */
class AdvisorCard(private val ui: Ui, private val session: GameSession, private val nav: Navigator) {
    val root: Table = ui.panelTable()
    private var open = true
    /** Écran étroit : l'encart est replié par défaut pour laisser la carte visible. */
    var compact = false
        set(v) { if (field != v) { field = v; open = !v; lastKey = "" } }
    private var lastKey = ""

    fun refresh() {
        val advices = session.advisor.advices()
        val key = open.toString() + advices.joinToString("|") { it.text }
        if (key == lastKey) return
        lastKey = key
        root.clearChildren()
        root.pad(6f)
        val head = Table()
        head.add(ui.label("★ À faire", "bold", Theme.highlight)).left().expandX()
        head.add(ui.label(if (open) "▲" else "▼", "small", Theme.textMuted)).right()
        head.onClick { open = !open; lastKey = ""; refresh() }
        root.add(head).growX().row()
        if (!open) return
        advices.forEach { a ->
            val row = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(4f, 6f, 4f, 6f) }
            row.add(ui.label(a.icon, "bold", Theme.tone(a.tone))).top().padRight(6f)
            row.add(ui.label(a.text, "small", wrap = true)).width(if (compact) COMPACT_WIDTH else TEXT_WIDTH).left()
            row.onClick { go(a) }
            root.add(row).growX().padTop(3f).row()
        }
    }

    private fun go(a: AdvisorReadout.Advice) = when (a.target) {
        AdvisorReadout.Target.INBOX -> nav.open(PanelId.INBOX)
        AdvisorReadout.Target.DEPARTMENT -> a.targetId?.let { nav.focusOn(it) } ?: Unit
        AdvisorReadout.Target.COUNTRY -> a.targetId?.let { nav.focusOn(it) } ?: Unit
        AdvisorReadout.Target.ECONOMY -> nav.open(PanelId.ECONOMY)
        AdvisorReadout.Target.ELECTIONS -> nav.open(PanelId.ELECTIONS)
        AdvisorReadout.Target.GOVERNMENT -> nav.open(PanelId.GOVERNMENT)
        AdvisorReadout.Target.DECISIONS -> nav.open(PanelId.DECISIONS, a.targetId)
        AdvisorReadout.Target.CRISIS -> nav.open(PanelId.CRISIS, a.targetId ?: "active")
    }

    private companion object {
        const val TEXT_WIDTH = 230f
        const val COMPACT_WIDTH = 170f
    }
}
