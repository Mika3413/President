package fr.president.engine.events

import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.time.WorldTime
import kotlinx.serialization.Serializable

@Serializable
class EventInstance(
    val id: String,
    val definitionId: String,
    val scopeId: String?,
    val params: Map<String, Double>,
    val startedAt: WorldTime,
    var messageId: String? = null,
    var resolved: Boolean = false,
    /** Nombre de relances (reports) déjà effectuées. */
    var reasks: Int = 0,
)

/** Brève d'actualité affichée dans le fil d'informations. */
@Serializable
data class NewsEntry(
    val time: WorldTime,
    val category: NotificationCategory,
    val headline: String,
    val focusId: String? = null,
)

@Serializable
class EventState(
    val lastFired: MutableMap<String, WorldTime> = mutableMapOf(),
    val lastFiredScope: MutableMap<String, WorldTime> = mutableMapOf(),
    val active: MutableList<EventInstance> = mutableListOf(),
    val news: MutableList<NewsEntry> = mutableListOf(),
)
