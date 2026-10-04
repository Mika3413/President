package fr.president.game.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.viewport.ScreenViewport
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui

/**
 * Affiché pendant que la partie se prépare en arrière-plan (création du monde, rattrapage du
 * temps écoulé) : l'application reste réactive, même sur un téléphone lent.
 */
class LoadingScreen(ui: Ui, uiScale: Float, title: String) : ScreenAdapter(), HasStage {
    override val stage = Stage(ScreenViewport().apply { unitsPerPixel = 1f / uiScale })
    private val dots: Label = ui.label("", "headline", Theme.accent)
    private var time = 0f

    init {
        val root = Table().apply { setFillParent(true) }
        root.add(ui.label(title, "title")).padBottom(12f).row()
        root.add(dots).row()
        root.color.a = 0f
        root.addAction(Actions.fadeIn(FADE_IN, Interpolation.fade))
        stage.addActor(root)
    }

    override fun show() { Gdx.input.inputProcessor = null }

    override fun render(delta: Float) {
        time += delta
        dots.setText("•".repeat(1 + (time / DOT_SECONDS).toInt() % MAX_DOTS))
        Gdx.gl.glClearColor(Theme.background.r, Theme.background.g, Theme.background.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        stage.act(delta)
        stage.draw()
    }

    override fun resize(width: Int, height: Int) = stage.viewport.update(width, height, true)
    override fun dispose() = stage.dispose()

    private companion object {
        const val FADE_IN = 0.3f
        const val DOT_SECONDS = 0.4f
        const val MAX_DOTS = 3
    }
}
