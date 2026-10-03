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
class Ui(val skin: UiSkin) {
    val s: Skin get() = skin.skin

    fun label(text: String, style: String = "default", color: Color? = null, wrap: Boolean = false): Label =
        Label(text, s, style).apply {
            color?.let { this.color = it }
            this.wrap = wrap
        }

    fun button(text: String, style: String = "default", action: () -> Unit): TextButton =
        TextButton(text, s, style).apply { onClick(action) }

    fun panelTable(): Table = Table().apply { setBackground(this@Ui.skin.fill(Theme.panel)); blockInput() }

    fun separator(): Actor = Table().apply { setBackground(this@Ui.skin.fill(Theme.panelBorder)) }
}

fun Actor.onClick(action: () -> Unit) {
    addListener(object : ClickListener() {
        override fun clicked(event: InputEvent?, x: Float, y: Float) = action()
    })
}

/** Empêche les touchers sur un panneau de traverser jusqu'à la carte. */
fun Actor.blockInput() {
    addListener(object : InputListener() {
        override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int) = true
        override fun scrolled(event: InputEvent?, x: Float, y: Float, amountX: Float, amountY: Float) = true
    })
}
