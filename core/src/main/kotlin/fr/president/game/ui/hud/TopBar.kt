package fr.president.game.ui.hud

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import fr.president.engine.readout.Indicator
import fr.president.engine.session.GameSession
import fr.president.game.ui.Formats
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui
import fr.president.game.ui.onClick
import fr.president.game.ui.panels.PanelId

/** Barre supérieure : date du monde, rythme, et indicateurs clés cliquables. */
class TopBar(private val ui: Ui, private val session: GameSession, private val open: (PanelId) -> Unit) {
    val root: Table = ui.panelTable()
    private val date = ui.label("", "bold")
    private val pace = ui.label("", "muted")
    private val chips = Table()

    init {
        root.pad(6f, 10f, 6f, 10f)
        val left = Table()
        left.add(date).left().row()
        left.add(pace).left()
        root.add(left).left()
        root.add(chips).expandX().right()
    }

    fun refresh() {
        date.setText(Formats.dateTime(session.state.time))
        val paceDef = session.db.config.paces.firstOrNull { it.id == session.state.meta.clock.paceId }
        pace.setText("Rythme ${paceDef?.label?.lowercase()} · ${session.db.country(session.state.player.countryId).definition.name}")
        chips.clearChildren()
        val national = session.national
        chip(national.approval(), PanelId.ECONOMY)
        chip(national.unemployment(), PanelId.ECONOMY)
        chip(national.debt(), PanelId.ECONOMY)
        val days = session.state.time.daysUntil(session.state.elections.nextElection).toInt()
        val election = ui.label("Élection J-$days", "small", if (days < WARNING_DAYS) Theme.warning else Theme.text)
        val box = Table().apply { pad(4f, 8f, 4f, 8f); setBackground(ui.skin.fill(Theme.panelAlt)) }
        box.add(election)
        box.onClick { open(PanelId.ELECTIONS) }
        chips.add(box).padLeft(6f)
    }

    private fun chip(indicator: Indicator, panel: PanelId) {
        val box = Table().apply { pad(4f, 8f, 4f, 8f); setBackground(ui.skin.fill(Theme.panelAlt)) }
        box.add(ui.label(indicator.label, "muted")).row()
        box.add(Label(indicator.status, ui.s, "small").apply { color = Theme.tone(indicator.tone) })
        box.onClick { open(panel) }
        chips.add(box).padLeft(6f)
    }

    private companion object {
        const val WARNING_DAYS = 120
    }
}
