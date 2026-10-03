package fr.president.game

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import fr.president.engine.data.DataLoader
import fr.president.engine.data.GameDatabase
import fr.president.engine.save.SaveRepository
import fr.president.engine.session.GameSession
import fr.president.engine.setup.NewGameOptions
import fr.president.game.app.GameController
import fr.president.game.app.GdxDataSource
import fr.president.game.map.MapData
import fr.president.game.platform.PlatformServices
import fr.president.game.screens.GameOverScreen
import fr.president.game.screens.MainScreen
import fr.president.game.screens.NewGameScreen
import fr.president.game.ui.Ui
import fr.president.game.ui.UiSkin

/**
 * Point d'entrée libGDX : charge les données, reprend la sauvegarde (avec rattrapage du temps
 * écoulé) ou lance une nouvelle partie, puis délègue aux écrans.
 */
class PresidentGame(private val platform: PlatformServices) : Game() {
    private lateinit var skin: UiSkin
    private lateinit var ui: Ui
    private lateinit var db: GameDatabase
    private lateinit var saves: SaveRepository
    private var mapData: MapData? = null
    var controller: GameController? = null
        private set

    override fun create() {
        skin = UiSkin(platform.uiScale)
        ui = Ui(skin)
        db = DataLoader(GdxDataSource()).load()
        saves = SaveRepository(platform.saveDirectory)
        platform.onForegrounded()
        if (saves.exists()) {
            try {
                val session = GameSession.fromSave(db, saves.read(), platform::nowUtcMillis)
                startSession(session, resumed = true)
                return
            } catch (e: Exception) {
                Gdx.app.error(TAG, "Sauvegarde illisible", e)
                showNewGame("Votre sauvegarde n'a pas pu être lue (${e.message}). Une copie a été conservée.")
                return
            }
        }
        showNewGame(null)
    }

    private fun showNewGame(error: String?) {
        switchTo(NewGameScreen(ui, db, platform.uiScale, platform::nowUtcMillis, error) { options -> newGame(options) })
    }

    fun newGame(options: NewGameOptions) {
        startSession(GameSession.newGame(db, options, platform::nowUtcMillis), resumed = false)
    }

    private fun startSession(session: GameSession, resumed: Boolean) {
        val c = GameController(session, saves, platform)
        controller = c
        val report = c.advance()
        c.save()
        if (session.isGameOver) {
            showGameOver(session)
            return
        }
        val map = mapData ?: MapData(db, skin.white, session.state.player.countryId).also { mapData = it }
        val screen = MainScreen(c, ui, map, platform.uiScale, { Gdx.app.postRunnable { showGameOver(session) } }) {
            Gdx.app.postRunnable {
                controller = null
                saves.delete()
                showNewGame(null)
            }
        }
        switchTo(screen)
        if (resumed) screen.showAbsence(report)
    }

    private fun showGameOver(session: GameSession) {
        controller?.save()
        controller = null
        switchTo(GameOverScreen(ui, session, platform.uiScale) {
            saves.delete()
            showNewGame(null)
        })
    }

    private fun switchTo(next: Screen) {
        val previous = screen
        setScreen(next)
        previous?.dispose()
    }

    override fun pause() {
        controller?.onPause()
        super.pause()
    }

    override fun resume() {
        super.resume()
        val c = controller ?: return
        // La simulation d'arrière-plan a pu faire avancer la sauvegarde : on la reprend.
        val newer = runCatching { saves.read() }.getOrNull()?.takeIf { it.savedAtRealUtcMillis > c.lastSaveMillis }
        if (newer != null) {
            platform.onForegrounded()
            startSession(GameSession.fromSave(db, newer, platform::nowUtcMillis), resumed = true)
            return
        }
        val report = c.onResume()
        (screen as? MainScreen)?.showAbsence(report)
    }

    override fun dispose() {
        controller?.save()
        screen?.dispose()
        skin.dispose()
        ui.portraits.dispose()
    }

    /** Écran courant, pour l'automatisation de développement. */
    val mainScreen: MainScreen? get() = screen as? MainScreen

    private companion object {
        const val TAG = "President"
    }
}
