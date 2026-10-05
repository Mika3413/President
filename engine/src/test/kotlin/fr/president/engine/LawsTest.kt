package fr.president.engine

import fr.president.engine.events.EventScope
import fr.president.engine.events.EventSystem
import fr.president.engine.events.ScopeRef
import fr.president.engine.government.PolicyStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LawsTest {
    @Test
    fun aLawGoesThroughParliament() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        s.state.government.parliamentSupport = 0.9
        val p = s.laws.propose("police_cameras", 1).getOrThrow()
        assertNotNull(s.laws.blocker("police_cameras", 1))
        clock.advanceWorldDays(40.0); s.advanceToNow()
        assertTrue(p.status == PolicyStatus.ADOPTED || p.status == PolicyStatus.REJECTED)
        if (p.status == PolicyStatus.ADOPTED) assertEquals(1, s.state.laws.values["police_cameras"])
    }

    @Test
    fun lawsShapeEventsIndicesAndRules() {
        val s = TestData.newSession()
        val terror = s.db.event("terror_attack")
        val scope = ScopeRef(EventScope.CITY, "paris")
        val before = EventSystem().probability(s.context, terror, scope)
        val liberty = s.laws.indices().liberty
        s.laws.enact("mass_surveillance", 1)
        assertTrue(EventSystem().probability(s.context, terror, scope) < before)
        assertTrue(s.laws.indices().liberty < liberty)
        s.laws.enact("term_length", 1)
        assertEquals(7.0, s.laws.flag("termYears"))
        s.laws.enact("article_49_3", 1)
        assertEquals(0.0, s.laws.flag("forcePass"))
    }

    @Test
    fun theConstitutionCanGoToReferendum() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        assertTrue(s.laws.referendum("citizen_referendum", 1).isSuccess)
        assertTrue(s.laws.referendum("cannabis", 1).isFailure, "pas de référendum pour une loi ordinaire")
        clock.advanceWorldDays(35.0); s.advanceToNow()
        assertTrue(s.state.laws.referendums.isEmpty())
        assertTrue(s.state.stats.journal.any { it.kind == "Loi" } || s.state.laws.values["citizen_referendum"] == 1 || s.state.laws.changedAt.containsKey("citizen_referendum"))
    }
}
