package fr.president.game.ui.hud

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import fr.president.engine.session.GameSession
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui
import fr.president.game.ui.panels.PanelId

/** Barre d'actions principale : accès aux grands domaines de la présidence. */
class ActionBar(ui: Ui, private val session: GameSession, open: (PanelId) -> Unit) {
    val root: Table = ui.panelTable()
    private val inbox: TextButton
    private val alerts: TextButton

    init {
        root.pad(4f)
        root.defaults().padRight(4f)
        root.add(ui.colorButton("★ Décider", Theme.highlightDark) { open(PanelId.DECISIONS) })
        root.add(ui.colorButton("⌂ Gouvernement", Theme.catGovernment) { open(PanelId.GOVERNMENT) })
        root.add(ui.colorButton("€ Économie", Theme.catEconomy) { open(PanelId.ECONOMY) })
        root.add(ui.colorButton("☎ Diplomatie", Theme.catDiplomacy) { open(PanelId.DIPLOMACY) })
        root.add(ui.colorButton("⚔ Armées", Theme.catArmy) { open(PanelId.ARMY) })
        inbox = ui.colorButton(INBOX, Theme.catInbox) { open(PanelId.INBOX) }
        root.add(inbox)
        alerts = ui.colorButton(ALERTS, Theme.catAlerts) { open(PanelId.NOTIFICATIONS) }
        root.add(alerts)
        root.add(ui.colorButton("✔ Élections", Theme.catElections) { open(PanelId.ELECTIONS) })
        root.add(ui.colorButton("⚙", Theme.catSettings) { open(PanelId.SETTINGS) })
        root.add(ui.colorButton("?", Theme.catHelp) { open(PanelId.HELP) })
    }

    fun refresh() {
        val pending = session.state.inbox.messages.count { it.awaitingAnswer }
        inbox.setText(if (pending > 0) "$INBOX ● $pending" else INBOX)
        val unread = session.state.notifications.unreadCount
        alerts.setText(if (unread > 0) "$ALERTS ● $unread" else ALERTS)
    }

    private companion object {
        const val INBOX = "✉ Messages"
        const val ALERTS = "⚑ Alertes"
    }
}
