package fr.president.game.ui.panels

import fr.president.engine.session.GameSession
import fr.president.game.map.MapSelection

/** Navigation entre panneaux et carte, fournie par l'écran principal. */
interface Navigator {
    val session: GameSession
    fun open(panel: PanelId, argument: String? = null)
    fun select(selection: MapSelection)
    fun focusOn(mapId: String)
    fun refresh()
}

enum class PanelId { SELECTION, GOVERNMENT, ECONOMY, DIPLOMACY, INBOX, NOTIFICATIONS, ELECTIONS, SETTINGS }
