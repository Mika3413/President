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
        val title = ctx.playerData.definition.institutions.headOfGovernmentTitle
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

    private companion object {
        const val TEMPLATE = "welcome"
    }
}
