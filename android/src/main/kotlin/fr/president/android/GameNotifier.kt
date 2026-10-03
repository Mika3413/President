package fr.president.android

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import fr.president.engine.notifications.GameNotification
import fr.president.engine.notifications.Urgency

/** Publie une notification du jeu dans la barre système Android. */
class GameNotifier(private val context: Context) {

    fun post(n: GameNotification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        NotificationChannels.create(context)
        val intent = PendingIntent.getActivity(
            context, 0, Intent(context, AndroidLauncher::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val channel = if (n.urgency == Urgency.URGENT) NotificationChannels.URGENT else NotificationChannels.IMPORTANT
        val notification = Notification.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(n.title)
            .setContentText(n.body)
            .setStyle(Notification.BigTextStyle().bigText(n.body))
            .setSubText(n.category.label)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify((n.id % Int.MAX_VALUE).toInt(), notification)
    }
}
