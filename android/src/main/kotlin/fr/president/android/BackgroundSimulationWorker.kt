package fr.president.android

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import fr.president.engine.data.DataLoader
import fr.president.engine.save.SaveRepository
import fr.president.engine.session.GameSession
import fr.president.game.app.GameController

/**
 * Simulation de rattrapage en arrière-plan : charge la sauvegarde, simule le temps écoulé,
 * enregistre, puis publie les notifications que le joueur a choisi de recevoir.
 * Le moteur étant indépendant de libGDX, aucun contexte graphique n'est nécessaire.
 */
class BackgroundSimulationWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val dir = AndroidPlatform.saveDirectory(applicationContext)
        if (ForegroundFlag.isForeground(dir)) return Result.success()
        val saves = SaveRepository(dir)
        if (!saves.exists()) return Result.success()
        return try {
            val db = DataLoader(AssetDataSource(applicationContext.assets)).load()
            val session = GameSession.fromSave(db, saves.read())
            if (session.isGameOver) return Result.success()
            session.advanceToNow()
            val pending = session.context.notifications.drainPlatformQueue()
            saves.write(session.toSaveFile(GameController.GAME_VERSION))
            val notifier = GameNotifier(applicationContext)
            pending.takeLast(MAX_NOTIFICATIONS).forEach { notifier.post(it) }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Échec de la simulation en arrière-plan", e)
            Result.retry()
        }
    }

    private companion object {
        const val TAG = "President"
        /** Évite d'inonder le joueur après une longue absence. */
        const val MAX_NOTIFICATIONS = 5
    }
}
