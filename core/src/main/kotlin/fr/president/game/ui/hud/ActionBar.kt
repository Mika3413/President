package fr.president.game.ui.hud

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import fr.president.engine.session.GameSession
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
        root.add(ui.button("Gouvernement") { open(PanelId.GOVERNMENT) })
        root.add(ui.button("Économie") { open(PanelId.ECONOMY) })
        root.add(ui.button("Diplomatie") { open(PanelId.DIPLOMACY) })
        root.add(ui.button("Armées") { open(PanelId.ARMY) })
        inbox = ui.button("Messages") { open(PanelId.INBOX) }
        root.add(inbox)
        alerts = ui.button("Alertes") { open(PanelId.NOTIFICATIONS) }
        root.add(alerts)
        root.add(ui.button("Élections") { open(PanelId.ELECTIONS) })
        root.add(ui.button("Réglages") { open(PanelId.SETTINGS) })
    }

    fun refresh() {
        val pending = session.state.inbox.messages.count { it.awaitingAnswer }
        inbox.setText(if (pending > 0) "Messages ($pending)" else "Messages")
        val unread = session.state.notifications.unreadCount
        alerts.setText(if (unread > 0) "Alertes ($unread)" else "Alertes")
    }
}
