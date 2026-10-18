package fr.president.game.ui.hud

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Disposable
import fr.president.engine.readout.Tone
import fr.president.engine.stats.BigNews
import fr.president.game.ui.Formats
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui
import fr.president.game.ui.blockInput
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Les grands moments en plein écran : flash spécial de la télévision (bandeau rouge, direct,
 * texte qui défile) pour les drames, une de journal illustrée pour le reste. Les illustrations
 * sont dessinées par programme.
 */
class BigNewsOverlay(private val ui: Ui) : Disposable {
    val root: Table = Table().apply {
        setFillParent(true)
        isVisible = false
        blockInput()
    }
    private val shapes = ShapeRenderer()

    fun show(n: BigNews, onClose: () -> Unit) {
        root.clearChildren()
        root.setBackground(ui.skin.fill(Color(0f, 0f, 0f, BACKDROP)))
        if (n.flash) flash(n, onClose) else frontPage(n, onClose)
        root.color.a = 0f
        root.addAction(Actions.fadeIn(FADE, com.badlogic.gdx.math.Interpolation.fade))
        root.isVisible = true
    }

    private fun flash(n: BigNews, onClose: () -> Unit) {
        val card = Table().apply { setBackground(ui.skin.fill(TV_BACK)); defaults().left().growX() }
        val band = Table().apply { setBackground(ui.skin.fill(Theme.bad)); pad(6f, 10f, 6f, 10f) }
        band.add(ui.label("FLASH SPÉCIAL", "title", Color.WHITE)).left().expandX()
        val live = ui.label("● EN DIRECT", "bold", Color.WHITE)
        live.addAction(Actions.forever(Actions.sequence(Actions.alpha(0.25f, BLINK), Actions.alpha(1f, BLINK))))
        band.add(live).right()
        card.add(band).row()
        card.add(Illustration(n.picture)).height(ILLUSTRATION_H).pad(10f, 10f, 0f, 10f).row()
        card.add(ui.label(n.title, "headline", Color.WHITE, wrap = true)).pad(10f, 12f, 4f, 12f).row()
        card.add(ui.label(n.subtitle, "default", TV_TEXT, wrap = true)).pad(0f, 12f, 8f, 12f).row()
        // Bandeau défilant en bas de l'écran, comme sur les chaînes d'information.
        val tickerBox = Table().apply { setBackground(ui.skin.fill(TV_TICKER)); pad(4f); clip = true }
        val ticker = ui.label(("  ▶ ${n.title.uppercase()}  ▶ ${n.subtitle}").repeat(2), "small", Color.WHITE)
        // Le texte déborde volontairement : le bandeau le coupe et le fait défiler.
        tickerBox.add(ticker).left().prefWidth(0f).minWidth(0f).expandX()
        ticker.addAction(Actions.forever(Actions.sequence(Actions.moveBy(-TICKER_SHIFT, 0f, TICKER_SECONDS), Actions.moveBy(TICKER_SHIFT, 0f))))
        card.add(tickerBox).height(TICKER_H).row()
        card.add(ui.colorButton("Continuer", Theme.accentDark) { close(onClose) }).pad(10f).left().fill(false, false).expandX().row()
        root.add(card).width(com.badlogic.gdx.scenes.scene2d.ui.Value.percentWidth(WIDTH_SHARE, root)).maxWidth(MAX_WIDTH)
    }

