package fr.president.game.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.viewport.ScreenViewport
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui

/**
 * Affiché à la place d'un plantage : explique ce qui s'est passé et permet de repartir.
 * Le détail technique peut être photographié et transmis pour correction.
 */
class ErrorScreen(
    ui: Ui,
    uiScale: Float,
    where: String,
    report: String,
    onHome: () -> Unit,
) : ScreenAdapter(), HasStage {
    override val stage = Stage(ScreenViewport().apply { unitsPerPixel = 1f / uiScale })

    init {
        val content = Table().apply { pad(24f); defaults().left().padBottom(8f) }
        content.add(ui.label("Une erreur est survenue", "headline")).row()
        content.add(ui.label("Le jeu ne s'est pas fermé : votre partie est sauvegardée régulièrement. " +
            "Faites une capture de cet écran et transmettez-la pour que le problème soit corrigé.", "default", wrap = true)).width(TEXT_WIDTH).row()
        content.add(ui.label("Étape : $where", "bold", Theme.warning)).row()
        content.add(ui.label(report.lines().take(MAX_LINES).joinToString("\n"), "small", Theme.textMuted, wrap = true)).width(TEXT_WIDTH).row()
        content.add(ui.button("Revenir à l'accueil", "accent") { Gdx.app.postRunnable(onHome) }).width(BUTTON_WIDTH).padTop(12f).row()
        val root = Table().apply { setFillParent(true) }
        root.add(ScrollPane(content, ui.s)).grow()
        stage.addActor(root)
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
        const val TEXT_WIDTH = 720f
        const val BUTTON_WIDTH = 280f
        const val MAX_LINES = 25
    }
}
