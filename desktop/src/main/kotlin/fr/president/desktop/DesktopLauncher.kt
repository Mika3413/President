package fr.president.desktop

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import fr.president.game.PresidentGame

/** Lanceur PC de développement (même jeu, même moteur que sur Android). */
fun main() {
    val platform = DesktopPlatform()
    val game = PresidentGame(platform)
    val script = System.getProperty("president.script")
    val listener = if (script != null) DevScriptDriver(game, script) else game
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("Président")
        setWindowedMode(WIDTH, HEIGHT)
        useVsync(true)
        setForegroundFPS(FPS)
        setBackBufferConfig(8, 8, 8, 8, 16, 0, SAMPLES)
    }
    Lwjgl3Application(listener, config)
}

private const val WIDTH = 1600
private const val HEIGHT = 900
private const val FPS = 60
private const val SAMPLES = 4
