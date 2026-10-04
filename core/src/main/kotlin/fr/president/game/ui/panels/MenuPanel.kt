package fr.president.game.ui.panels

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui
import fr.president.game.ui.onClick

/**
 * Menu « ☰ Plus » : les écrans moins fréquents en grandes tuiles faciles à toucher, chacune
 * avec une phrase qui dit à quoi elle sert et une pastille quand quelque chose attend.
 */
class MenuPanel(ui: Ui, private val nav: Navigator, onClose: () -> Unit) : Panel(ui, onClose) {
    override val title = "☰ Tous les écrans"
    private val session get() = nav.session

    private data class Entry(val icon: String, val label: String, val text: String, val color: Color, val panel: PanelId, val badge: () -> Int = { 0 })

    private val entries = listOf(
        Entry("★", "Décider", "Plans, décrets, déplacements, décisions de crise.", Theme.highlightDark, PanelId.DECISIONS),
        Entry("▲", "Bilan", "Courbes, causes, groupes sociaux, pays, journal.", Theme.catStats, PanelId.STATS),
        Entry("⌂", "Gouvernement", "Ministres, Assemblée, Sénat, réformes.", Theme.catGovernment, PanelId.GOVERNMENT),
        Entry("€", "Économie", "Impôts, dépenses, services publics.", Theme.catEconomy, PanelId.ECONOMY),
        Entry("☎", "Diplomatie", "Accords, sanctions, ultimatums.", Theme.catDiplomacy, PanelId.DIPLOMACY),
        Entry("⚔", "Armées", "Unités, logistique, conflits, opérations.", Theme.catArmy, PanelId.ARMY),
        Entry("▤", "Presse", "Unes du jour, sondages, climat médiatique.", Theme.catPress, PanelId.PRESS),
        Entry("✔", "Élections", "Sondages, candidats, promesses.", Theme.catElections, PanelId.ELECTIONS),
        Entry("✉", "Messages", "Les décisions qui attendent votre réponse.", Theme.catInbox, PanelId.INBOX) { session.state.inbox.messages.count { it.awaitingAnswer } },
        Entry("⚑", "Alertes", "Toutes les notifications récentes.", Theme.catAlerts, PanelId.NOTIFICATIONS) { session.state.notifications.unreadCount },
        Entry("⚙", "Réglages", "Texte, couleurs, sons, rythme, notifications.", Theme.catSettings, PanelId.SETTINGS),
        Entry("?", "Aide", "Guide, glossaire, tutoriel.", Theme.catHelp, PanelId.HELP),
    )

    override fun build(into: Table) {
        val grid = Table().apply { defaults().growX().uniformX().pad(3f) }
        entries.forEachIndexed { i, e ->
            val tile = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(8f); defaults().left() }
            val head = Table()
            head.add(Table().apply { setBackground(ui.skin.fill(e.color)) }.also { it.add(ui.label(e.icon, "value", Color.WHITE)) }).size(ICON).padRight(8f)
            head.add(ui.label(e.label, "bold")).left().expandX()
            val n = e.badge()
            if (n > 0) head.add(ui.label("● $n", "small", Theme.warning)).right()
            tile.add(head).growX().row()
            tile.add(ui.label(e.text, "muted", wrap = true)).growX().padTop(3f).row()
            tile.onClick { nav.open(e.panel) }
            grid.add(tile).fill()
            if (i % 2 == 1) grid.row()
        }
        into.add(grid).growX().row()
    }

    private companion object {
        const val ICON = 30f
    }
}
