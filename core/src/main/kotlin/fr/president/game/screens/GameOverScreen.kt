package fr.president.game.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.viewport.ScreenViewport
import fr.president.engine.session.GameSession
import fr.president.engine.util.Formatting
import fr.president.game.ui.Formats
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui

/** Fin de partie : le président a perdu l'élection. Il ne devient pas chef de l'opposition. */
class GameOverScreen(ui: Ui, session: GameSession, uiScale: Float, onNewGame: () -> Unit) : ScreenAdapter() {
    private val stage = Stage(ScreenViewport().apply { unitsPerPixel = 1f / uiScale })

    init {
        val s = session.state
        val president = s.characters.getValue(s.player.presidentId)
        val days = s.meta.startTime.daysUntil(s.time).toInt()
        val t = Table().apply { setFillParent(true); defaults().padBottom(10f) }
        t.add(ui.label("FIN DE LA PRÉSIDENCE", "headline", Theme.bad)).row()
        t.add(ui.label(s.player.gameOver?.reason ?: "", "large", wrap = true)).width(WIDTH).row()
        t.add(ui.label("${president.fullName} a dirigé le pays pendant $days jours (${s.player.termNumber} mandat(s)), jusqu'au ${Formats.date(s.time)}.", "default", wrap = true)).width(WIDTH).row()
        s.elections.results.lastOrNull()?.let { r ->
            val share = (r.secondRound ?: r.firstRound).shares[president.id] ?: 0.0
            t.add(ui.label("Votre score : ${Formatting.percent(share)}", "default")).row()
        }
        val e = s.playerCountry.economy
        t.add(ui.label("Bilan : chômage ${Formatting.percent(e.unemployment)}, dette ${Formatting.percent(e.debtRatio)} du PIB, opinion ${Formatting.percent(s.opinion.nationalApproval)}.", "muted", wrap = true)).width(WIDTH).row()
        t.add(ui.button("Nouvelle partie", "accent") { onNewGame() }).padTop(20f)
        stage.addActor(t)
    }

    override fun show() { Gdx.input.inputProcessor = stage }

    override fun render(delta: Float) {
        Gdx.gl.glClearColor(Theme.background.r, Theme.background.g, Theme.background.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        stage.act(delta)
        stage.draw()
    }

    override fun resize(width: Int, height: Int) = stage.viewport.update(width, height, true)
    override fun dispose() = stage.dispose()

    private companion object {
        const val WIDTH = 600f
    }
}
