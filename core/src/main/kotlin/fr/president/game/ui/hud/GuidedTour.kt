package fr.president.game.ui.hud

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import fr.president.engine.session.GameSession
import fr.president.game.map.MapSelection
import fr.president.game.map.ThematicLayer
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui
import fr.president.game.ui.panels.PanelId

/** Ce que le tutoriel observe de l'écran principal pour savoir si une étape est réussie. */
interface TourHost {
    val openPanel: PanelId?
    val selection: MapSelection?
    val layer: ThematicLayer
}

/**
 * Tutoriel guidé, comme dans Supremacy : une consigne à la fois, l'élément à toucher entouré
 * d'un cadre lumineux, l'étape validée dès que le joueur l'a faite. On peut passer une étape
 * ou quitter le tutoriel à tout moment ; la progression est sauvegardée avec la partie.
 */
class GuidedTour(private val ui: Ui, private val session: GameSession, private val host: TourHost) {
    private data class Step(val title: String, val text: String, val target: String?, val journalKind: String? = null, val done: (GuidedTour) -> Boolean)

    /** Consigne affichée en haut de l'écran. */
    val card: Table = ui.panelTable()
    /** Cadre lumineux dessiné par-dessus l'interface, autour de l'élément à toucher. */
    val highlight: Actor = Highlight()
    private var shownStep = Int.MIN_VALUE
    private var journalAtStart = 0

    private val steps = listOf(
        Step("1/6 · Vos chiffres", "En haut, l'état du pays. ${Theme.goodName.replaceFirstChar { it.uppercase() }} = bien, orange = à surveiller, ${Theme.badName} = danger. Touchez votre popularité : sa courbe montre déjà l'effet de votre première décision.",
            "chip.approval") { host.openPanel == PanelId.STATS },
        Step("2/6 · Décider pour tout le pays", "Touchez « ★ Décider » en bas, choisissez une décision, regardez sa prévision (${Theme.goodName} = gain, ${Theme.badName} = perte) et lancez-la.",
            "bar.decide", "Décision") { it.newJournal("Décision") },
        Step("3/6 · Agir sur le terrain", "Touchez un département sur la carte, ouvrez « ▶ Agir » et lancez une action locale : hôpital, usine, police...",
            null, "Territoire") { it.newJournal("Territoire") },
        Step("4/6 · Lire la carte", "Touchez « ☰ Carte » en haut à gauche et choisissez « Chômage » : chaque département montre son chiffre.",
            "layers") { host.layer == ThematicLayer.UNEMPLOYMENT },
        Step("5/6 · Le monde", "Dézoomez et touchez un pays étranger : sa couleur dit votre relation. Coopérez ou faites pression.",
            null) { host.selection is MapSelection.Country },
        Step("6/6 · Lois et budget", "Impôts et dépenses entrent dans le budget voté chaque automne ; une loi réunit plusieurs changements ; un décret s'applique tout de suite. Touchez « ☰ Plus » puis « Lois et budget ».",
            "bar.more") { host.openPanel == PanelId.LEGISLATION },
    )

    private fun newJournal(kind: String) = session.state.stats.journal.count { it.kind == kind } > journalAtStart

    private val step: Int get() = session.state.player.tourStep

    /** À appeler à chaque image : valide l'étape en cours et met à jour la consigne. */
    fun update() {
        val i = step
        if (i < 0 || i > steps.size) { hide(); return }
        // Le premier matin passe avant le tutoriel.
        val p = session.state.player
        if (p.firstCrisis == null && p.progressiveScreens || p.firstCrisis != null && !p.firstCrisisShown) { if (card.isVisible) hide(); return }
        if (i < steps.size && steps[i].done(this)) { advance(); return }
        if (i != shownStep) build(i)
    }

    private fun advance() {
        session.state.player.tourStep = step + 1
    }

    private fun build(i: Int) {
        shownStep = i
        val s = steps.getOrNull(i)
        s?.target.let { (highlight as Highlight).target = it }
        s?.journalKind?.let { kind -> journalAtStart = session.state.stats.journal.count { it.kind == kind } }
        card.clearChildren()
        card.pad(8f, 12f, 8f, 12f)
        card.isVisible = true
        if (s == null) {
            card.add(ui.label("★ Bravo, Président !", "bold", Theme.highlight)).left().row()
            card.add(ui.label("Vous savez l'essentiel. D'autres écrans s'ouvriront au fil du mandat, quand ils deviendront utiles. « ? » répond à vos questions. Bonne présidence !", "small", wrap = true)).width(TEXT_WIDTH).left().row()
            card.add(ui.colorButton("Terminer", Theme.accentDark) { session.state.player.tourStep = -1; hide() }).left().padTop(4f)
            return
        }
        card.add(ui.label("★ ${s.title}", "bold", Theme.highlight)).left().row()
        card.add(ui.label(s.text, "small", wrap = true)).width(TEXT_WIDTH).left().row()
        val buttons = Table().apply { defaults().padRight(4f) }
        buttons.add(ui.button("Passer cette étape", "flat") { advance() })
        buttons.add(ui.button("Quitter le tutoriel", "flat") { session.state.player.tourStep = -1; hide() })
        card.add(buttons).left().padTop(2f)
    }

    private fun hide() {
        card.isVisible = false
        (highlight as Highlight).target = null
        shownStep = Int.MIN_VALUE
    }

    /** Cadre pulsant autour de l'acteur nommé [target], retrouvé à chaque image. */
    private inner class Highlight : Actor() {
        var target: String? = null
        private var time = 0f
        private val pos = Vector2()
        private val c = Color()

        init {
            touchable = Touchable.disabled
        }

        override fun act(delta: Float) {
            super.act(delta)
            time += delta
        }

        override fun draw(batch: Batch, parentAlpha: Float) {
            val name = target ?: return
            val actor = stage?.root?.findActor<Actor>(name) ?: return
            if (!actor.isVisible || actor.stage == null) return
            actor.localToStageCoordinates(pos.set(0f, 0f))
            val pulse = (MathUtils.sin(time * PULSE) + 1f) / 2f
            val m = MARGIN + pulse * GROW
            val x = pos.x - m
            val y = pos.y - m
            val w = actor.width + 2 * m
            val h = actor.height + 2 * m
            batch.color = c.set(Theme.highlight).also { it.a = (ALPHA_MIN + (1 - ALPHA_MIN) * pulse) * parentAlpha }
            val t = THICKNESS
            batch.draw(ui.skin.white, x, y, w, t)
            batch.draw(ui.skin.white, x, y + h - t, w, t)
            batch.draw(ui.skin.white, x, y, t, h)
            batch.draw(ui.skin.white, x + w - t, y, t, h)
            batch.color = Color.WHITE
        }
    }

    private companion object {
        const val TEXT_WIDTH = 380f
        const val PULSE = 5f
        const val MARGIN = 3f
        const val GROW = 4f
        const val THICKNESS = 3f
        const val ALPHA_MIN = 0.35f
    }
}
