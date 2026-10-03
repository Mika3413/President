package fr.president.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/** Canaux de notification Android (réglables par le joueur dans les paramètres système). */
object NotificationChannels {
    const val URGENT = "urgent"
    const val IMPORTANT = "important"

    fun create(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(URGENT, context.getString(R.string.channel_urgent), NotificationManager.IMPORTANCE_HIGH),
        )
        manager.createNotificationChannel(
            NotificationChannel(IMPORTANT, context.getString(R.string.channel_important), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }
}
