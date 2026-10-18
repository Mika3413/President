package fr.president.game.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.viewport.ScreenViewport
import fr.president.engine.readout.HistoryBookReadout
import fr.president.engine.readout.Tone
import fr.president.engine.session.GameSession
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui

/**
 * Le livre de votre présidence : une couverture, un chapitre par année, l'épilogue. On tourne
 * les pages comme un vrai livre.
 */
class HistoryBookScreen(private val ui: Ui, private val session: GameSession, uiScale: Float, private val onClose: () -> Unit) : ScreenAdapter(), HasStage {
    override val stage = Stage(ScreenViewport().apply { unitsPerPixel = 1f / uiScale })
    private val book = HistoryBookReadout(session.context).book()
    private val pages = book.chapters.size + 2
    private var page = 0
    private val paper = Table()
    private val nav = Table()

    init {
        val root = Table().apply { setFillParent(true); pad(10f) }
        paper.setBackground(ui.skin.fill(PAPER))
        paper.isTransform = true
        val scroll = ScrollPane(paper, ui.s).apply { setScrollingDisabled(true, false); setFadeScrollBars(false) }
        root.add(scroll).grow().maxWidth(MAX_WIDTH).row()
        root.add(nav).growX().maxWidth(MAX_WIDTH).padTop(8f)
        stage.addActor(root)
        build()
        stage.addListener(object : InputListener() {
            override fun keyDown(event: InputEvent?, keycode: Int): Boolean {
                if (keycode != com.badlogic.gdx.Input.Keys.BACK && keycode != com.badlogic.gdx.Input.Keys.ESCAPE) return false
                onClose(); return true
            }
        })
    }

    private fun turn(to: Int) {
        if (to !in 0 until pages || to == page) return
        fr.president.game.ui.Sfx.play(fr.president.game.ui.Sfx.Kind.CLICK)
        paper.setOrigin(com.badlogic.gdx.utils.Align.center)
        paper.clearActions()
        paper.addAction(Actions.sequence(Actions.scaleTo(0f, 1f, TURN / 2), Actions.run { page = to; build() }, Actions.scaleTo(1f, 1f, TURN / 2)))
    }

    private fun build() {
        paper.clearChildren()
        paper.top().pad(18f).defaults().left().growX()
        when (page) {
            0 -> cover()
            pages - 1 -> epilogue()
            else -> chapter(book.chapters[page - 1])
        }
        nav.clearChildren()
        nav.add(ui.button("◀ Page précédente") { turn(page - 1) }.also { it.isDisabled = page == 0 }).left()
        nav.add(ui.label("${page + 1} / $pages", "small", Theme.textMuted)).expandX()
        if (page < pages - 1) nav.add(ui.button("Page suivante ▶", "accent") { turn(page + 1) }).right()
        else nav.add(ui.colorButton("Fermer le livre", Theme.accentDark) { onClose() }).right()
    }

    private fun cover() {
        val president = session.state.characters.getValue(session.state.player.presidentId)
        paper.add(ui.label("HISTOIRE DE FRANCE", "small", INK_SOFT)).center().row()
        paper.add(rule()).height(2f).padTop(6f).padBottom(14f).row()
        paper.add(ui.portraits.image(president, PORTRAIT)).size(PORTRAIT).center().fill(false, false).expandX().row()
        paper.add(ui.label(book.title, "headline", INK, wrap = true)).padTop(14f).row()
        paper.add(ui.label(book.subtitle, "default", INK_SOFT, wrap = true)).padTop(4f).row()
        paper.add(rule()).height(1f).padTop(14f).padBottom(8f).row()
        book.chapters.forEach { c -> paper.add(ui.label("Chapitre ${c.number} · ${c.period} — ${c.title}", "small", INK, wrap = true)).padTop(2f).row() }
        paper.add(ui.label(book.epilogueTitle, "small", INK, wrap = true)).padTop(2f).row()
    }

    private fun chapter(c: HistoryBookReadout.Chapter) {
        paper.add(ui.label("CHAPITRE ${c.number} · ${c.period}", "small", INK_SOFT)).row()
        paper.add(ui.label(c.title, "headline", if (c.tone == Tone.BAD) DARK_RED else INK, wrap = true)).padTop(4f).row()
        paper.add(rule()).height(1f).padTop(8f).padBottom(10f).row()
        c.paragraphs.forEach { p -> paper.add(ui.label(p, "default", INK, wrap = true)).padBottom(8f).row() }
        if (c.figures.isNotEmpty()) {
            val box = Table().apply { setBackground(ui.skin.fill(PAPER_DARK)); pad(8f); left(); defaults().left() }
            box.add(ui.label("L'année en chiffres", "bold", INK)).row()
            c.figures.forEach { (label, value) -> box.add(ui.label("$label : $value", "small", INK_SOFT)).row() }
            paper.add(box).padTop(6f).row()
        }
    }

    private fun epilogue() {
        paper.add(ui.label("ÉPILOGUE", "small", INK_SOFT)).row()
        paper.add(ui.label(book.epilogueTitle.removePrefix("Épilogue : "), "headline", INK, wrap = true)).padTop(4f).row()
        paper.add(rule()).height(1f).padTop(8f).padBottom(10f).row()
        paper.add(ui.label(book.epilogue, "default", INK, wrap = true)).row()
        paper.add(ui.label(book.grade, "headline", DARK_RED)).center().fill(false, false).expandX().padTop(16f).row()
    }

    private fun rule() = Table().apply { setBackground(ui.skin.fill(INK)) }

    override fun show() { Gdx.input.inputProcessor = stage; Gdx.input.setCatchKey(com.badlogic.gdx.Input.Keys.BACK, true) }

    override fun render(delta: Float) {
        fr.president.game.ui.MusicPlayer.update(delta)
        Gdx.gl.glClearColor(DESK.r, DESK.g, DESK.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        stage.act(delta)
        stage.draw()
    }

    override fun resize(width: Int, height: Int) = stage.viewport.update(width, height, true)
    override fun dispose() = stage.dispose()

    private companion object {
        const val MAX_WIDTH = 620f
        const val PORTRAIT = 96f
        const val TURN = 0.4f
        val DESK: Color = Color.valueOf("2b211a")
        val PAPER: Color = Color.valueOf("f3ecdc")
        val PAPER_DARK: Color = Color.valueOf("e6dcc6")
        val INK: Color = Color.valueOf("221d17")
        val INK_SOFT: Color = Color.valueOf("5a5045")
        val DARK_RED: Color = Color.valueOf("8f1d1d")
    }
}
