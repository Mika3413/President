package fr.president.android

import android.content.Context
import android.util.Log
import fr.president.engine.data.DataLoader
import fr.president.engine.data.GameDatabase
import fr.president.engine.save.SaveRepository
import fr.president.engine.session.GameSession
import fr.president.game.app.GameController

/**
 * Simulation de rattrapage hors du jeu : charge la sauvegarde, fait avancer le monde jusqu'à
 * maintenant, enregistre, publie les notifications puis planifie le prochain réveil.
 * Utilisée par le travail périodique comme par l'alarme ciblée ; jamais deux à la fois.
 */
object BackgroundRun {
    private const val TAG = "President"
    private val lock = Any()
    @Volatile private var cachedDb: GameDatabase? = null

    enum class Outcome { DONE, SKIPPED, FAILED }

    fun execute(context: Context): Outcome = synchronized(lock) {
        val app = context.applicationContext
        val dir = AndroidPlatform.saveDirectory(app)
        if (ForegroundFlag.isForeground(dir)) return Outcome.SKIPPED
        val saves = SaveRepository(dir)
        if (!saves.exists()) return Outcome.SKIPPED
        try {
            val db = cachedDb ?: DataLoader(AssetDataSource(app.assets)).load().also { cachedDb = it }
            val session = GameSession.fromSave(db, saves.read())
            if (session.isGameOver) {
                BackgroundScheduler.cancel(app)
                return Outcome.SKIPPED
            }
            session.advanceToNow()
            val pending = session.context.notifications.drainPlatformQueue()
            // Le joueur a pu rouvrir le jeu pendant la simulation : sa partie a priorité.
            if (ForegroundFlag.isForeground(dir)) return Outcome.SKIPPED
            saves.write(session.toSaveFile(GameController.GAME_VERSION))
            GameNotifier(app).postBatch(pending, session.state.player.gameOver != null)
            if (session.isGameOver) BackgroundScheduler.cancel(app)
            else BackgroundScheduler.scheduleTargeted(app, session.nextKeyMomentRealMillis())
            Outcome.DONE
        } catch (e: Exception) {
            Log.e(TAG, "Échec de la simulation en arrière-plan", e)
            Outcome.FAILED
        }
    }
}
