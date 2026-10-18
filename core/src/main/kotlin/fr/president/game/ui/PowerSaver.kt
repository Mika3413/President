package fr.president.game.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.Timer
import com.badlogic.gdx.utils.TimeUtils

/**
 * Économie de batterie : après un moment sans toucher l'écran, l'image n'est plus redessinée
 * 60 fois par seconde mais une quinzaine de fois (la carte reste animée, le jeu continue) ;
 * le moindre toucher rend toute sa fluidité.
 */
object PowerSaver {
    /** Désactivé par les scripts de développement (captures d'écran). */
    var enabled = true
    private var lastActive = 0L
    private var saving = false
    private var task: Timer.Task? = null

    fun onFrame() {
        val now = TimeUtils.millis()
        val input = Gdx.input ?: return
        val active = input.isTouched || input.justTouched()
        if (active || lastActive == 0L) lastActive = now
        when {
            saving && (active || !enabled || !UserSettings.batterySaver) -> wake()
            !saving && enabled && UserSettings.batterySaver && now - lastActive > IDLE_MILLIS -> sleep()
        }
    }

    fun wake() {
        lastActive = TimeUtils.millis()
        if (!saving) return
        saving = false
        task?.cancel(); task = null
        runCatching { Gdx.graphics.isContinuousRendering = true }
    }

    private fun sleep() {
        saving = true
        runCatching { Gdx.graphics.isContinuousRendering = false }
        task = Timer.schedule(object : Timer.Task() {
            override fun run() { Gdx.graphics?.requestRendering() }
        }, 0f, 1f / IDLE_FPS)
    }

    private const val IDLE_MILLIS = 20_000L
    private const val IDLE_FPS = 15f
}
