package fr.president.game.ui

import com.badlogic.gdx.Gdx

/** Vibrations du téléphone pour les grands moments (désactivées avec les sons). */
object Haptics {
    const val LIGHT = 40
    const val STRONG = 180

    fun buzz(millis: Int) {
        if (!UserSettings.sound || !UserSettings.vibrations) return
        runCatching { Gdx.input?.vibrate(millis) }
    }
}
