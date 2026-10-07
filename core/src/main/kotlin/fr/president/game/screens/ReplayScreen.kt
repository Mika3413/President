package fr.president.game.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.utils.viewport.ScreenViewport
import fr.president.engine.readout.ReplayReadout
import fr.president.engine.readout.Tone
import fr.president.engine.session.GameSession
import fr.president.game.map.GeoProjection
import fr.president.game.map.MapCameraController
import fr.president.game.map.MapData
import fr.president.game.map.MapRenderer
import fr.president.game.map.ThematicLayer
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui

/**
 * Relecture accélérée du mandat : la carte de popularité des départements évolue semaine après
 * semaine, les fronts avancent et reculent, les chiffres clés défilent avec les grands titres du
 * moment. Lecture, pause, vitesse, et frise où l'on touche pour sauter à une date.
 */
class ReplayScreen(
    private val ui: Ui,
    private val session: GameSession,
    private val map: MapData,
    uiScale: Float,
    private val onClose: () -> Unit,
) : ScreenAdapter() {
    private val stage = Stage(ScreenViewport().apply { unitsPerPixel = 1f / uiScale })
    private val camera = OrthographicCamera()
    private val renderer = MapRenderer(map, session.state.player.countryId)
    private val cameraController = MapCameraController(camera) { _, _ -> }
    private val readout = ReplayReadout(session.context)
    private val count = readout.count
    private val milestones = readout.milestones()

    /** Position dans le mandat, en semaines (fractionnaire pour des fondus doux). */
    private var position = 0f
    private var playing = count > 1
    private var speed = SPEEDS[1]
    private var shown = -1
    private var current: ReplayReadout.Step? = null
    private var next: ReplayReadout.Step? = null
    private var cameraReady = false

    private val dateLabel = ui.label("", "headline", Theme.highlight)
    private val enemiesLabel = ui.label("", "small", Theme.bad)
    private val metrics = Table()
    private val headlines = Table()
    private val playButton: TextButton = ui.button("❚❚ Pause") { togglePlay() }
    private val speedButtons = ArrayList<TextButton>()
    private val color = Color()
    private val mix = Color()

    init {
        renderer.zoneLookup = session.db.zones
        renderer.holderHostile = { h -> current?.enemyIds?.contains(h) == true }
        renderer.departmentOverride = { code -> departmentColor(code) }
        build()
    }

    private fun build() {
        val root = Table().apply { setFillParent(true) }
        val top = Table().apply { setBackground(ui.skin.fill(Theme.panel)); pad(6f, 10f, 6f, 10f) }
        top.add(ui.label("▶ Relecture du mandat", "bold")).left().expandX()
        top.add(ui.button("✕ Fermer") { close() }).right()
        root.add(top).growX().row()
        val head = Table().apply { pad(6f, 10f, 0f, 10f) }
        head.add(dateLabel).left().row()
        head.add(enemiesLabel).left().row()
        root.add(head).growX().left().row()
        root.add().expand().row()
        val card = Table().apply { setBackground(ui.skin.fill(Theme.panel)); pad(8f, 10f, 8f, 10f); defaults().left() }
        if (count < 2) {
            card.add(ui.label("Pas encore assez d'histoire à relire : revenez après quelques semaines de mandat.", "default", wrap = true)).growX().row()
        } else {
            card.add(Timeline()).growX().height(TIMELINE_H).padBottom(6f).row()
            val controls = Table().apply { defaults().padRight(4f) }
            controls.add(ui.button("Début") { position = 0f; playing = true; updatePlay() })
            controls.add(playButton)
            SPEEDS.forEachIndexed { i, s ->
                val b = ui.button(SPEED_LABELS[i], "toggle") { speed = s; updateSpeed() }
                speedButtons += b
                controls.add(b)
            }
            card.add(controls).left().padBottom(6f).row()
            card.add(metrics).growX().padBottom(4f).row()
            card.add(headlines).growX().row()
            card.add(ui.label("Couleur des départements : popularité du président (rouge : faible, vert : forte). Carrés : zones occupées.", "muted", wrap = true)).growX().padTop(4f).row()
        }
        root.add(card).growX().pad(6f)
        stage.addActor(root)
        updateSpeed()
        updatePlay()
    }

    private fun togglePlay() {
        if (!playing && position >= count - 1) position = 0f
        playing = !playing
        updatePlay()
    }

    private fun updatePlay() { playButton.setText(if (playing) "❚❚ Pause" else "▶ Lecture") }
    private fun updateSpeed() { speedButtons.forEachIndexed { i, b -> b.isChecked = SPEEDS[i] == speed } }

    private fun close() {
        renderer.departmentOverride = null
        onClose()
    }

    /** Couleur d'un département, fondue entre deux semaines. */
    private fun departmentColor(code: String): Color? {
        val a = current?.approval?.get(code) ?: return null
        val b = next?.approval?.get(code) ?: a
        val f = position - position.toInt()
        val v = a + (b - a) * f
        return Theme.heat(((v - APPROVAL_LOW) / (APPROVAL_HIGH - APPROVAL_LOW)).toFloat(), color)
    }

    private fun show(i: Int) {
        if (i == shown) return
        shown = i
        current = readout.step(i)
        next = readout.step(i + 1)
        val s = current ?: return
        dateLabel.setText(s.date)
        enemiesLabel.setText(if (s.enemies.isEmpty()) "" else "⚔ En guerre contre : ${s.enemies.joinToString(", ")}")
        renderer.occupiedOverride = s.occupied
        metrics.clearChildren()
        s.metrics.forEach { m ->
            val cell = Table().apply { defaults().left() }
            cell.add(ui.label(m.label, "small", Theme.textMuted)).row()
            cell.add(ui.label(m.value, "bold")).row()
            cell.add(ui.label(m.delta, "small", toneColor(m.tone))).row()
            metrics.add(cell).growX().uniformX().left().padRight(6f)
        }
        headlines.clearChildren()
        s.headlines.forEach { h ->
            headlines.add(ui.label("${toneIcon(h.tone)} ${h.text}", "small", toneColor(h.tone), wrap = true)).growX().left().row()
        }
    }

    private fun toneColor(t: Tone) = when (t) { Tone.GOOD -> Theme.good; Tone.BAD -> Theme.bad; Tone.WARNING -> Theme.warning; Tone.NEUTRAL -> Theme.text }
    private fun toneIcon(t: Tone) = when (t) { Tone.GOOD -> "▲"; Tone.BAD -> "▼"; Tone.WARNING -> "!"; Tone.NEUTRAL -> "•" }

    /** Frise du mandat : progression, repères des faits marquants ; on la touche pour sauter. */
    private inner class Timeline : Widget() {
        init {
            addListener(object : InputListener() {
                override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int): Boolean { seek(x); return true }
                override fun touchDragged(event: InputEvent?, x: Float, y: Float, pointer: Int) = seek(x)
            })
        }

        private fun seek(x: Float) {
            position = ((x / width).coerceIn(0f, 1f) * (count - 1))
        }

        override fun getPrefHeight() = TIMELINE_H

        override fun draw(batch: Batch, parentAlpha: Float) {
            val white = ui.skin.white
            val mid = y + height / 2
            batch.color = color.set(Theme.panelAlt)
            batch.draw(white, x, mid - 3f, width, 6f)
            val done = width * (position / (count - 1).coerceAtLeast(1))
            batch.color = color.set(Theme.accent)
            batch.draw(white, x, mid - 3f, done, 6f)
            // Repères : élections, guerres, lois, titres sportifs.
            for ((i, e) in milestones) {
                val px = x + width * i / (count - 1).coerceAtLeast(1)
                batch.color = mix.set(toneColor(e.tone))
                batch.draw(white, px - 1f, mid - 8f, 2f, 16f)
            }
            batch.color = color.set(Theme.highlight)
            batch.draw(white, x + done - 4f, mid - 7f, 8f, 14f)
            batch.color = Color.WHITE
        }
    }

    override fun show() {
        Gdx.input.inputProcessor = InputMultiplexer(stage, cameraController.gestureDetector, cameraController.scrollProcessor)
        Gdx.input.setCatchKey(com.badlogic.gdx.Input.Keys.BACK, true)
        stage.addListener(object : InputListener() {
            override fun keyDown(event: InputEvent?, keycode: Int): Boolean {
                if (keycode != com.badlogic.gdx.Input.Keys.BACK && keycode != com.badlogic.gdx.Input.Keys.ESCAPE) return false
                close(); return true
            }
        })
    }

    override fun render(delta: Float) {
        fr.president.game.ui.MusicPlayer.update(delta)
        if (playing && count > 1) {
            position += delta * speed
            if (position >= count - 1) { position = (count - 1).toFloat(); playing = false; updatePlay() }
        }
        if (count > 0) show(position.toInt().coerceIn(0, count - 1))
        camera.update()
        renderer.render(camera, session.state, ThematicLayer.OPINION, fr.president.game.map.LodPolicy.of(camera.viewportWidth * camera.zoom), null)
        stage.act(delta)
        stage.draw()
    }

    override fun resize(width: Int, height: Int) {
        camera.setToOrtho(false, width.toFloat(), height.toFloat())
        if (!cameraReady) {
            cameraReady = true
            if (map.playerCountryId == "FRA") cameraController.focus(GeoProjection.x(FRANCE_LON), GeoProjection.y(FRANCE_LAT), FRANCE_VIEW_WIDTH)
            else { val (x, y, w) = map.homeView(); cameraController.focus(x, y, w.coerceIn(FRANCE_VIEW_WIDTH * 0.6f, FRANCE_VIEW_WIDTH * 4)) }
        }
        stage.viewport.update(width, height, true)
    }

    override fun dispose() {
        stage.dispose()
        renderer.dispose()
    }

    private companion object {
        /** Semaines relues par seconde. */
        val SPEEDS = listOf(2f, 5f, 12f)
        val SPEED_LABELS = listOf("×1", "×2", "×5")
        const val TIMELINE_H = 22f
        const val APPROVAL_LOW = 0.35
        const val APPROVAL_HIGH = 0.62
        const val FRANCE_LON = 2.4
        const val FRANCE_LAT = 46.6
        const val FRANCE_VIEW_WIDTH = 1500f
    }
}
