package fr.president.engine

import fr.president.engine.government.AffairKind
import fr.president.engine.government.CabinetService
import fr.president.engine.inbox.MessageOrigin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CabinetTest {
    @Test
    fun ministersActOnTheirOwnOverTwoYears() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        clock.advanceWorldDays(730.0)
        s.advanceToNow()
        val cabinet = s.state.inbox.messages.filter { it.origin == MessageOrigin.CABINET }
        assertTrue(cabinet.size >= 3, "affaires du gouvernement en deux ans : ${cabinet.size}")
        assertTrue(cabinet.filter { it.deadline!! < s.state.time }.all { it.chosenOptionId != null }, "toutes tranchées, au besoin par défaut")
    }

    @Test
    fun backingAnInitiativePleasesTheMinister() {
        val s = TestData.newSession()
        val id = s.state.government.ministers.getValue("health")
        val m = s.state.characters.getValue(id)
        val before = m.loyalty
        val message = CabinetService(s.context).raise(AffairKind.INITIATIVE, m, ref = "nurse_practice")
        s.answer(message.id, "back")
        assertTrue(m.loyalty > before)
        assertTrue(s.state.government.affairs.isEmpty())
    }

    @Test
    fun disputesAndThreatsHaveConsequences() {
        val s = TestData.newSession()
        val gov = s.state.government
        val eco = s.state.characters.getValue(gov.ministers.getValue("economy"))
        val edu = s.state.characters.getValue(gov.ministers.getValue("education"))
        val ecoBefore = eco.loyalty
        val dispute = CabinetService(s.context).raise(AffairKind.DISPUTE, eco, other = edu, ref = "budget_cuts")
        assertEquals(4, dispute.options.size)
        s.answer(dispute.id, "b")
        assertTrue(eco.loyalty < ecoBefore, "le ministre désavoué est vexé")

        val threat = CabinetService(s.context).raise(AffairKind.THREAT, edu)
        s.answer(threat.id, "accept")
        assertFalse(edu.active)
        assertTrue("education" !in gov.ministers)
    }
}
