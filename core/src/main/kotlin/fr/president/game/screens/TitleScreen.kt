package fr.president.game.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.viewport.ScreenViewport
import fr.president.game.map.GeoProjection
import fr.president.game.map.MapData
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui
import kotlin.math.sin

/**
 * Écran d'accueil : la France dessinée département par département, traversée par une onde
 * bleu-blanc-rouge, et les choix « Continuer » ou « Nouvelle partie ».
 */
class TitleScreen(
    private val ui: Ui,
    private val map: MapData,
    uiScale: Float,
    hasSave: Boolean,
    subtitle: String?,
    private val onContinue: () -> Unit,
    private val onNewGame: () -> Unit,
) : ScreenAdapter(), HasStage {
    override val stage = Stage(ScreenViewport().apply { unitsPerPixel = 1f / uiScale })
    private val camera = OrthographicCamera()
    private val polygons = PolygonSpriteBatch()
    private val shapes = ShapeRenderer()
    private val color = Color()
    private var time = 0f
    /** Les médaillons d'outre-mer (codes à trois chiffres) ne figurent pas sur l'affiche. */
    private val metropolitan = map.departments.filter { it.id.length < OVERSEAS_CODE_LENGTH }

    init {
        val root = Table().apply { setFillParent(true); left().padLeft(PAD) }
        val box = Table().apply { defaults().left().padBottom(8f) }
        box.add(ui.label("PRÉSIDENT", "headline")).row()
        box.add(ui.label("Gouverner la France, une décision après l'autre.", "default")).padBottom(18f).row()
        subtitle?.let { box.add(ui.label(it, "small", Theme.warning, wrap = true)).width(TEXT_WIDTH).row() }
        if (hasSave) box.add(ui.button("Continuer la partie", "accent") { leave(onContinue) }).width(BUTTON_WIDTH).height(BUTTON_HEIGHT).row()
        box.add(ui.button(if (hasSave) "Nouvelle partie…" else "Commencer une partie", if (hasSave) "default" else "accent") { leave(onNewGame) })
            .width(BUTTON_WIDTH).height(BUTTON_HEIGHT).row()
        box.add(ui.label("Le monde continue de tourner quand l'application est fermée.", "muted")).padTop(18f).row()
        root.add(box)
        root.color.a = 0f
        root.addAction(Actions.fadeIn(FADE_IN, Interpolation.fade))
        stage.addActor(root)
        Gdx.input.inputProcessor = stage
    }

    private fun leave(next: () -> Unit) {
        Gdx.input.inputProcessor = null
        stage.root.addAction(Actions.sequence(Actions.fadeOut(FADE_OUT, Interpolation.fade), Actions.run { next() }))
    }

    override fun render(delta: Float) {
        time += delta
        Gdx.gl.glClearColor(Theme.sea.r, Theme.sea.g, Theme.sea.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        fitCamera()

        polygons.projectionMatrix = camera.combined
        polygons.begin()
        for (dept in metropolitan) {
            // Onde lente qui parcourt la carte d'ouest en est : bleu, blanc, rouge.
            val phase = time * WAVE_SPEED - dept.labelX * WAVE_SCALE + dept.labelY * WAVE_TILT
            val t = (sin(phase.toDouble()).toFloat() + 1f) / 2f
            if (t < 0.5f) color.set(BLUE).lerp(WHITE, t * 2f) else color.set(WHITE).lerp(RED, (t - 0.5f) * 2f)
            color.lerp(Theme.france, BASE_MIX)
            polygons.color = color
            dept.rings.forEach { polygons.draw(it.region, 0f, 0f) }
        }
        polygons.end()

        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapes.projectionMatrix = camera.combined
        shapes.begin(ShapeRenderer.ShapeType.Line)
        shapes.color = Theme.departmentBorder
        metropolitan.forEach { d -> d.rings.forEach { shapes.polygon(it.vertices) } }
        shapes.end()

        stage.act(delta)
        stage.draw()
    }

    /** La France occupe la moitié droite de l'écran, le menu la gauche. */
    private fun fitCamera() {
        val w = Gdx.graphics.width.toFloat()
        val h = Gdx.graphics.height.toFloat()
        val franceW = GeoProjection.x(EAST) - GeoProjection.x(WEST)
        val franceH = GeoProjection.y(NORTH) - GeoProjection.y(SOUTH)
        val zoom = maxOf(franceW / (w * MAP_SHARE), franceH / (h * MAP_HEIGHT_SHARE))
        camera.setToOrtho(false, w * zoom, h * zoom)
        camera.position.set(GeoProjection.x(WEST) + franceW / 2 - w * zoom * MAP_OFFSET, GeoProjection.y(SOUTH) + franceH / 2, 0f)
        camera.update()
    }

    override fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
    }

    override fun dispose() {
        stage.dispose()
        polygons.dispose()
        shapes.dispose()
    }

    private companion object {
        const val PAD = 48f
        const val OVERSEAS_CODE_LENGTH = 3
        const val TEXT_WIDTH = 420f
        const val BUTTON_WIDTH = 280f
        const val BUTTON_HEIGHT = 48f
        const val FADE_IN = 0.8f
        const val FADE_OUT = 0.35f
        const val WAVE_SPEED = 0.6f
        const val WAVE_SCALE = 0.004f
        const val WAVE_TILT = 0.002f
        const val BASE_MIX = 0.35f
        const val WEST = -5.5
        const val EAST = 9.8
        const val SOUTH = 41.2
        const val NORTH = 51.3
        const val MAP_SHARE = 0.5f
        const val MAP_HEIGHT_SHARE = 0.85f
        /** Décalage de la France vers la droite (part de la largeur d'écran). */
        const val MAP_OFFSET = 0.2f
        val BLUE: Color = Color.valueOf("2a5f93")
        val WHITE: Color = Color.valueOf("e8edf2")
        val RED: Color = Color.valueOf("e05a4f")
    }
}
