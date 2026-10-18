package fr.president.engine

import fr.president.engine.government.AffairKind
import fr.president.engine.government.CabinetService
import fr.president.engine.politics.CharacterLife
import fr.president.engine.politics.Traits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CharacterLifeTest {

    private fun minister(s: fr.president.engine.session.GameSession) =
        s.state.government.ministers.values.filter { it != s.state.government.primeMinisterId }.map { s.state.characters.getValue(it) }.first()

    @Test
    fun `un ministre se souvient de vous et vous le rappelle`() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        val m = minister(s)
        val cabinet = CabinetService(s.context)
        val first = cabinet.raise(AffairKind.THREAT, m)
        s.answer(first.id, "concede")
        assertTrue(m.memories.any { it.text.contains("moyens") && it.weight > 0 })
        assertTrue(CharacterLife.expressionOf(m) >= 0)
        clock.advanceWorldDays(10.0); s.advanceToNow()
        val second = cabinet.raise(AffairKind.THREAT, m)
        assertTrue(second.body.contains("Je n'oublie pas"), second.body)
        assertTrue(CharacterLife(s.context).sheet(m, minister = true).any { it.first.startsWith("Se souvient") })
    }

    @Test
    fun `un ministre ambitieux devient un rival puis on peut le limoger`() {
        val s = TestData.newSession(seed = 5L)
        val m = minister(s)
        m.traits[Traits.EGO] = 1.0; m.traits[Traits.CHARISMA] = 1.0; m.popularity = 0.9
        m.relationWithPlayer = 0.2
        s.state.opinion.nationalApproval = 0.3
        val life = CharacterLife(s.context)
        repeat(40) { if (s.state.government.affairs.none { it.kind == AffairKind.RIVALRY }) { s.state.opinion.nationalApproval = 0.3; life.monthly() } }
        val affair = s.state.government.affairs.firstOrNull { it.kind == AffairKind.RIVALRY && it.ministerId == m.id }
        assertTrue(affair != null || !m.active, "Le rival doit se manifester (rivalité ${m.rivalry})")
        if (affair != null) {
            val message = s.state.inbox.messages.first { it.originId == affair.id }
            s.answer(message.id, "fire")
            assertFalse(m.active && s.state.government.ministers.containsValue(m.id))
        }
    }

    @Test
    fun `une demande rejetée laisse une rancune visible`() {
        val s = TestData.newSession()
        val prefect = s.state.characters.values.first { it.role == fr.president.engine.politics.CharacterRole.PREFECT }
        repeat(3) { s.context.memory.record(prefect.id, "flood", fr.president.engine.events.InteractionOutcome.REFUSED, "Inondations") }
        assertEquals(3, prefect.memories.size)
        assertTrue(CharacterLife.expressionOf(prefect) < 0)
    }
}
