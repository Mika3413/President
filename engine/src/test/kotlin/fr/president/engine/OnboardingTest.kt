package fr.president.engine

import fr.president.engine.presidency.OnboardingService
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OnboardingTest {

    @Test
    fun `le premier matin est une vraie crise dont on voit les suites`() {
        val s = TestData.newSession()
        val o = OnboardingService(s.context)
        assertTrue(o.firstMorningDue())
        val m = o.startFirstMorning()
        assertNotNull(m)
        assertFalse(o.firstMorningDue())
        assertTrue(m.text.contains("préfet"))
        val message = s.state.inbox.messages.first { it.id == m.messageId }
        assertTrue(message.awaitingAnswer)
        assertNull(o.outcome(), "Pas de bilan avant la décision")
        s.answer(message.id, message.options.first().id)
        val outcome = o.outcome()
        assertNotNull(outcome)
        assertTrue(outcome.lines.isNotEmpty())
        assertTrue(outcome.lines.none { it.text.contains(" 0 M€") }, "Le coût réel doit apparaître : ${outcome.lines}")
        o.outcomeSeen()
        assertNull(o.outcome())
    }

    @Test
    fun `les écrans s'ouvrent au fil du mandat`() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        val o = OnboardingService(s.context)
        assertFalse(o.unlocked("TRADE"))
        assertFalse(o.unlocked("PRESS"))
        assertTrue(o.unlocked("ECONOMY"), "Les écrans de base sont toujours là")
        clock.advanceWorldDays(35.0); s.advanceToNow()
        assertTrue(o.unlocked("PRESS") && o.unlocked("TRADE") && o.unlocked("LAWS"))
        assertTrue(s.state.notifications.feed.any { it.title.startsWith("Nouvel écran") })
        o.showAll()
        assertTrue(o.locked().isEmpty())
    }
}
