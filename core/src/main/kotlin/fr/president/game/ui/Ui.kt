package fr.president.game.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener

/** Petites fabriques pour construire l'interface de façon concise et homogène. */
class Ui(val skin: UiSkin, val portraits: fr.president.game.ui.widgets.Portraits = fr.president.game.ui.widgets.Portraits()) {
    /** Info-bulles de l'écran principal (null ailleurs). */
    var hints: Hints? = null
    val s: Skin get() = skin.skin
    /** Adaptation des textes au pays joué (posée par l'écran de jeu). */
    var localize: (String) -> String = { it }

    fun label(text: String, style: String = "default", color: Color? = null, wrap: Boolean = false): Label =
        Label(localize(text), s, style).apply {
            color?.let { this.color = it }
            this.wrap = wrap
        }

    fun button(text: String, style: String = "default", action: () -> Unit): TextButton =
        TextButton(localize(text), s, style).apply { onClick { Sfx.play(Sfx.Kind.CLICK); action() } }

    /** Bouton plein et coloré, pour les grandes catégories et les actions principales. */
    fun colorButton(text: String, color: Color, action: () -> Unit): TextButton =
        TextButton(localize(text), skin.colorButtonStyle(color)).apply { onClick { Sfx.play(Sfx.Kind.CLICK); action() } }

    fun panelTable(): Table = Table().apply { setBackground(this@Ui.skin.fill(Theme.panel)); blockInput() }

    fun separator(): Actor = Table().apply { setBackground(this@Ui.skin.fill(Theme.panelBorder)) }
}

fun Actor.onClick(action: () -> Unit) {
    addListener(object : ClickListener() {
        override fun clicked(event: InputEvent?, x: Float, y: Float) = action()
    })
}

/**
 * Défilement tolérant au doigt : sur un téléphone à haute densité, le seuil par défaut (20 pixels,
 * soit moins de 2 mm) transformait un simple appui en début de défilement et annulait le bouton.
 * On le porte à environ 4 mm, quelle que soit la densité de l'écran.
 */
fun com.badlogic.gdx.scenes.scene2d.ui.ScrollPane.tolerant(): com.badlogic.gdx.scenes.scene2d.ui.ScrollPane {
    val density = runCatching { com.badlogic.gdx.Gdx.graphics.density }.getOrDefault(1f).coerceAtLeast(1f)
    setFlickScrollTapSquareSize(TAP_SQUARE_DP * density)
    return this
}

private const val TAP_SQUARE_DP = 26f

/** Empêche les touchers sur un panneau de traverser jusqu'à la carte. */
fun Actor.blockInput() {
    addListener(object : InputListener() {
        override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int) = true
        override fun scrolled(event: InputEvent?, x: Float, y: Float, amountX: Float, amountY: Float) = true
    })
}
