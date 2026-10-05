package fr.president.engine.setup

import fr.president.engine.dialogue.DialogueContextBuilder
import fr.president.engine.inbox.InboxMessage
import fr.president.engine.inbox.MessageOrigin
import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.simulation.SimulationContext

/** Premier message du Premier ministre au président fraîchement élu. */
class WelcomeMessage(private val ctx: SimulationContext) {
    fun send() {
        val pm = ctx.state.government.primeMinisterId?.let { ctx.state.characters[it] }
        val title = ctx.playerData.definition.institutions.headOfGovernment(pm?.female == true)
        val composed = ctx.messages.compose(TEMPLATE, DialogueContextBuilder(ctx).sender(pm, title).build())
        ctx.state.inbox.messages += InboxMessage(
            id = ctx.state.newId("msg"),
            senderId = pm?.id,
            senderLabel = listOfNotNull(pm?.fullName, title).joinToString(", "),
            subject = composed.subject,
            body = composed.body,
            time = ctx.now,
            category = NotificationCategory.GOVERNMENT,
            origin = MessageOrigin.INFO,
            originId = null,
        )
    }

    /**
     * Les fiches de prise en main ne passent plus par la messagerie (réservée à ce qui arrive dans
     * le monde) : elles sont dans l'Aide, l'Académie et la visite guidée. Les anciennes parties
     * peuvent encore en avoir programmé ou reçu : on les ignore et on les retire.
     */
    @Suppress("UNUSED_PARAMETER")
    fun tutorial(index: Int) = purgeTutorials()

    fun purgeTutorials() {
        ctx.state.inbox.messages.removeAll { it.senderLabel == TUTORIAL_SENDER && it.origin == MessageOrigin.INFO && !it.awaitingAnswer }
    }

    private companion object {
        const val TUTORIAL_SENDER = "Secrétaire général de la présidence"
        const val TEMPLATE = "welcome"
    }
}
