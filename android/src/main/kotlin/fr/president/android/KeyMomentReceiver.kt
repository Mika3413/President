package fr.president.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Réveil ciblé (alarme) au moment d'un événement marquant, et relance après redémarrage du
 * téléphone. La simulation tourne directement, en tâche asynchrone ; en cas d'échec, elle est
 * confiée à WorkManager.
 */
class KeyMomentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            BackgroundScheduler.schedule(app, null)
            BackgroundScheduler.runSoon(app)
            return
        }
        val pending = goAsync()
        Thread {
            try {
                if (BackgroundRun.execute(app) == BackgroundRun.Outcome.FAILED) BackgroundScheduler.runSoon(app)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
