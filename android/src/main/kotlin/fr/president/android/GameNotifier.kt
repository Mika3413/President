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

/** Publie les notifications du jeu dans la barre système Android, regroupées. */
class GameNotifier(private val context: Context) {
    private val manager get() = context.getSystemService(NotificationManager::class.java)

    private fun allowed(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun post(n: GameNotification) {
        if (!allowed()) return
        NotificationChannels.create(context)
        manager.notify(idOf(n), build(n))
        postSummary(listOf(n), 0)
    }

    /**
     * Publie le résultat d'une simulation d'arrière-plan : les plus importantes une à une,
     * les autres résumées dans une notification de groupe (« et 6 autres événements »).
     */
    fun postBatch(notifications: List<GameNotification>, gameOver: Boolean) {
        if (!allowed() || notifications.isEmpty()) return
        NotificationChannels.create(context)
        val ranked = notifications.sortedWith(compareByDescending<GameNotification> { it.urgency.ordinal }.thenByDescending { it.time })
        val shown = if (gameOver) ranked.take(1) else ranked.take(MAX_INDIVIDUAL)
        shown.forEach { manager.notify(idOf(it), build(it)) }
        postSummary(ranked, ranked.size - shown.size)
    }

    fun clearAll() = runCatching { manager.cancelAll() }

    private fun build(n: GameNotification): Notification {
        val channel = if (n.urgency == Urgency.URGENT) NotificationChannels.URGENT else NotificationChannels.IMPORTANT
        return Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_president)
            .setColor(ACCENT)
            .setContentTitle(n.title)
            .setContentText(n.body)
            .setStyle(Notification.BigTextStyle().bigText(n.body))
            .setSubText(n.category.label)
            .setContentIntent(openGame())
            .setGroup(GROUP)
            .setAutoCancel(true)
            .build()
    }

    /** Notification de groupe : obligatoire pour regrouper, et porte le résumé des autres. */
    private fun postSummary(all: List<GameNotification>, hidden: Int) {
        val style = Notification.InboxStyle()
        all.take(MAX_SUMMARY_LINES).forEach { style.addLine(it.title) }
        val title = if (hidden > 0) "Et $hidden autre${if (hidden > 1) "s" else ""} événement${if (hidden > 1) "s" else ""}" else "Président"
        style.setSummaryText(if (hidden > 0) "Ouvrez le jeu pour tout voir" else "Nouvelles de la présidence")
        val summary = Notification.Builder(context, NotificationChannels.IMPORTANT)
            .setSmallIcon(R.drawable.ic_stat_president)
            .setColor(ACCENT)
            .setContentTitle(title)
            .setContentText(all.firstOrNull()?.title ?: "")
            .setStyle(style)
            .setContentIntent(openGame())
            .setGroup(GROUP)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()
        manager.notify(SUMMARY_ID, summary)
    }

    private fun openGame(): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, AndroidLauncher::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun idOf(n: GameNotification): Int = (n.id % (Int.MAX_VALUE - 1)).toInt() + 1

    private companion object {
        const val GROUP = "president"
        const val SUMMARY_ID = 0
        const val MAX_INDIVIDUAL = 4
        const val MAX_SUMMARY_LINES = 6
        const val ACCENT = 0xFF2A5F93.toInt()
    }
}
