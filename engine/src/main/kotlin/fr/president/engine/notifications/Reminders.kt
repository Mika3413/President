package fr.president.engine.notifications

import fr.president.engine.session.GameSession

/**
 * Rappels de rendez-vous quand le jeu est fermé : une décision qui va expirer, une élection
 * imminente. Ils passent seulement par les notifications du téléphone (jamais en jeu, où la
 * pastille des messages suffit) et réveillent le jeu au bon moment.
 */
class Reminders(private val session: GameSession) {
    private val state get() = session.state
    private val clock get() = state.meta.clock

    /** Prochain rappel à envoyer (heure réelle), pour planifier un réveil ciblé. */
    fun nextReminderRealMillis(): Long? {
        val deadlines = state.inbox.messages.filter { it.awaitingAnswer && !it.reminded && it.deadline != null && it.deadline > state.time }
            .map { clock.realMillisAt(it.deadline!!) - MESSAGE_LEAD_MILLIS }
        val election = if (state.player.electionReminded < state.elections.nextElection.seconds) clock.realMillisAt(state.elections.nextElection) - ELECTION_LEAD_MILLIS else null
        return (deadlines + listOfNotNull(election)).minOrNull()
    }

    /** Ajoute à la file du téléphone les rappels devenus pertinents ; à appeler en arrière-plan. */
    fun collect(realNow: Long) {
        val ctx = session.context
        val settings = state.notifications.settings
        if (!settings.enabled) return
        val due = state.inbox.messages.filter { m ->
            m.awaitingAnswer && !m.reminded && m.deadline != null && m.deadline > state.time &&
                clock.realMillisAt(m.deadline) - realNow <= MESSAGE_LEAD_MILLIS
        }
        // Un seul rappel groupé : jamais une rafale.
        if (due.isNotEmpty()) {
            due.forEach { it.reminded = true }
            val first = due.first()
            val default = first.options.firstOrNull { it.id == first.defaultOptionId }?.label
            val title = if (due.size == 1) "Dernier délai : ${first.subject}" else "${due.size} décisions expirent bientôt"
            val body = if (due.size == 1) "Sans réponse, l'Élysée appliquera : ${default?.lowercase() ?: "la réponse par défaut"}." else due.take(3).joinToString(" · ") { it.subject }
            state.notifications.pendingPlatform += GameNotification(state.nextId++, NotificationCategory.POLITICS, Urgency.IMPORTANT, title, body, state.time)
        }
        val next = state.elections.nextElection
        if (state.player.electionReminded < next.seconds && clock.realMillisAt(next) - realNow <= ELECTION_LEAD_MILLIS) {
            state.player.electionReminded = next.seconds
            state.notifications.pendingPlatform += GameNotification(state.nextId++, NotificationCategory.ELECTIONS, Urgency.URGENT,
                "L'élection approche", "Le pays vote très bientôt : derniers arbitrages, dernier discours, tout se joue maintenant.", state.time)
        }
        ctx.log("reminders", "${due.size} rappel(s) de décision")
    }

    private companion object {
        const val MESSAGE_LEAD_MILLIS = 3L * 3600_000
        const val ELECTION_LEAD_MILLIS = 24L * 3600_000
    }
}
