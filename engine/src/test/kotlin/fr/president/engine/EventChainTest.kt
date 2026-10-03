package fr.president.engine

import fr.president.engine.events.EventScope
import fr.president.engine.events.ScopeRef
import fr.president.engine.events.EventLauncher
import kotlin.test.Test
import kotlin.test.assertTrue

class EventChainTest {
    @Test
    fun `une décision peut provoquer la suite d'une histoire`() {
        val clock = TestData.FakeClock()
        val session = TestData.newSession(seed = 41L, clock = clock)
        val ctx = session.context
        var launched = 0
        // Plusieurs essais : la suite est probable (70 %) mais pas certaine.
        repeat(6) {
            val def = ctx.db.event("rail_strike")
            val instance = EventLauncher(ctx).launch(def, ScopeRef(EventScope.NATIONAL, null))
            val message = session.state.inbox.messages.first { it.originId == instance.id }
            session.answer(message.id, "firm")
            if (session.state.scheduler.actions.any { it is fr.president.engine.simulation.ScheduledAction.EventLaunch && it.definitionId == "strike_spreads" }) launched++
            session.state.scheduler.actions.removeAll { it is fr.president.engine.simulation.ScheduledAction.EventLaunch && it.definitionId == "strike_spreads" }
        }
        assertTrue(launched >= 2, "Suites programmées : $launched / 6")
    }
}