    private fun frontPage(n: BigNews, onClose: () -> Unit) {
        val paper = Table().apply { setBackground(ui.skin.fill(PAPER)); pad(14f); defaults().left().growX() }
        paper.add(ui.label("LE QUOTIDIEN NATIONAL", "title", INK)).center().fill(false, false).expandX().row()
        paper.add(Table().apply { setBackground(ui.skin.fill(INK)) }).height(2f).padTop(4f).row()
        paper.add(ui.label("${Formats.date(n.time)} · Édition spéciale", "small", INK_SOFT)).center().fill(false, false).expandX().padTop(2f).row()
        paper.add(Table().apply { setBackground(ui.skin.fill(INK)) }).height(1f).padTop(2f).padBottom(10f).row()
        paper.add(ui.label(n.title.uppercase(), "headline", if (n.tone == Tone.BAD) DARK_RED else INK, wrap = true)).row()
        val frame = Table().apply { setBackground(ui.skin.fill(INK)); pad(2f) }
        frame.add(Illustration(n.picture)).height(ILLUSTRATION_H).growX()
        paper.add(frame).padTop(10f).row()
        paper.add(ui.label(n.subtitle, "default", INK_SOFT, wrap = true)).padTop(8f).row()
        paper.add(ui.colorButton("Tourner la page", Theme.accentDark) { close(onClose) }).padTop(12f).left().fill(false, false).expandX().row()
        root.add(paper).width(com.badlogic.gdx.scenes.scene2d.ui.Value.percentWidth(WIDTH_SHARE, root)).maxWidth(MAX_WIDTH)
        paper.isTransform = true
        paper.setOrigin(com.badlogic.gdx.utils.Align.center)
        // La une arrive en tournoyant, comme dans les vieux films.
        paper.addAction(Actions.sequence(Actions.parallel(Actions.scaleTo(0.2f, 0.2f), Actions.rotateTo(-200f)),
            Actions.parallel(Actions.scaleTo(1f, 1f, SPIN, com.badlogic.gdx.math.Interpolation.swingOut), Actions.rotateTo(0f, SPIN, com.badlogic.gdx.math.Interpolation.pow2Out))))
    }

    private fun close(onClose: () -> Unit) {
        root.isVisible = false
        root.clearChildren()
        onClose()
    }

