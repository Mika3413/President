package fr.president.engine.notifications

import fr.president.engine.events.NewsEntry
import fr.president.engine.simulation.SimulationContext

/** Publie les notifications internes et sélectionne celles à pousser vers la plateforme. */
class NotificationCenter(private val ctx: SimulationContext) {

    fun post(
        category: NotificationCategory,
        urgency: Urgency,
        title: String,
        body: String,
        focusId: String? = null,
    ): GameNotification {
        val state = ctx.state.notifications
        val n = GameNotification(ctx.state.nextId++, category, urgency, title, body, ctx.now, focusId)
        state.feed.add(n)
        trim(state.feed, ctx.db.config.simulation.notificationFeedSize)
        state.unreadCount++
        if (state.settings.shouldPush(n)) state.pendingPlatform.add(n)
        ctx.log("notification", "[${category.name}/${urgency.name}] $title")
        return n
    }

    /** Ajoute une brève au fil d'actualité (sans notification). */
    fun news(category: NotificationCategory, headline: String, focusId: String? = null) {
        val news = ctx.state.events.news
        news.add(NewsEntry(ctx.now, category, headline, focusId))
        trim(news, ctx.db.config.simulation.notificationFeedSize)
    }

    /** Vide la file des notifications destinées au système. */
    fun drainPlatformQueue(): List<GameNotification> {
        val pending = ctx.state.notifications.pendingPlatform
        val copy = pending.toList()
        pending.clear()
        return copy
    }

    private fun <T> trim(list: MutableList<T>, max: Int) {
        while (list.size > max) list.removeAt(0)
    }
}
