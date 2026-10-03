package fr.president.android

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import fr.president.engine.notifications.GameNotification
import fr.president.game.platform.PlatformServices
import java.io.File

/**
 * Services Android. Le processus peut être tué à tout moment : rien ne dépend de lui.
 * En arrière-plan, la simulation continue grâce à WorkManager et à une alarme ciblée,
 * qui mettent la sauvegarde à jour et publient les notifications importantes.
 */
class AndroidPlatform(private val context: Context) : PlatformServices {
    override val saveDirectory: File = saveDirectory(context)
    override val uiScale: Float = context.resources.displayMetrics.density

    override fun postSystemNotification(notification: GameNotification) =
        GameNotifier(context).post(notification)

    override fun onBackgrounded(nextKeyMomentUtcMillis: Long?) {
        ForegroundFlag.set(saveDirectory, false)
        BackgroundScheduler.schedule(context, nextKeyMomentUtcMillis)
    }

    override fun onForegrounded() {
        ForegroundFlag.set(saveDirectory, true)
        BackgroundScheduler.ensurePeriodic(context)
        BackgroundScheduler.cancelTargeted(context)
        GameNotifier(context).clearAll()
    }

    override fun onHeartbeat() = ForegroundFlag.set(saveDirectory, true)

    override val backgroundRestricted: Boolean
        get() = context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == false

    @SuppressLint("BatteryLife")
    override fun requestBackgroundExemption() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure {
            runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    companion object {
        fun saveDirectory(context: Context) = File(context.filesDir, "saves")
    }
}
