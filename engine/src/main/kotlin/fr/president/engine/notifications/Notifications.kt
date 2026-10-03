package fr.president.engine.notifications

import fr.president.engine.time.WorldTime
import kotlinx.serialization.Serializable

@Serializable
enum class NotificationCategory(val label: String) {
    DIPLOMACY("Diplomatie"),
    MILITARY("Militaire"),
    ECONOMY("Économie"),
    DISASTER("Catastrophes"),
    POLITICS("Politique"),
    TERRITORY("Territoires"),
    GOVERNMENT("Gouvernement"),
    PROJECTS("Projets"),
    ELECTIONS("Élections"),
    SECURITY("Sécurité"),
    ENERGY("Énergie"),
}

@Serializable
enum class Urgency { INFO, IMPORTANT, URGENT }

/** Réglage par catégorie : toutes, urgentes seulement, ou désactivée. */
@Serializable
enum class NotificationLevel(val label: String) { ALL("Toutes"), URGENT_ONLY("Urgentes seulement"), OFF("Désactivée") }

@Serializable
data class GameNotification(
    val id: Long,
    val category: NotificationCategory,
    val urgency: Urgency,
    val title: String,
    val body: String,
    val time: WorldTime,
    /** Élément de carte concerné (département, ville, infrastructure) pour centrer la vue. */
    val focusId: String? = null,
)

@Serializable
data class NotificationSettings(
    var enabled: Boolean = true,
    val levels: MutableMap<NotificationCategory, NotificationLevel> = defaultLevels(),
) {
    companion object {
        /** Catégories plus techniques : seules les urgences sont poussées par défaut. */
        private val QUIET = setOf(NotificationCategory.ECONOMY, NotificationCategory.PROJECTS)

        fun defaultLevels(): MutableMap<NotificationCategory, NotificationLevel> =
            NotificationCategory.entries.associateWith { if (it in QUIET) NotificationLevel.URGENT_ONLY else NotificationLevel.ALL }.toMutableMap()
    }

    fun level(category: NotificationCategory): NotificationLevel = levels[category] ?: NotificationLevel.URGENT_ONLY

    /** Faut-il pousser cette notification vers le système (Android) ? */
    fun shouldPush(n: GameNotification): Boolean {
        if (!enabled) return false
        return when (level(n.category)) {
            NotificationLevel.ALL -> n.urgency != Urgency.INFO
            NotificationLevel.URGENT_ONLY -> n.urgency == Urgency.URGENT
            NotificationLevel.OFF -> false
        }
    }
}

@Serializable
data class NotificationState(
    val feed: MutableList<GameNotification> = mutableListOf(),
    /** Notifications à transmettre à la plateforme (vidé par la couche Android/desktop). */
    val pendingPlatform: MutableList<GameNotification> = mutableListOf(),
    val settings: NotificationSettings = NotificationSettings(),
    var unreadCount: Int = 0,
)
