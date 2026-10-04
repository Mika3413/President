package fr.president.game.ui.hud

import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.VerticalGroup
import fr.president.engine.notifications.GameNotification
import fr.president.engine.notifications.Urgency
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui
import fr.president.game.ui.onClick

/** Bandeaux temporaires annonçant les nouvelles alertes en jeu. */
class Toasts(private val ui: Ui, private val onFocus: (String) -> Unit) {
    val root = VerticalGroup().apply { space(4f); top() }

    fun show(n: GameNotification) {
        if (n.urgency == Urgency.INFO) return
        fr.president.game.ui.Sfx.play(if (n.urgency == Urgency.URGENT) fr.president.game.ui.Sfx.Kind.ALERT else fr.president.game.ui.Sfx.Kind.MESSAGE)
        val toast = ui.panelTable().apply { pad(6f, 10f, 6f, 10f) }
        val color = if (n.urgency == Urgency.URGENT) Theme.bad else Theme.warning
        toast.add(ui.label(n.title, "bold", color, wrap = true)).width(TOAST_WIDTH).row()
        if (n.body.isNotBlank()) toast.add(ui.label(n.body, "small", wrap = true)).width(TOAST_WIDTH).row()
        n.focusId?.let { f -> toast.onClick { onFocus(f) } }
        toast.color.a = 0f
        toast.addAction(Actions.sequence(Actions.fadeIn(FADE_IN_SECONDS, com.badlogic.gdx.math.Interpolation.fade),
            Actions.delay(DISPLAY_SECONDS), Actions.fadeOut(FADE_SECONDS), Actions.removeActor()))
        while (root.children.size >= MAX_VISIBLE) root.children.first().remove()
        root.addActor(toast)
    }

    private companion object {
        const val TOAST_WIDTH = 320f
        const val DISPLAY_SECONDS = 6f
        const val FADE_SECONDS = 0.6f
        const val FADE_IN_SECONDS = 0.25f
        const val MAX_VISIBLE = 3
    }
}
