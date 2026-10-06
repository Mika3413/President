package fr.president.game.ui.hud

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import fr.president.engine.session.GameSession
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui
import fr.president.game.ui.hint
import fr.president.game.ui.panels.PanelId

/**
 * Barre d'actions, comme dans Supremacy : peu de boutons, les plus utiles. Le reste est dans
 * « ☰ Plus », en grandes tuiles. Les pastilles signalent ce qui attend une réponse.
 */
class ActionBar(ui: Ui, private val session: GameSession, open: (PanelId) -> Unit) {
    val root: Table = ui.panelTable()
    private val inbox: TextButton
    private val more: TextButton
    private val buttons = mutableListOf<Pair<TextButton, String>>()
    private var compact = false

    init {
        root.pad(4f)
        root.defaults().padRight(4f).minHeight(BUTTON_HEIGHT)
        root.add(ui.colorButton("★ Décider", Theme.highlightDark) { open(PanelId.DECISIONS) }.also { it.name = "bar.decide" }
            .hint(ui, "Vos décisions nationales : plans, décrets, déplacements, annonces."))
        root.add(ui.colorButton("▲ Bilan", Theme.catStats) { open(PanelId.STATS) }.also { it.name = "bar.stats" }
            .hint(ui, "Courbes du mandat, causes de vos chiffres, groupes sociaux, classement des pays, journal."))
        inbox = ui.colorButton(INBOX, Theme.catInbox) { open(PanelId.INBOX) }
        inbox.name = "bar.inbox"
        root.add(inbox.hint(ui, "Les décisions qui attendent votre réponse."))
        root.add(ui.colorButton("☎ Diplomatie", Theme.catDiplomacy) { open(PanelId.DIPLOMACY) }.hint(ui, "Négocier avec les 29 pays simulés : accords, sanctions, ultimatums."))
        root.add(ui.colorButton("⚔ Armées", Theme.catArmy) { open(PanelId.ARMY) }.hint(ui, "Unités, ordres, opérations et guerres en cours."))
        more = ui.colorButton(MORE, Theme.catSettings) { open(PanelId.MENU) }
        more.name = "bar.more"
        root.add(more.hint(ui, "Gouvernement, économie, presse, élections, alertes, réglages, aide."))
        root.cells.forEach { c -> (c.actor as? TextButton)?.let { buttons += it to it.text.toString() } }
    }

    /** Écran étroit : l'icône au-dessus d'un mot court, pour que les six boutons tiennent. */
    fun setCompact(value: Boolean) {
        if (value == compact) return
        compact = value
        buttons.forEach { (b, full) -> b.setText(label(full)) }
        refresh()
    }

    private fun label(full: String): String {
        if (!compact) return full
        val icon = full.substringBefore(' ')
        val word = SHORT[full.substringAfter(' ')] ?: full.substringAfter(' ')
        return "$icon\n$word"
    }

    fun refresh() {
        val pending = session.state.inbox.messages.count { it.awaitingAnswer }
        inbox.setText(label(INBOX) + if (pending > 0) " ● $pending" else "")
        val unread = session.state.notifications.unreadCount
        more.setText(label(MORE) + if (unread > 0) " ● ${if (unread > 99) "99+" else unread}" else "")
    }

    private companion object {
        const val INBOX = "✉ Messages"
        const val MORE = "☰ Plus"
        const val BUTTON_HEIGHT = 38f
        val SHORT = mapOf("Diplomatie" to "Diplo", "Messages" to "Messages")
    }
}
