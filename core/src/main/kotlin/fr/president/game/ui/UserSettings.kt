package fr.president.game.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences

/**
 * Réglages d'accessibilité propres à l'appareil (et non à la partie) : taille du texte,
 * palette pour daltoniens, sons. Conservés dans les préférences de la plateforme.
 */
object UserSettings {
    private val prefs: Preferences? by lazy { runCatching { Gdx.app.getPreferences(NAME) }.getOrNull() }

    /** Tailles de texte proposées (facteur appliqué à toute l'interface). */
    val textScales = listOf(0.9f to "Petit", 1f to "Normal", 1.15f to "Grand", 1.3f to "Très grand")

    var textScale: Float
        get() = prefs?.getFloat(TEXT, 1f) ?: 1f
        set(v) { prefs?.apply { putFloat(TEXT, v); flush() } }

    var colorblind: Boolean
        get() = prefs?.getBoolean(COLORBLIND, false) ?: false
        set(v) { prefs?.apply { putBoolean(COLORBLIND, v); flush() } }

    var sound: Boolean
        get() = prefs?.getBoolean(SOUND, true) ?: true
        set(v) { prefs?.apply { putBoolean(SOUND, v); flush() } }

    var music: Boolean
        get() = prefs?.getBoolean(MUSIC, true) ?: true
        set(v) { prefs?.apply { putBoolean(MUSIC, v); flush() } }

    /** Volumes (0 à 1) des sons et de la musique. */
    var soundVolume: Float
        get() = prefs?.getFloat(SOUND_VOLUME, 0.8f) ?: 0.8f
        set(v) { prefs?.apply { putFloat(SOUND_VOLUME, v); flush() } }

    var musicVolume: Float
        get() = prefs?.getFloat(MUSIC_VOLUME, 0.7f) ?: 0.7f
        set(v) { prefs?.apply { putFloat(MUSIC_VOLUME, v); flush() } }

    /** Alternance du jour et de la nuit sur la carte. */
    var dayNight: Boolean
        get() = prefs?.getBoolean(DAY_NIGHT, true) ?: true
        set(v) { prefs?.apply { putBoolean(DAY_NIGHT, v); flush() } }

    val volumes = listOf(0.25f to "25 %", 0.5f to "50 %", 0.8f to "80 %", 1f to "100 %")

    private const val SOUND_VOLUME = "soundVolume"
    private const val MUSIC_VOLUME = "musicVolume"
    private const val DAY_NIGHT = "dayNight"
    private const val NAME = "president-reglages"
    private const val MUSIC = "music"
    private const val TEXT = "textScale"
    private const val COLORBLIND = "colorblind"
    private const val SOUND = "sound"
}
