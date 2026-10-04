package fr.president.game

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import fr.president.engine.data.DataLoader
import fr.president.engine.data.GameDatabase
import fr.president.engine.save.SaveRepository
import fr.president.engine.session.GameSession
import fr.president.engine.setup.NewGameOptions
import fr.president.engine.simulation.Simulator
import fr.president.game.app.GameController
import fr.president.game.app.GdxDataSource
import fr.president.game.map.MapData
import fr.president.game.platform.PlatformServices
import fr.president.game.screens.ErrorScreen
import fr.president.game.screens.GameOverScreen
import fr.president.game.screens.LoadingScreen
import fr.president.game.screens.MainScreen
import fr.president.game.screens.NewGameScreen
import fr.president.game.screens.TitleScreen
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

    /** Échelle de l'interface : densité de l'écran × taille de texte choisie par le joueur. */
    private val scale: Float get() = platform.uiScale * fr.president.game.ui.UserSettings.textScale

    override fun create() {
        fr.president.game.ui.Theme.applyPalette(fr.president.game.ui.UserSettings.colorblind)
        skin = UiSkin(scale)
        ui = Ui(skin)
        db = DataLoader(GdxDataSource()).load()
        saves = SaveRepository(platform.saveDirectory)
        platform.onForegrounded()
        safely("accueil") { showTitle() }
    }

    /**
     * Toute erreur est rattrapée : au lieu de fermer l'application, on affiche ce qui s'est passé
     * (et on l'enregistre pour le prochain lancement).
     */
    private fun safely(where: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            reportError(where, t)
        }
    }

    private fun reportError(where: String, t: Throwable) {
        Gdx.app.error(TAG, "Erreur ($where)", t)
        val report = t.stackTraceToString()
        runCatching { controller?.save() }
        controller = null
        runCatching {
            switchTo(ErrorScreen(ui, scale, where, report) { safely("accueil") { showTitle() } })
        }
    }

    override fun render() {
        try {
            super.render()
        } catch (t: Throwable) {
            reportError(if (screen is MainScreen) "partie en cours" else "affichage", t)
        }
    }

    /** Écran d'accueil : continuer la partie en cours ou en commencer une nouvelle. */
    private fun showTitle() {
        // Erreur fatale lors de la session précédente : on montre d'abord son détail.
        platform.takeCrashReport()?.let { report ->
            val where = report.lineSequence().firstOrNull().orEmpty()
            switchTo(ErrorScreen(ui, scale, "session précédente — $where", report.substringAfter('\n')) { safely("accueil") { showTitle() } })
            return
        }
        val map = mapData ?: MapData(db, skin.white, db.snapshot.playableCountries.first()).also { mapData = it }
        switchTo(TitleScreen(ui, map, scale, saves.exists(), null, onContinue = { Gdx.app.postRunnable { safely("reprise de la partie") { resumeSave() } } },
            onNewGame = { Gdx.app.postRunnable { safely("nouvelle partie") { showNewGame(null) } } },
            slots = saves.slots().map { TitleScreen.SlotEntry(it.id, it.meta.title.ifBlank { "Partie" }, it.meta.detail, it.id == saves.activeSlot) },
            onLoad = { id -> Gdx.app.postRunnable { safely("reprise de la partie") { saves.activeSlot = id; resumeSave() } } },
            onDelete = { id -> Gdx.app.postRunnable { safely("accueil") { saves.delete(id); showTitle() } } }))
    }

    private fun resumeSave() {
        val file = try {
            saves.read()
        } catch (e: Exception) {
            Gdx.app.error(TAG, "Sauvegarde illisible", e)
            showNewGame("Votre sauvegarde n'a pas pu être lue (${e.message}). Une copie a été conservée.")
            return
        }
        prepareSession("reprise de la partie", "Retour à l'Élysée", resumed = true) { GameSession.fromSave(db, file, platform::nowUtcMillis) }
    }

    private fun showNewGame(error: String?) {
        switchTo(NewGameScreen(ui, db, scale, platform::nowUtcMillis, error) { options ->
            // Après le traitement du toucher, pour ne pas détruire l'écran pendant qu'il gère l'événement.
            Gdx.app.postRunnable { safely("prise de fonctions") { newGame(options) } }
        })
    }

    fun newGame(options: NewGameOptions) {
        // Une nouvelle partie ne remplace jamais les précédentes : elle prend un emplacement libre.
        if (saves.exists()) saves.activeSlot = saves.newSlotId()
        prepareSession("prise de fonctions", "Passation de pouvoirs", resumed = false) { GameSession.newGame(db, options, platform::nowUtcMillis) }
    }

    /**
     * Prépare la partie hors du fil d'affichage (création du monde ou rattrapage du temps écoulé,
     * qui peut prendre plusieurs secondes sur téléphone) derrière un écran d'attente, puis l'affiche.
     */
    private fun prepareSession(where: String, title: String, resumed: Boolean, build: () -> GameSession) {
        controller = null
        switchTo(LoadingScreen(ui, scale, title))
        val work = Runnable {
            try {
                val c = GameController(build(), saves, platform)
                val report = c.advance()
                c.save()
                Gdx.app.postRunnable { safely(where) { showSession(c, report, resumed) } }
            } catch (t: Throwable) {
                Gdx.app.postRunnable { reportError(where, t) }
            }
        }
        Thread(null, work, "president-preparation", WORKER_STACK_BYTES).apply { isDaemon = true }.start()
    }

    private fun showSession(c: GameController, report: Simulator.Report, resumed: Boolean) {
        val session = c.session
        controller = c
        if (session.isGameOver) {
            showGameOver(session)
            return
        }
        val map = mapData ?: MapData(db, skin.white, session.state.player.countryId).also { mapData = it }
        val screen = MainScreen(c, ui, map, scale, { Gdx.app.postRunnable { safely("fin de partie") { showGameOver(session) } } },
            onDisplayChange = { Gdx.app.postRunnable { safely("réglages d'affichage") { applyDisplaySettings() } } }) {
            Gdx.app.postRunnable {
                safely("abandon") {
                    controller = null
                    saves.delete()
                    showNewGame(null)
                }
            }
        }
        switchTo(screen)
        if (resumed) screen.showAbsence(report)
    }

    /**
     * Taille du texte ou palette modifiées : on régénère polices et styles, puis on rouvre
     * l'écran de jeu sur la même partie.
     */
    fun applyDisplaySettings() {
        fr.president.game.ui.Theme.applyPalette(fr.president.game.ui.UserSettings.colorblind)
        val old = skin
        skin = UiSkin(scale)
        val portraits = ui.portraits
        ui = Ui(skin, portraits)
        // La carte dessine avec la texture blanche de l'habillage : elle est reconstruite avec lui.
        mapData = null
        val c = controller
        if (c != null) {
            val now = c.session.state.time
            showSession(c, Simulator.Report(now, now, 0, 0), resumed = false)
            // On revient là où le joueur était : dans les réglages.
            mainScreen?.open(fr.president.game.ui.panels.PanelId.SETTINGS)
        } else showTitle()
        Gdx.app.postRunnable { runCatching { old.dispose() } }
    }

    private fun showGameOver(session: GameSession) {
        controller?.save()
        controller = null
        switchTo(GameOverScreen(ui, session, scale) {
            Gdx.app.postRunnable {
                safely("nouvelle partie") {
                    saves.delete()
                    showNewGame(null)
                }
            }
        })
    }

    private fun switchTo(next: Screen) {
        val previous = screen
        setScreen(next)
        // L'ancien écran est libéré après la frame en cours (il peut encore être en train de traiter un événement).
        previous?.let { old -> Gdx.app.postRunnable { runCatching { old.dispose() } } }
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
            prepareSession("reprise de la partie", "Retour à l'Élysée", resumed = true) { GameSession.fromSave(db, newer, platform::nowUtcMillis) }
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
        /** Pile large : le fil de préparation ne doit jamais manquer de place, même sur Android. */
        const val WORKER_STACK_BYTES = 16L * 1024 * 1024
    }
}
