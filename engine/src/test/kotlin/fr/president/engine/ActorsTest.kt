package fr.president.engine

import kotlin.test.Test
import kotlin.test.assertTrue

class ActorsTest {
    @Test
    fun actorsReactToLawsAndConcessions() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        val def = s.context.playerData.actors!!.actors.first { it.id == "muslim_council" }
        val before = s.actors.target(def)
        s.laws.enact("secularism", 1)
        assertTrue(s.actors.target(def) < before)

        val boss = s.context.playerData.actors!!.actors.first { it.id == "employers_big" }
        val bossBefore = s.actors.target(boss)
        assertTrue(s.actors.grant("union_militant", "wages").isSuccess)
        assertTrue(s.actors.target(boss) < bossBefore, "le patronat n'aime pas")
        assertTrue(s.actors.grant("union_militant", "wages").isFailure)
        assertTrue(s.actors.meet("church").isSuccess)
        clock.advanceWorldDays(365.0); s.advanceToNow()
        assertTrue(s.actors.rows().all { it.satisfaction in 0.0..1.0 })
    }
}
