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
        /** Faux pour les simples avis (courrier reçu) : le journal garde les faits, pas les annonces. */
        journal: Boolean = true,
        /** Les alertes concernent surtout la France : seules les nouvelles militaires du monde rejoignent le journal. */
        world: Boolean = category == NotificationCategory.MILITARY,
    ): GameNotification {
        if (world) toWorld(category, title, body, focusId)
        val state = ctx.state.notifications
        val n = GameNotification(ctx.state.nextId++, category, urgency, title, body, ctx.now, focusId)
        state.feed.add(n)
        trim(state.feed, ctx.db.config.simulation.notificationFeedSize)
        // Le compteur ne retient que ce qui mérite l'attention : les simples infos n'affolent pas la pastille.
        if (urgency != Urgency.INFO) state.unreadCount++
        if (state.settings.shouldPush(n)) state.pendingPlatform.add(n)
        ctx.log("notification", "[${category.name}/${urgency.name}] $title")
        // Tout ce qui est important ou urgent entre dans le journal du mandat.
        if (journal && urgency != Urgency.INFO) {
            fr.president.engine.stats.JournalService(ctx).add(category.label, title,
                if (urgency == Urgency.URGENT) fr.president.engine.readout.Tone.WARNING else fr.president.engine.readout.Tone.NEUTRAL)
        }
        return n
    }

    /** Ajoute une brève au fil d'actualité (sans notification). */
    fun news(category: NotificationCategory, headline: String, focusId: String? = null, world: Boolean = true) {
        val news = ctx.state.events.news
        news.add(NewsEntry(ctx.now, category, headline, focusId))
        trim(news, ctx.db.config.simulation.notificationFeedSize)
        if (world) toWorld(category, headline, "", focusId)
    }

    /** Ce qui concerne un pays étranger rejoint aussi le journal du monde. */
    private fun toWorld(category: NotificationCategory, headline: String, body: String, focusId: String?) {
        val country = focusId?.takeIf { it != ctx.state.player.countryId && it in ctx.state.countries } ?: return
        // Ce qui nous concerne directement (propositions, réponses à nos démarches) n'est pas une nouvelle du monde.
        if (ABOUT_US.containsMatchIn(headline) || ABOUT_US.containsMatchIn(body) || headline.startsWith("Réponse automatique")) return
        val kind = when (category) {
            NotificationCategory.MILITARY, NotificationCategory.SECURITY -> "conflit"
            NotificationCategory.ECONOMY, NotificationCategory.ENERGY -> "économie"
            NotificationCategory.DISASTER -> "catastrophe"
            NotificationCategory.POLITICS, NotificationCategory.ELECTIONS -> "politique"
            else -> "diplomatie"
        }
        // Une même nouvelle annoncée deux fois (alerte et brève) n'apparaît qu'une fois.
        if (ctx.state.world.entries.takeLast(DEDUP_WINDOW).any { it.headline == headline && country in it.countries }) return
        ctx.state.world.add(fr.president.engine.ai.WorldEntry(ctx.now, listOf(country), kind, headline, body))
    }

    private companion object {
        const val DEDUP_WINDOW = 12
        val ABOUT_US = Regex("\\b(vous|votre|vos|nous|notre|nos|France|français|française)\\b", RegexOption.IGNORE_CASE)
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
