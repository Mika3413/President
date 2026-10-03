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

    /** Messages de prise en main, distribués progressivement. */
    fun scheduleTutorial() {
        ctx.db.help.tutorial.forEachIndexed { i, t ->
            ctx.scheduler.schedule(fr.president.engine.simulation.ScheduledAction.Tutorial(ctx.now.plusHours(t.delayHours.toLong()), i))
        }
    }

    fun tutorial(index: Int) {
        val t = ctx.db.help.tutorial.getOrNull(index) ?: return
        val honorific = DialogueContextBuilder(ctx).build().variables["honorific"] ?: ""
        ctx.state.inbox.messages += InboxMessage(
            id = ctx.state.newId("msg"), senderId = null, senderLabel = TUTORIAL_SENDER,
            subject = t.subject, body = t.body.replace("Monsieur le Président", honorific),
            time = ctx.now, category = NotificationCategory.GOVERNMENT, origin = MessageOrigin.INFO, originId = null,
        )
    }

    private companion object {
        const val TUTORIAL_SENDER = "Secrétaire général de la présidence"
        const val TEMPLATE = "welcome"
    }
}
