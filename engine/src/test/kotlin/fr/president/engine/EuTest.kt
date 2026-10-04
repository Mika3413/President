package fr.president.engine

import fr.president.engine.diplomacy.EuProcedure
import fr.president.engine.diplomacy.EuVote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EuTest {
    @Test
    fun theCommissionProposesAndTheCouncilVotes() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        clock.advanceWorldDays(25.0); s.advanceToNow()
        val (proc, _) = assertNotNull(s.eu.current(), "un texte est sur la table")
        s.eu.setPosition(EuVote.YES)
        val partner = s.eu.outlook()!!.forecasts.first().country
        assertTrue(s.eu.lobby(partner).isSuccess)
        assertTrue(s.eu.lobby(partner).isFailure, "une seule fois par texte")
        assertTrue(s.eu.amend().isSuccess)
        clock.advanceWorldDays(proc.voteAt.daysUntil(s.state.time).let { -it } + 1); s.advanceToNow()
        val r = s.state.eu.results.first()
        assertEquals(EuVote.YES, r.france)
        assertTrue(r.votes.size >= 10)
        assertTrue(s.eu.alignment().isNotEmpty())
    }

    @Test
    fun aFrenchVetoBlocksAUnanimityText() {
        val s = TestData.newSession()
        s.state.eu.current = EuProcedure("common_debt", s.state.time, s.state.time.plusDays(30.0), EuVote.NO)
        s.eu.vote()
        val r = s.state.eu.results.single()
        assertFalse(r.adopted)
        assertEquals("FRA", r.vetoBy)
    }
}
