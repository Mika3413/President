package fr.president.android

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Planification du travail d'arrière-plan. Deux mécanismes complémentaires, qui survivent à la
 * fermeture de l'application et au redémarrage du téléphone (WorkManager) :
 *  - une simulation périodique (toutes les 15 minutes, minimum imposé par Android) ;
 *  - un réveil ciblé au prochain moment marquant (scrutin, vote, réponse diplomatique...), par
 *    une alarme autorisée même en veille profonde (Doze), sans permission spéciale.
 */
object BackgroundScheduler {
    const val PERIODIC_WORK = "president-background-simulation"
    const val TARGETED_WORK = "president-next-moment"
    const val PERIOD_MINUTES = 15L
    /** Petite marge pour que le moment soit déjà passé dans le monde au réveil. */
    private const val TARGET_MARGIN_MILLIS = 30_000L
    private const val MIN_DELAY_MILLIS = 60_000L

    fun schedule(context: Context, nextKeyMomentUtcMillis: Long?) {
        ensurePeriodic(context)
        scheduleTargeted(context, nextKeyMomentUtcMillis)
    }

    /**
     * Le travail périodique reste planifié en permanence, même jeu ouvert (il ne fait alors rien) :
     * ainsi la partie continue même si le processus est tué sans avoir été mis en pause.
     */
    fun ensurePeriodic(context: Context) {
        val periodic = PeriodicWorkRequestBuilder<BackgroundSimulationWorker>(PERIOD_MINUTES, TimeUnit.MINUTES)
            .setInitialDelay(PERIOD_MINUTES, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, periodic)
    }

    fun cancelTargeted(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(alarmIntent(context))
    }

    fun scheduleTargeted(context: Context, nextKeyMomentUtcMillis: Long?) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = alarmIntent(context)
        if (nextKeyMomentUtcMillis == null) {
            alarms.cancel(pending)
            return
        }
        val at = maxOf(nextKeyMomentUtcMillis + TARGET_MARGIN_MILLIS, System.currentTimeMillis() + MIN_DELAY_MILLIS)
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }

    /** Lance immédiatement une simulation de rattrapage (après un redémarrage, par exemple). */
    fun runSoon(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            TARGETED_WORK, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<BackgroundSimulationWorker>().build(),
        )
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, ALARM_REQUEST, Intent(context, KeyMomentReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun cancel(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(PERIODIC_WORK)
        wm.cancelUniqueWork(TARGETED_WORK)
        context.getSystemService(AlarmManager::class.java)?.cancel(alarmIntent(context))
    }

    private const val ALARM_REQUEST = 7
}
