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

    /**
     * Le jeu passe en arrière-plan : planifier la simulation et les notifications différées.
     * [nextKeyMomentUtcMillis] : prochain moment marquant connu (scrutin, vote...), pour un réveil ciblé.
     */
    fun onBackgrounded(nextKeyMomentUtcMillis: Long?)

    fun onForegrounded()

    /** Appelé régulièrement tant que le jeu est au premier plan (signal de vie). */
    fun onHeartbeat() {}

    /** L'appareil peut-il retarder ou bloquer le travail d'arrière-plan (optimisation de batterie) ? */
    val backgroundRestricted: Boolean get() = false

    /** Ouvre le réglage système permettant au jeu de travailler en arrière-plan. */
    fun requestBackgroundExemption() {}

    fun nowUtcMillis(): Long = System.currentTimeMillis()

    /** Rapport d'une erreur fatale enregistrée lors de la session précédente (puis effacé), ou null. */
    fun takeCrashReport(): String? = null
}
