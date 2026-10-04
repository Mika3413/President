package fr.president.game.ui

import com.badlogic.gdx.graphics.Color
import fr.president.engine.readout.Tone

/** Palette de l'interface et de la carte. */
object Theme {
    val background = Color.valueOf("0f1720")
    val sea = Color.valueOf("21496e")
    val panel = Color.valueOf("16202bee")
    val panelAlt = Color.valueOf("1f2c3a")
    val panelBorder = Color.valueOf("2f4255")
    val text = Color.valueOf("e8edf2")
    val textMuted = Color.valueOf("9aabbc")
    val accent = Color.valueOf("4c9be8")
    val accentDark = Color.valueOf("2a5f93")
    val button = Color.valueOf("24364a")
    val buttonOver = Color.valueOf("2f4760")
    val buttonDown = Color.valueOf("3b5a7a")

    val good = Color.valueOf("4caf7d")
    val neutral = Color.valueOf("c9d1d9")
    val warning = Color.valueOf("e0a83a")
    val bad = Color.valueOf("e05a4f")

    val land = Color.valueOf("3a4a3f")
    val landForeign = Color.valueOf("9c9a86")
    val landSimulated = Color.valueOf("b5ad8a")
    val france = Color.valueOf("3f7fd0")
    private val relationHostile = Color.valueOf("c9544a")
    private val relationNeutral = Color.valueOf("d9c27a")
    private val relationFriendly = Color.valueOf("5fb35f")
    val relationWar = Color.valueOf("8e1f1f")

    /** Rouge (hostile) → jaune (neutre) → vert (allié). */
    fun relation(value: Float, out: Color): Color {
        val v = value.coerceIn(0f, 1f)
        return if (v < 0.5f) out.set(relationHostile).lerp(relationNeutral, v * 2f) else out.set(relationNeutral).lerp(relationFriendly, (v - 0.5f) * 2f)
    }
    val border = Color.valueOf("0b1118")
    val regionBorder = Color.valueOf("d7e3ee")
    val departmentBorder = Color.valueOf("8fa3b6")
    val highlight = Color.valueOf("ffd166")
    val rail = Color.valueOf("c77dff")
    val motorway = Color.valueOf("f4a261")

    /** Couleurs des grands domaines (barre d'actions, en-têtes de panneaux). */
    val catGovernment = Color.valueOf("3f72c4")
    val catEconomy = Color.valueOf("d49a1a")
    val catDiplomacy = Color.valueOf("23a08f")
    val catArmy = Color.valueOf("c4473a")
    val catInbox = Color.valueOf("8e5bc4")
    val catAlerts = Color.valueOf("e07b2a")
    val catElections = Color.valueOf("3da35d")
    val catSettings = Color.valueOf("5c7080")
    val catHelp = Color.valueOf("1fa3c4")
    val catLocal = Color.valueOf("2f9e6e")
    /** Bouton « Décider », mis en avant : le cœur du jeu. */
    val highlightDark = Color.valueOf("c98a12")
    val catStats = Color.valueOf("4a7fa8")

    fun tone(t: Tone): Color = when (t) {
        Tone.GOOD -> good
        Tone.NEUTRAL -> neutral
        Tone.WARNING -> warning
        Tone.BAD -> bad
    }

    /** Dégradé rouge → jaune → vert pour les cartes de chaleur. */
    fun heat(value: Float, out: Color): Color {
        val v = value.coerceIn(0f, 1f)
        return if (v < 0.5f) out.set(bad).lerp(warning, v * 2f) else out.set(warning).lerp(good, (v - 0.5f) * 2f)
    }
}
