package fr.president.desktop

import fr.president.engine.notifications.GameNotification
import fr.president.game.platform.PlatformServices
import java.io.File

/** Services de plateforme pour le PC : sauvegardes dans le dossier utilisateur, notifications en console. */
class DesktopPlatform : PlatformServices {
    override val saveDirectory: File = File(
        System.getProperty("president.saveDir") ?: (System.getProperty("user.home") + "/.president/saves"),
    )
    override val uiScale: Float = System.getProperty("president.uiScale")?.toFloatOrNull() ?: 1f

    override fun postSystemNotification(notification: GameNotification) {
        println("[notification] ${notification.title} — ${notification.body}")
    }

    override fun onBackgrounded(nextKeyMomentUtcMillis: Long?) = Unit
    override fun onForegrounded() = Unit
}
