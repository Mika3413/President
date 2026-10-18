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
class ActionBar(private val ui: Ui, private val session: GameSession, open: (PanelId) -> Unit) {
    val root: Table = ui.panelTable()
    private class Item(val button: TextButton, val icon: String, val word: String, val short: String) { var badge = "" }
    private val items = mutableListOf<Item>()
    private var compact = false

    init {
        root.pad(4f)
        root.defaults().padRight(4f).minHeight(BUTTON_HEIGHT)
        add("decide", "Décider", "Décider", Theme.highlightDark, "bar.decide", "Vos décisions nationales : plans, décrets, déplacements, annonces.") { open(PanelId.DECISIONS) }
        add("stats", "Bilan", "Bilan", Theme.catStats, "bar.stats", "Courbes du mandat, causes de vos chiffres, groupes sociaux, classement des pays, journal.") { open(PanelId.STATS) }
        add("inbox", "Messages", "Messages", Theme.catInbox, "bar.inbox", "Les décisions qui attendent votre réponse.") { open(PanelId.INBOX) }
        add("diplomacy", "Diplomatie", "Diplo", Theme.catDiplomacy, "bar.diplomacy", "Négocier avec les pays simulés : accords, sanctions, ultimatums.") { open(PanelId.DIPLOMACY) }
        add("army", "Armées", "Armées", Theme.catArmy, "bar.army", "Unités, ordres, opérations et guerres en cours.") { open(PanelId.ARMY) }
        add("more", "Plus", "Plus", Theme.catSettings, "bar.more", "Gouvernement, économie, presse, élections, alertes, réglages, aide.") { open(PanelId.MENU) }
        items.forEach { layout(it) }
    }

    private fun add(icon: String, word: String, short: String, color: com.badlogic.gdx.graphics.Color, name: String, hint: String, action: () -> Unit) {
        val b = ui.colorButton(word, color, action)
        b.name = name
        val item = Item(b, icon, word, short)
        items += item
        root.add(b.hint(ui, hint))
    }

    /** Icône dessinée à gauche du mot ; sur écran étroit, au-dessus d'un mot court. */
    private fun layout(item: Item) {
        val b = item.button
        val label = b.label
        label.setText(ui.localize(if (compact) item.short else item.word) + item.badge)
        b.clearChildren()
        val image = fr.president.game.ui.Icons.region(item.icon)?.let { com.badlogic.gdx.scenes.scene2d.ui.Image(it) }
        if (image != null) {
            if (compact) { b.add(image).size(ICON_COMPACT).row(); b.add(label) }
            else { b.add(image).size(ICON).padRight(5f); b.add(label) }
        } else b.add(label)
    }

    /** Écran étroit : l'icône au-dessus d'un mot court, pour que les six boutons tiennent. */
    fun setCompact(value: Boolean) {
        if (value == compact) return
        compact = value
        items.forEach { layout(it) }
        refresh()
    }

    fun refresh() {
        val pending = session.state.inbox.messages.count { it.awaitingAnswer }
        val unread = session.state.notifications.unreadCount + session.state.player.newScreens.size
        items.forEach { item ->
            val badge = when (item.icon) {
                "inbox" -> if (pending > 0) " ● $pending" else ""
                "more" -> if (unread > 0) " ● ${if (unread > 99) "99+" else unread}" else ""
                else -> ""
            }
            if (badge != item.badge) { item.badge = badge; item.button.label.setText(ui.localize(if (compact) item.short else item.word) + badge) }
        }
    }

    private companion object {
        const val BUTTON_HEIGHT = 38f
        const val ICON = 18f
        const val ICON_COMPACT = 16f
    }
}
