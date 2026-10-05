package fr.president.engine

import fr.president.engine.diplomacy.UnVote
import fr.president.engine.military.Geopolitics
import fr.president.engine.military.WarService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnTest {
    @Test
    fun theCouncilHasFifteenMembersAndRenewsEachYear() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        assertEquals(15, s.un.members().size)
        val before = s.un.members().toSet()
        clock.advanceWorldDays(120.0); s.advanceToNow()
        assertEquals(15, s.un.members().size)
        assertTrue(s.un.members().toSet() != before, "cinq sièges changent au 1er janvier")
        assertTrue(s.state.un.results.isNotEmpty() || s.state.un.drafts.isNotEmpty(), "des projets arrivent")
    }

    @Test
    fun aRussianVetoBlocksTheCondemnationOfRussia() {
        val s = TestData.newSession()
        val geo = Geopolitics(s.context)
        if (!geo.atWar("RUS", "UKR")) WarService(s.context).declare("RUS", "UKR", "test")
        val p = s.un.proposals().first { it.template.kind == "condemn" && it.aggressor == "RUS" }
        assertTrue(s.un.propose(p.template.id, p.aggressor, p.victim).isSuccess)
        assertTrue(s.un.propose(p.template.id, p.aggressor, p.victim).isFailure, "un projet à la fois")
        val d = s.state.un.drafts.first { it.sponsor == "FRA" }
        val o = s.un.outlook(d)
        assertTrue("RUS" in o.vetoBy)
        assertFalse(o.adopted)
        s.un.vote(d)
        assertFalse(s.state.un.results.last().adopted)
    }

    @Test
    fun lobbyingWinsVotesOnAThematicText() {
        val s = TestData.newSession()
        val p = s.un.proposals().first { it.template.id == "haiti" }
        assertTrue(s.un.propose(p.template.id, null, null).isSuccess)
        val d = s.state.un.drafts.first { it.sponsor == "FRA" }
        val before = s.un.outlook(d).yes
        s.un.members().filter { s.un.forecast(it, d) != UnVote.YES }.take(2).forEach { s.un.lobby(d.id, it) }
        assertTrue(s.un.outlook(d).yes >= before)
        assertTrue(s.un.amend(d.id).isSuccess)
        s.un.vote(d)
        assertEquals(1, s.state.un.results.size)
    }
}
