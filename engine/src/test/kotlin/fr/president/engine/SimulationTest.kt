package fr.president.engine

import fr.president.engine.data.GameJson
import fr.president.engine.world.WorldState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimulationTest {

    @Test
    fun `deux ans de simulation restent dans des bornes plausibles`() {
        val clock = TestData.FakeClock()
        val session = TestData.newSession(seed = 7L, clock = clock)
        repeat(24) {
            clock.advanceWorldDays(30.0)
            session.advanceToNow()
            val e = session.state.playerCountry.economy
            assertTrue(e.unemployment in 0.02..0.2, "Chômage ${e.unemployment}")
            assertTrue(e.inflation in -0.03..0.1, "Inflation ${e.inflation}")
            assertTrue(e.realGrowth in -0.08..0.08, "Croissance ${e.realGrowth}")
            assertTrue(session.state.opinion.nationalApproval in 0.0..1.0)
        }
        assertTrue(session.state.events.news.isNotEmpty(), "Des événements doivent survenir en deux ans")
        assertTrue(session.state.elections.latestPoll != null)
    }

    @Test
    fun `le rattrapage en une fois équivaut à une progression continue`() {
        val clockA = TestData.FakeClock()
        val clockB = TestData.FakeClock()
        val a = TestData.newSession(seed = 99L, clock = clockA)
        val b = TestData.newSession(seed = 99L, clock = clockB)
        // A : le joueur est absent 120 heures réelles d'un coup.
        clockA.advanceHours(120.0)
        a.advanceToNow()
        // B : le joueur ouvre le jeu régulièrement, par petits pas irréguliers.
        var elapsed = 0.0
        val steps = listOf(0.3, 1.7, 5.0, 0.01, 13.0, 40.0, 2.5)
        var i = 0
        while (elapsed < 120.0) {
            val step = minOf(steps[i++ % steps.size], 120.0 - elapsed)
            clockB.advanceHours(step)
            b.advanceToNow()
            elapsed += step
        }
        assertEquals(a.state.time, b.state.time)
        assertEquals(serialize(a.state), serialize(b.state))
    }

    @Test
    fun `le temps du monde dépend du rythme choisi`() {
        val clock = TestData.FakeClock()
        val fast = TestData.newSession(clock = clock, pace = "rapide")
        val start = fast.state.time
        clock.advanceHours(1.0)
        fast.advanceToNow()
        assertEquals(48.0, start.daysUntil(fast.state.time) * 24, 0.01)
    }

    private fun serialize(state: WorldState) = GameJson.save.encodeToString(WorldState.serializer(), state)
}
