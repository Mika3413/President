package fr.president.engine

import fr.president.engine.dialogue.ConversationTopic
import fr.president.engine.dialogue.DialogueContextBuilder
import fr.president.engine.politics.CharacterRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DialogueVarietyTest {
    @Test
    fun `un même interlocuteur ne répète jamais une phrase`() {
        val session = TestData.newSession(seed = 31L)
        val ctx = session.context
        val mayor = session.state.characters.values.first { it.role == CharacterRole.MAYOR }
        val paragraphs = mutableListOf<String>()
        repeat(8) { i ->
            val topic = listOf("listen", "visit", "praise", "reprimand")[i % 4]
            val composed = ctx.messages.compose("talk_local", DialogueContextBuilder(ctx).sender(mayor, "Maire")
                .tag("topic:$topic").tag("mood:neutral").tag("talk:local").variable("place", "Lyon").build())
            paragraphs += composed.body.split("\n\n")
        }
        val duplicates = paragraphs.groupBy { it }.filter { it.value.size > 1 }.keys
        assertTrue(duplicates.isEmpty(), "Phrases répétées : $duplicates")
    }

    @Test
    fun `les propositions diplomatiques varient d'un pays à l'autre`() {
        val session = TestData.newSession(seed = 32L)
        val ctx = session.context
        val bodies = session.state.countries.values.filter { it.id != session.state.player.countryId }.take(12).map { country ->
            val leader = session.state.characters.getValue(country.leaderId)
            ctx.messages.compose("diplomatic_proposal", DialogueContextBuilder(ctx).sender(leader, "Chef du gouvernement")
                .tag("proposal:new").variable("foreignCountry", country.id).variable("clauses", "• un accord")
                .variable("motive", "sécuriser notre approvisionnement").build()).body
        }
        val firstLines = bodies.map { it.lines().first() }
        assertTrue(firstLines.toSet().size >= 8, "Ouvertures trop semblables : $firstLines")
    }

    @Test
    fun `un entretien produit un compte rendu et impose un délai`() {
        val session = TestData.newSession(seed = 33L)
        val country = session.state.countries.values.first { it.id == "DEU" }
        val result = session.conversations.talk(country.leaderId, ConversationTopic.STRENGTHEN).getOrThrow()
        val message = session.state.inbox.messages.first { it.id == result.messageId }
        assertTrue(message.body.contains("— Vous :"), message.body)
        assertNotNull(session.conversations.blocker(country.leaderId))
        val mayor = session.state.characters.values.first { it.role == CharacterRole.MAYOR }
        session.conversations.talk(mayor.id, ConversationTopic.LISTEN).getOrThrow()
        assertEquals(1, session.state.scheduler.actions.count { it is fr.president.engine.simulation.ScheduledAction.EventLaunch && it.scopeId == mayor.roleRef })
    }
}