    /** Illustration dessinée par programme, légèrement animée. */
    private inner class Illustration(private val picture: String) : Actor() {
        private var time = 0f
        private val c = Color()

        override fun act(delta: Float) { super.act(delta); time += delta }

        override fun draw(batch: Batch, parentAlpha: Float) {
            batch.end()
            shapes.projectionMatrix = batch.projectionMatrix
            shapes.transformMatrix = batch.transformMatrix
            com.badlogic.gdx.Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND)
            shapes.begin(ShapeRenderer.ShapeType.Filled)
            val w = width; val h = height
            val x0 = x; val y0 = y
            fun rect(x: Float, y: Float, rw: Float, rh: Float, col: Color) { shapes.color = c.set(col).also { it.a *= parentAlpha }; shapes.rect(x0 + x * w, y0 + y * h, rw * w, rh * h) }
            fun circle(x: Float, y: Float, r: Float, col: Color) { shapes.color = c.set(col).also { it.a *= parentAlpha }; shapes.circle(x0 + x * w, y0 + y * h, r * h, 28) }
            fun tri(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, col: Color) {
                shapes.color = c.set(col).also { it.a *= parentAlpha }
                shapes.triangle(x0 + ax * w, y0 + ay * h, x0 + bx * w, y0 + by * h, x0 + cx * w, y0 + cy * h)
            }
            val pulse = (sin(time * 3f) + 1f) / 2f
            when (picture) {
                "WAR" -> {
                    rect(0f, 0f, 1f, 1f, SKY_RED); rect(0f, 0f, 1f, 0.3f, GROUND_DARK)
                    for (i in 0 until 5) circle(0.15f + i * 0.18f, 0.32f + (i % 2) * 0.08f, 0.1f + pulse * 0.04f * ((i + 1) % 3), FIRE)
                    // Silhouettes de chars.
                    for (i in 0 until 3) { val bx = 0.12f + i * 0.3f; rect(bx, 0.18f, 0.18f, 0.08f, SILHOUETTE); rect(bx + 0.04f, 0.26f, 0.09f, 0.05f, SILHOUETTE); rect(bx + 0.12f, 0.285f, 0.12f, 0.015f, SILHOUETTE) }
                }
                "PEACE" -> {
                    rect(0f, 0f, 1f, 1f, SKY_BLUE); circle(0.8f, 0.78f, 0.13f, SUN)
                    // Colombe : corps, ailes, rameau.
                    val dy = pulse * 0.04f
                    circle(0.5f, 0.5f + dy, 0.12f, Color.WHITE); circle(0.62f, 0.56f + dy, 0.07f, Color.WHITE)
                    tri(0.42f, 0.52f + dy, 0.2f, 0.85f + dy, 0.55f, 0.6f + dy, Color.WHITE)
                    tri(0.38f, 0.48f + dy, 0.22f, 0.4f + dy, 0.4f, 0.55f + dy, Color.WHITE)
                    tri(0.67f, 0.56f + dy, 0.75f, 0.53f + dy, 0.67f, 0.52f + dy, SUN)
                    rect(0.7f, 0.5f + dy, 0.12f, 0.012f, OLIVE); circle(0.78f, 0.52f + dy, 0.025f, OLIVE); circle(0.74f, 0.49f + dy, 0.025f, OLIVE)
                }
                "NUCLEAR" -> {
                    rect(0f, 0f, 1f, 1f, SKY_ASH); rect(0f, 0f, 1f, 0.18f, GROUND_DARK)
                    val g = 0.7f + pulse * 0.3f
                    rect(0.46f, 0.18f, 0.08f, 0.45f, c.set(FIRE).mul(g, g, g, 1f).cpy())
                    circle(0.5f, 0.68f, 0.24f, FIRE); circle(0.38f, 0.66f, 0.15f, FIRE_DARK); circle(0.62f, 0.66f, 0.15f, FIRE_DARK)
                    circle(0.5f, 0.74f, 0.13f, FLASH_YELLOW); circle(0.5f, 0.2f, 0.14f, FIRE_DARK)
                }
                "TROPHY" -> {
                    rect(0f, 0f, 1f, 1f, SKY_BLUE)
                    for (i in 0 until 26) { val px = ((i * 37) % 100) / 100f; val py = ((i * 61 + (time * 30).toInt()) % 100) / 100f; rect(px, 1f - py, 0.015f, 0.03f, CONFETTI[i % CONFETTI.size]) }
                    circle(0.5f, 0.62f, 0.22f, GOLD); rect(0.3f, 0.62f, 0.4f, 0.2f, GOLD)
                    circle(0.29f, 0.7f, 0.08f, GOLD_DARK); circle(0.71f, 0.7f, 0.08f, GOLD_DARK)
                    rect(0.47f, 0.24f, 0.06f, 0.2f, GOLD_DARK); rect(0.38f, 0.14f, 0.24f, 0.1f, GOLD)
                }
                "BETRAYAL" -> {
                    rect(0f, 0f, 1f, 1f, NIGHT); circle(0.78f, 0.75f, 0.12f, MOON)
                    // Poignard.
                    tri(0.5f, 0.92f, 0.46f, 0.42f, 0.54f, 0.42f, BLADE)
                    rect(0.38f, 0.38f, 0.24f, 0.04f, GOLD_DARK); rect(0.475f, 0.14f, 0.05f, 0.24f, HANDLE); circle(0.5f, 0.13f, 0.035f, GOLD_DARK)
                    circle(0.5f, 0.92f - pulse * 0.02f, 0.01f, Theme.bad)
                }
                "PARLIAMENT" -> {
                    rect(0f, 0f, 1f, 1f, SKY_BLUE)
                    for (ring in 0 until 4) for (k in 0..12) {
                        val a = PI * k / 12; val r = 0.18f + ring * 0.09f
                        val col = HEMICYCLE[(k + ring) % HEMICYCLE.size]
                        circle(0.5f + (cos(a) * r * h / w).toFloat(), 0.15f + (sin(a) * r).toFloat(), 0.025f, col)
                    }
                    rect(0.44f, 0.08f, 0.12f, 0.08f, SILHOUETTE)
                }
                "ATTACK", "RIOT" -> {
                    rect(0f, 0f, 1f, 1f, NIGHT)
                    for (i in 0 until 6) rect(0.02f + i * 0.165f, 0f, 0.13f, 0.3f + (i % 3) * 0.12f, SILHOUETTE)
                    val blue = if ((time * 4).toInt() % 2 == 0) SIREN_BLUE else SIREN_RED
                    circle(0.2f, 0.75f, 0.1f, blue); circle(0.8f, 0.75f, 0.1f, if (blue === SIREN_BLUE) SIREN_RED else SIREN_BLUE)
                    if (picture == "RIOT") for (i in 0 until 4) circle(0.2f + i * 0.2f, 0.32f, 0.07f + pulse * 0.03f, FIRE)
                }
                else -> {
                    rect(0f, 0f, 1f, 1f, SKY_ASH)
                    for (i in 0 until 3) { val wy = 0.2f + i * 0.12f + pulse * 0.02f; rect(0f, wy, 1f, 0.06f, WAVE) }
                    for (i in 0 until 4) rect(0.1f + i * 0.22f, 0.15f, 0.14f, 0.2f, SILHOUETTE)
                }
            }
            shapes.end()
            batch.begin()
        }
    }

    override fun dispose() = shapes.dispose()

    private companion object {
        const val BACKDROP = 0.75f
        const val FADE = 0.35f
        const val BLINK = 0.5f
        const val SPIN = 0.9f
        const val ILLUSTRATION_H = 170f
        const val TICKER_H = 22f
        const val TICKER_SHIFT = 600f
        const val TICKER_SECONDS = 9f
        const val WIDTH_SHARE = 0.94f
        const val MAX_WIDTH = 560f
        val TV_BACK: Color = Color.valueOf("12151c")
        val TV_TEXT: Color = Color.valueOf("d6dbe3")
        val TV_TICKER: Color = Color.valueOf("a32020")
        val PAPER: Color = Color.valueOf("f2ede0")
        val INK: Color = Color.valueOf("1d1b18")
        val INK_SOFT: Color = Color.valueOf("4a453c")
        val DARK_RED: Color = Color.valueOf("8f1d1d")
        val SKY_RED: Color = Color.valueOf("5a1a14")
        val SKY_BLUE: Color = Color.valueOf("7fb3e0")
        val SKY_ASH: Color = Color.valueOf("4b4f57")
        val NIGHT: Color = Color.valueOf("141a2b")
        val GROUND_DARK: Color = Color.valueOf("2a221c")
        val FIRE: Color = Color.valueOf("f07b2a")
        val FIRE_DARK: Color = Color.valueOf("b4471d")
        val FLASH_YELLOW: Color = Color.valueOf("ffe28a")
        val SILHOUETTE: Color = Color.valueOf("15120f")
        val SUN: Color = Color.valueOf("ffd166")
        val OLIVE: Color = Color.valueOf("5c7a2e")
        val GOLD: Color = Color.valueOf("e8b923")
        val GOLD_DARK: Color = Color.valueOf("b38712")
        val MOON: Color = Color.valueOf("e8e3c8")
        val BLADE: Color = Color.valueOf("c9ced6")
        val HANDLE: Color = Color.valueOf("4a2c1a")
        val SIREN_BLUE: Color = Color.valueOf("2f6bff")
        val SIREN_RED: Color = Color.valueOf("ff3b3b")
        val WAVE: Color = Color.valueOf("3d6f9e")
        val CONFETTI = listOf("e63946", "2a9d8f", "f4a261", "ffffff", "457b9d").map { Color.valueOf(it) }
        val HEMICYCLE = listOf("c0392b", "e67e22", "f1c40f", "2980b9", "34495e").map { Color.valueOf(it) }
    }
}
