package fr.president.game.ui.panels

import com.badlogic.gdx.scenes.scene2d.ui.Table
import fr.president.engine.notifications.Urgency
import fr.president.game.ui.Formats
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui
import fr.president.game.ui.onClick

/** Fil des alertes et de l'actualité du pays. */
class NotificationsPanel(ui: Ui, private val nav: Navigator, onClose: () -> Unit) : Panel(ui, onClose) {
    override val title = "Alertes et actualité"
    private val session get() = nav.session
    private var showNews = false

    override fun build(into: Table) {
        session.state.notifications.unreadCount = 0
        val tabs = Table().apply { defaults().padRight(4f) }
        tabs.add(ui.button("Alertes", "toggle") { showNews = false; nav.refresh() }.also { it.isChecked = !showNews })
        tabs.add(ui.button("Actualité", "toggle") { showNews = true; nav.refresh() }.also { it.isChecked = showNews })
        into.add(tabs).left().padBottom(GAP).row()
        if (showNews) {
            session.state.events.news.asReversed().take(MAX).forEach { n ->
                val row = Table().apply { defaults().left() }
                row.add(ui.label(n.headline, "small", wrap = true)).growX().row()
                row.add(ui.label("${n.category.label} · ${Formats.date(n.time)}", "muted")).row()
                n.focusId?.let { f -> row.onClick { nav.focusOn(f) } }
                into.add(row).growX().padBottom(6f).row()
            }
            return
        }
        session.state.notifications.feed.asReversed().take(MAX).forEach { n ->
            val color = when (n.urgency) { Urgency.URGENT -> Theme.bad; Urgency.IMPORTANT -> Theme.warning; Urgency.INFO -> Theme.textMuted }
            val row = Table().apply { defaults().left(); pad(4f); setBackground(ui.skin.fill(Theme.panelAlt)) }
            row.add(ui.label(n.title, "bold", color, wrap = true)).growX().row()
            if (n.body.isNotBlank()) row.add(ui.label(n.body, "small", wrap = true)).growX().row()
            row.add(ui.label("${n.category.label} · ${Formats.dateTime(n.time)}", "muted")).row()
            n.focusId?.let { f -> row.onClick { nav.focusOn(f) } }
            into.add(row).growX().padBottom(4f).row()
        }
    }

    private companion object {
        const val MAX = 80
    }
}
