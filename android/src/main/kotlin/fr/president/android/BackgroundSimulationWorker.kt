package fr.president.android

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/** Travail WorkManager : survit à la fermeture de l'application et au redémarrage du téléphone. */
class BackgroundSimulationWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result = when (BackgroundRun.execute(applicationContext)) {
        BackgroundRun.Outcome.FAILED -> if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        else -> Result.success()
    }

    private companion object {
        const val MAX_RETRIES = 3
    }
}
