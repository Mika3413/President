package fr.president.game.ui.hud

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import fr.president.engine.presidency.OnboardingService
import fr.president.engine.readout.Tone
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui
import fr.president.game.ui.blockInput

/**
 * Le premier matin à l'Élysée : une crue frappe un département et le préfet appelle. Le joueur
 * décide tout de suite (rien à lire avant), puis découvre ce que sa décision change.
 */
class FirstMorning(private val ui: Ui) {
    val root: Table = Table().apply {
        setFillParent(true)
        setBackground(ui.skin.fill(Color(0f, 0f, 0f, BACKDROP)))
        isVisible = false
        blockInput()
    }

    fun showMorning(m: OnboardingService.Morning, onDecide: () -> Unit) {
        root.clearChildren()
        val card = ui.panelTable().apply { pad(PAD); defaults().left().growX() }
        val band = Table().apply { setBackground(ui.skin.fill(Theme.bad)); pad(3f, 8f, 3f, 8f) }
        band.add(ui.label("⚠ URGENT · Appel du préfet", "bold", Color.WHITE)).left()
        card.add(band).left().fill(false, false).padBottom(8f).row()
        card.add(ui.label(m.title, "headline", Theme.highlight, wrap = true)).row()
        card.add(ui.label(m.text, "default", wrap = true)).padTop(6f).row()
        card.add(ui.label("Ici, pas de cours : vous apprenez en décidant. Chaque choix a un prix et des effets que vous verrez juste après.", "muted", wrap = true)).padTop(8f).row()
        card.add(ui.colorButton("▶ Voir la carte et décider", Theme.accentDark) { hide(); onDecide() }).left().padTop(PAD).row()
        root.add(card).width(com.badlogic.gdx.scenes.scene2d.ui.Value.percentWidth(WIDTH_SHARE, root)).maxWidth(MAX_WIDTH)
        root.isVisible = true
    }

    fun showOutcome(o: OnboardingService.Outcome, onContinue: () -> Unit) {
        root.clearChildren()
        val card = ui.panelTable().apply { pad(PAD); defaults().left().growX() }
        card.add(ui.label("Votre première décision", "title", wrap = true)).row()
        card.add(ui.label("« ${o.choice} »", "bold", Theme.highlight, wrap = true)).padTop(4f).row()
        card.add(ui.label("Ce qui va se passer", "bold")).padTop(10f).row()
        o.lines.forEach { l -> card.add(ui.label("${icon(l.tone)} ${l.text}", "default", Theme.tone(l.tone), wrap = true)).padTop(2f).row() }
        val press = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(8f) }
        press.add(ui.label("▤ La presse de demain", "small", Theme.textMuted)).left().row()
        press.add(ui.label(o.headline, "title", Theme.tone(o.tone), wrap = true)).growX().left().row()
        card.add(press).padTop(10f).row()
        card.add(ui.label("C'est tout le jeu : on vous écrit, vous tranchez, le pays réagit. La carte montre la popularité de chaque département, les chiffres en haut suivent le pays entier.", "muted", wrap = true)).padTop(10f).row()
        card.add(ui.colorButton("▶ Continuer", Theme.accentDark) { hide(); onContinue() }).left().padTop(PAD).row()
        root.add(card).width(com.badlogic.gdx.scenes.scene2d.ui.Value.percentWidth(WIDTH_SHARE, root)).maxWidth(MAX_WIDTH)
        root.isVisible = true
    }

    private fun icon(t: Tone) = when (t) { Tone.GOOD -> "▲"; Tone.BAD -> "▼"; Tone.WARNING -> "€"; Tone.NEUTRAL -> "•" }

    fun hide() { root.isVisible = false }

    private companion object {
        const val BACKDROP = 0.6f
        const val PAD = 14f
        const val WIDTH_SHARE = 0.92f
        const val MAX_WIDTH = 520f
    }
}
