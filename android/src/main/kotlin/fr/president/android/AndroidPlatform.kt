package fr.president.android

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import fr.president.engine.notifications.GameNotification
import fr.president.game.platform.PlatformServices
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Services Android. Le processus peut être tué à tout moment : rien ne dépend de lui.
 * En arrière-plan, WorkManager relance périodiquement une simulation de rattrapage
 * qui met la sauvegarde à jour et publie les notifications importantes.
 */
class AndroidPlatform(private val context: Context) : PlatformServices {
    override val saveDirectory: File = saveDirectory(context)
    override val uiScale: Float = context.resources.displayMetrics.density

    override fun postSystemNotification(notification: GameNotification) =
        GameNotifier(context).post(notification)

    override fun onBackgrounded() {
        val request = PeriodicWorkRequestBuilder<BackgroundSimulationWorker>(PERIOD_MINUTES, TimeUnit.MINUTES)
            .setInitialDelay(PERIOD_MINUTES, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        ForegroundFlag.set(saveDirectory, false)
    }

    override fun onForegrounded() {
        ForegroundFlag.set(saveDirectory, true)
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    companion object {
        /** Intervalle minimal autorisé par Android pour un travail périodique. */
        const val PERIOD_MINUTES = 15L
        const val WORK_NAME = "president-background-simulation"
        fun saveDirectory(context: Context) = File(context.filesDir, "saves")
    }
}
