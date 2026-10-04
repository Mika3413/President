package fr.president.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaceChangeTest {
    @Test
    fun changingPaceKeepsThePastAndSpeedsUpTheFuture() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        clock.advanceHours(2.0)
        s.advanceToNow()
        val before = s.state.time
        s.changePace("express")
        assertEquals("express", s.state.meta.clock.paceId)
        assertEquals(before, s.state.time, "le passé du monde ne bouge pas")
        clock.advanceHours(1.0)
        s.advanceToNow()
        assertTrue(before.daysUntil(s.state.time) in 6.9..7.1, "1 heure réelle = 1 semaine au rythme express")
    }
}
