package fr.president.engine

import fr.president.engine.government.PolicyKind
import fr.president.engine.politics.MovementPhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnrestTest {
    @Test
    fun aContestedReformBringsPeopleIntoTheStreets() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        s.unrest.onPolicy(PolicyKind.REFORM, "pension_age_65", 0)
        val m = s.state.unrest.movements.single()
        assertEquals("pensions", m.cause)
        clock.advanceWorldDays(10.0); s.advanceToNow()
        assertTrue(m.crowd > 50, "foule : ${m.crowd}")
        assertTrue(s.unrest.respond(m.id, "dialogue").isSuccess)
        assertTrue(s.unrest.respond(m.id, "dialogue").isFailure, "délai")
        assertTrue(s.unrest.respond(m.id, "concede").isSuccess)
        clock.advanceWorldDays(150.0); s.advanceToNow()
        assertTrue(s.state.unrest.movements.none { it.id == m.id }, "le mouvement finit par s'essouffler")
        assertTrue(s.state.unrest.past.isNotEmpty())
    }

    @Test
    fun repressionRadicalizesUntilRiots() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        s.unrest.spark(s.unrest.cause("anger")!!, 1.5)
        val m = s.state.unrest.movements.single()
        m.crowd = 500.0; m.radicalization = 0.45; m.momentum = 2.0
        s.state.opinion.groups.values.forEach { it.approval = 0.2 }
        repeat(3) { i ->
            m.lastAction.clear()
            s.unrest.respond(m.id, "crackdown")
            clock.advanceWorldDays(5.0); s.advanceToNow()
        }
        assertTrue(m.radicalization > 0.5, "radicalité ${m.radicalization}")
        assertTrue(m.phase >= MovementPhase.RIOTS)
    }

    @Test
    fun aLongInsurrectionCanEndThePresidency() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        s.unrest.spark(s.unrest.cause("anger")!!, 1.5)
        val m = s.state.unrest.movements.single()
        s.state.opinion.groups.values.forEach { it.approval = 0.05 }
        s.state.opinion.nationalApproval = 0.1
        var days = 0
        while (s.state.player.gameOver == null && days < 400) {
            m.crowd = 1500.0; m.radicalization = 0.95; m.momentum = 3.0
            s.state.opinion.nationalApproval = 0.1
            clock.advanceWorldDays(5.0); s.advanceToNow(); days += 5
        }
        assertNotNull(s.state.player.gameOver)
    }

    @Test
    fun aDisloyalArmyPlotsAndCanBePurged() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        s.unrest.state.armyLoyalty = 0.2
        s.state.unrest.conspiracy = 0.5
        s.state.unrest.plotKnown = true
        assertTrue(s.unrest.armyAction("purge").isSuccess)
        assertEquals(0.0, s.state.unrest.conspiracy)
        assertTrue(s.unrest.armyAction("pay").isSuccess)
        assertTrue(s.unrest.armyAction("pay").isFailure)
        // Un coup raté soude le pays derrière ses institutions.
        s.state.unrest.armyLoyalty = 0.99
        s.state.opinion.nationalApproval = 0.6
        s.unrest.coupAttempt()
        assertNull(s.state.player.gameOver)
        assertEquals(1, s.state.unrest.coups)
    }
}
