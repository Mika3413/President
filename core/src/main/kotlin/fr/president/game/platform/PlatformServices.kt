package fr.president.game.platform

import fr.president.engine.notifications.GameNotification
import java.io.File

/** Services propres à la plateforme (Android, PC), injectés dans le jeu. */
interface PlatformServices {
    /** Répertoire persistant des sauvegardes. */
    val saveDirectory: File

    /** Facteur d'échelle de l'interface (densité d'écran). */
    val uiScale: Float

    /** Affiche une notification système (seulement lorsque le jeu n'est pas au premier plan). */
    fun postSystemNotification(notification: GameNotification)

    /** Le jeu passe en arrière-plan : planifier la simulation et les notifications différées. */
    fun onBackgrounded()

    fun onForegrounded()

    fun nowUtcMillis(): Long = System.currentTimeMillis()
}
