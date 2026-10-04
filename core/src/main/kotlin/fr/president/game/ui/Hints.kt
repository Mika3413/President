package fr.president.game.ui

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener

/**
 * Info-bulles : survol à la souris (après un court délai) ou appui long au doigt.
 * Une seule bulle à la fois, posée au-dessus de l'élément et gardée dans l'écran.
 */
class Hints(private val ui: Ui) {
    val bubble: Table = Table().apply {
        setBackground(ui.skin.fill(Theme.panelBorder))
        pad(6f, 8f, 6f, 8f)
        touchable = Touchable.disabled
        isVisible = false
    }
    private val text: Label = ui.label("", "small", wrap = true)
    private val pos = Vector2()

    init {
        bubble.add(text).width(WIDTH)
    }

    fun attach(actor: Actor, message: String) {
        if (message.isBlank()) return
        actor.addListener(object : InputListener() {
            override fun enter(event: InputEvent?, x: Float, y: Float, pointer: Int, fromActor: Actor?) {
                // pointer == -1 : souris sans bouton enfoncé (survol sur ordinateur).
                if (pointer == -1 && fromActor?.isDescendantOf(actor) != true) show(actor, message, HOVER_DELAY)
            }

            override fun exit(event: InputEvent?, x: Float, y: Float, pointer: Int, toActor: Actor?) {
                if (pointer == -1 && toActor?.isDescendantOf(actor) != true) hide()
            }
        })
        actor.addListener(object : ActorGestureListener() {
            override fun longPress(a: Actor?, x: Float, y: Float): Boolean {
                show(actor, message, 0f)
                bubble.addAction(Actions.sequence(Actions.delay(TOUCH_SECONDS), Actions.run { hide() }))
                return true
            }
        })
    }

    private fun show(actor: Actor, message: String, delay: Float) {
        val stage = actor.stage ?: return
        bubble.clearActions()
        text.setText(message)
        bubble.pack()
        actor.localToStageCoordinates(pos.set(actor.width / 2, actor.height))
        val x = (pos.x - bubble.width / 2).coerceIn(MARGIN, (stage.width - bubble.width - MARGIN).coerceAtLeast(MARGIN))
        var y = pos.y + GAP
        if (y + bubble.height > stage.height - MARGIN) {
            actor.localToStageCoordinates(pos.set(actor.width / 2, 0f))
            y = pos.y - bubble.height - GAP
        }
        bubble.setPosition(x, y.coerceAtLeast(MARGIN))
        bubble.toFront()
        if (delay <= 0f) { bubble.isVisible = true; return }
        bubble.isVisible = false
        bubble.addAction(Actions.sequence(Actions.delay(delay), Actions.visible(true)))
    }

    fun hide() {
        bubble.clearActions()
        bubble.isVisible = false
    }

    private companion object {
        const val WIDTH = 260f
        const val MARGIN = 6f
        const val GAP = 6f
        const val HOVER_DELAY = 0.45f
        const val TOUCH_SECONDS = 4f
    }
}

/** Ajoute une info-bulle si le gestionnaire est disponible (écran principal). */
fun Actor.hint(ui: Ui, message: String): Actor {
    ui.hints?.attach(this, message)
    return this
}
