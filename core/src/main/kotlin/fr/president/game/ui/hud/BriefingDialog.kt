package fr.president.game.ui.hud

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import fr.president.engine.readout.BriefingReadout
import fr.president.game.ui.Formats
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui
import fr.president.game.ui.blockInput

/**
 * Bilan au retour : une fenêtre au centre de l'écran qui résume l'absence (chiffres avant → après,
 * faits marquants, décisions en attente, échéances). Un bouton pour se remettre au travail.
 */
class BriefingDialog(private val ui: Ui, private val onMessages: () -> Unit) {
    /** Calque plein écran (fond assombri) ; invisible tant qu'il n'y a rien à montrer. */
    val root: Table = Table().apply {
        setFillParent(true)
        setBackground(ui.skin.fill(Color(0f, 0f, 0f, BACKDROP)))
        isVisible = false
        blockInput()
    }

    fun show(b: BriefingReadout.Briefing) {
        root.clearChildren()
        val card = ui.panelTable().apply { pad(PAD); defaults().left().growX() }
        val days = if (b.days >= 1.5) "${Math.round(b.days)} jours" else "un jour"
        card.add(ui.label("Pendant votre absence : $days", "title", wrap = true)).row()
        card.add(ui.separator()).height(1f).padTop(4f).padBottom(6f).row()
        if (b.changes.isNotEmpty()) {
            val grid = Table().apply { defaults().left().padRight(10f).padBottom(2f); left() }
            b.changes.forEach { c ->
                val color = when (c.good) { true -> Theme.good; false -> Theme.bad; null -> Theme.text }
                grid.add(ui.label(c.label, "small", Theme.textMuted))
                grid.add(ui.label(c.before, "small"))
                grid.add(ui.label("→", "small", Theme.textMuted))
                grid.add(ui.label(c.after, "bold", color)).row()
            }
            card.add(grid).row()
        }
        if (b.facts.isNotEmpty()) {
            card.add(ui.label("Ce qui s'est passé", "bold")).padTop(6f).row()
            b.facts.forEach { f -> card.add(ui.label("• ${Formats.date(f.time)} — ${f.text}", "small", Theme.tone(f.tone).takeIf { f.tone != fr.president.engine.readout.Tone.NEUTRAL } ?: Theme.text, wrap = true)).row() }
        }
        card.add(ui.label("Ce qui vous attend", "bold")).padTop(6f).row()
        if (b.pendingDecisions > 0) card.add(ui.label("✉ ${b.pendingDecisions} décision(s) attendent votre réponse", "small", Theme.warning, wrap = true)).row()
        b.upcoming.forEach { card.add(ui.label("• $it", "small", wrap = true)).row() }
        val buttons = Table().apply { defaults().padRight(6f) }
        if (b.pendingDecisions > 0) buttons.add(ui.colorButton("✉ Voir les messages", Theme.catInbox) { hide(); onMessages() })
        buttons.add(ui.colorButton("▶ Au travail", Theme.accentDark) { hide() })
        card.add(buttons).left().padTop(PAD).row()
        val scroll = ScrollPane(card, ui.s).apply { setFadeScrollBars(false); setScrollingDisabled(true, false) }
        root.add(scroll).width(com.badlogic.gdx.scenes.scene2d.ui.Value.percentWidth(WIDTH_SHARE, root)).maxWidth(MAX_WIDTH)
            .maxHeight(com.badlogic.gdx.scenes.scene2d.ui.Value.percentHeight(HEIGHT_SHARE, root))
        root.isVisible = true
    }

    fun hide() {
        root.isVisible = false
    }

    private companion object {
        const val BACKDROP = 0.55f
        const val PAD = 14f
        const val WIDTH_SHARE = 0.9f
        const val MAX_WIDTH = 520f
        const val HEIGHT_SHARE = 0.85f
    }
}
