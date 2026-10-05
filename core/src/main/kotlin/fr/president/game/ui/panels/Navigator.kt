package fr.president.game.ui.panels

import fr.president.engine.session.GameSession
import fr.president.game.map.MapSelection

/** Navigation entre panneaux et carte, fournie par l'écran principal. */
interface Navigator {
    val session: GameSession
    val platform: fr.president.game.platform.PlatformServices
    fun open(panel: PanelId, argument: String? = null)
    fun select(selection: MapSelection)
    fun focusOn(mapId: String)
    fun refresh()
    /** Attend que le joueur touche la carte pour désigner la zone cible d'un ordre. */
    fun startTargeting(unitId: String, order: fr.president.engine.military.UnitOrder)
    fun prepareProposal(country: String, clauseType: String, params: Map<String, Double>)
    /** Taille du texte ou palette changée : reconstruire l'interface. */
    fun applyDisplaySettings() {}
}

enum class PanelId { SELECTION, MENU, DECISIONS, STATS, PRESS, GOVERNMENT, ECONOMY, DIPLOMACY, ARMY, CRISIS, EU, AGENDA, INBOX, NOTIFICATIONS, ELECTIONS, SETTINGS, HELP }
