package fr.president.engine

import fr.president.engine.events.EventIntensity
import fr.president.engine.events.EventLauncher
import fr.president.engine.events.EventScope
import fr.president.engine.events.ScopeRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntensityTest {
    @Test
    fun theSameEventCanBeSmallOrHuge() {
        val levels = mutableSetOf<Double>()
        val headlines = mutableSetOf<String>()
        for (seed in 1L..40L) {
            val s = TestData.newSession(seed = seed)
            val instance = EventLauncher(s.context).launch(s.db.event("disaster_abroad"), ScopeRef(EventScope.FOREIGN_COUNTRY, "TUR"))
            levels += instance.params.getValue(EventIntensity.FACTOR)
            headlines += s.state.events.news.last().headline
            val message = s.state.inbox.messages.first { it.id == instance.messageId }
            assertTrue(message.body.startsWith("Ampleur estimée"))
        }
        assertTrue(levels.size >= 3, "ampleurs tirées : $levels")
        assertTrue(headlines.size >= 3, "titres : $headlines")
    }

    @Test
    fun costsInHintsFollowTheIntensity() {
        assertEquals("Coût : 300 M€ ; relation renforcée", EventIntensity.scaleCosts("Coût : 150 M€ ; relation renforcée", 2.0))
        assertEquals("Coût : 2,8 Md€", EventIntensity.scaleCosts("Coût : 1 Md€", 2.8))
        assertEquals("Coût : 5 M€", EventIntensity.scaleCosts("Coût : 10 M€", 0.5))
    }

    @Test
    fun summitsHaveNoIntensity() {
        val s = TestData.newSession()
        val summit = s.db.events.first { it.id in s.db.intensity!!.fixed && it.scope == EventScope.NATIONAL }
        val instance = EventLauncher(s.context).launch(summit, ScopeRef(EventScope.NATIONAL, null))
        assertTrue(EventIntensity.FACTOR !in instance.params)
    }
}
