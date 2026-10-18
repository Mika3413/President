package fr.president.game.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.viewport.ScreenViewport
import fr.president.engine.politics.Character
import fr.president.engine.politics.CharacterRole
import fr.president.engine.save.GalleryEntry
import fr.president.engine.time.WorldTime
import fr.president.engine.util.Formatting
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui
import fr.president.game.ui.tolerant

/** Galerie de vos présidents : portraits, années, parcours, fin de mandat, note de l'Histoire. */
class GalleryScreen(private val ui: Ui, entries: List<GalleryEntry>, uiScale: Float, private val onClose: () -> Unit) : ScreenAdapter(), HasStage {
    override val stage = Stage(ScreenViewport().apply { unitsPerPixel = 1f / uiScale })

    init {
        val root = Table().apply { setFillParent(true); pad(10f) }
        val list = Table().apply { top(); defaults().growX().padBottom(8f) }
        list.add(ui.label("Galerie de vos présidents", "headline")).left().row()
        list.add(ui.label("${entries.size} présidence${if (entries.size > 1) "s" else ""}, de la plus récente à la plus ancienne. Meilleure note : ${entries.maxOfOrNull { it.grade }?.let { note(it) } ?: "—"}.", "muted", wrap = true)).row()
        entries.sortedByDescending { it.to }.forEach { e -> list.add(card(e)).row() }
        val scroll = ScrollPane(list, ui.s).apply { setScrollingDisabled(true, false); setFadeScrollBars(false) }.tolerant()
        root.add(scroll).grow().maxWidth(MAX_WIDTH).row()
        root.add(ui.colorButton("Retour", Theme.accentDark) { onClose() }).left().padTop(8f)
        stage.addActor(root)
        stage.addListener(object : InputListener() {
            override fun keyDown(event: InputEvent?, keycode: Int): Boolean {
                if (keycode != com.badlogic.gdx.Input.Keys.BACK && keycode != com.badlogic.gdx.Input.Keys.ESCAPE) return false
                onClose(); return true
            }
        })
    }

    private fun card(e: GalleryEntry): Table {
        val t = Table().apply { setBackground(ui.skin.fill(Theme.panel)); pad(8f); defaults().left() }
        val face = Character(e.id, e.name.substringBefore(' '), e.name.substringAfter(' '), e.female, e.birthYear, "", CharacterRole.FORMER,
            portraitSeed = e.portraitSeed, appearance = e.appearance)
        t.add(ui.portraits.image(face, PORTRAIT)).size(PORTRAIT).top().padRight(10f)
        val text = Table().apply { defaults().left() }
        text.add(ui.label(e.name, "bold")).row()
        val years = "${WorldTime(e.from).toDateTime().year}–${WorldTime(e.to).toDateTime().year}"
        text.add(ui.label("${e.country} · $years · ${e.terms} mandat${if (e.terms > 1) "s" else ""}" + if (e.career.isNotEmpty()) " · ${e.career.lowercase()}" else "", "small", Theme.textMuted, wrap = true)).growX().row()
        text.add(ui.label("${e.title} — ${note(e.grade)}", "small", if (e.grade >= GOOD) Theme.good else if (e.grade >= AVERAGE) Theme.warning else Theme.bad, wrap = true)).growX().row()
        if (e.ending.isNotEmpty()) text.add(ui.label(e.ending, "muted", wrap = true)).growX().row()
        t.add(text).growX().minWidth(0f)
        return t
    }

    private fun note(g: Double) = "${Formatting.amount(Math.round(g * 10) / 10.0)} / 20"

    override fun show() { Gdx.input.inputProcessor = stage; Gdx.input.setCatchKey(com.badlogic.gdx.Input.Keys.BACK, true) }

    override fun render(delta: Float) {
        Gdx.gl.glClearColor(Theme.background.r, Theme.background.g, Theme.background.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        stage.act(delta)
        stage.draw()
    }

    override fun resize(width: Int, height: Int) = stage.viewport.update(width, height, true)
    override fun dispose() = stage.dispose()

    private companion object {
        const val MAX_WIDTH = 620f
        const val PORTRAIT = 56f
        const val GOOD = 12.0
        const val AVERAGE = 9.0
    }
}
