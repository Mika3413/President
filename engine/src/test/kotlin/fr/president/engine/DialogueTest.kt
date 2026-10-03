package fr.president.engine

import fr.president.engine.dialogue.DialogueContextBuilder
import fr.president.engine.util.Hashing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DialogueTest {
    @Test
    fun `un même modèle ne produit jamais deux fois le même message`() {
        val session = TestData.newSession()
        val ctx = session.context
        val mayor = session.state.characters.values.first { it.role.name == "MAYOR" }
        val bodies = (1..300).map {
            val context = DialogueContextBuilder(ctx).sender(mayor, "Maire de Toulouse")
                .variable("city", "Toulouse").variable("amountText", "120 M€").build()
            ctx.messages.compose("territorial_transport_request", context).body
        }
        assertEquals(bodies.size, bodies.map { Hashing.messageSignature(it) }.toSet().size)
        assertFalse(bodies.any { it.contains("{") }, "Variable non remplacée")
        assertFalse(bodies.any { it.contains("[[") }, "Synonyme non remplacé")
    }

    @Test
    fun `l'historique des refus colore le message`() {
        val session = TestData.newSession()
        val ctx = session.context
        val mayor = session.state.characters.values.first { it.role.name == "MAYOR" }
        repeat(2) { ctx.memory.record(mayor.id, "city_transport_request", fr.president.engine.events.InteractionOutcome.REFUSED) }
        val texts = (1..40).map {
            ctx.messages.compose("territorial_transport_request", DialogueContextBuilder(ctx).sender(mayor, "Maire")
                .variable("city", "Nîmes").variable("amountText", "80 M€").build()).body
        }
        assertTrue(texts.any { it.contains("refus") }, "Au moins un message doit évoquer les refus passés")
    }
}
