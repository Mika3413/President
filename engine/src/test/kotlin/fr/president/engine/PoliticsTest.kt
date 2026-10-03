package fr.president.engine

import fr.president.engine.government.PolicyStatus
import fr.president.engine.government.Priority
import fr.president.engine.opinion.GroupOpinion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PoliticsTest {
    @Test
    fun `une hausse de TVA votée augmente les recettes`() {
        val clock = TestData.FakeClock()
        val session = TestData.newSession(seed = 21L, clock = clock)
        session.state.government.parliamentSupport = 0.9
        val vat = session.state.playerCountry.economy.budget!!.revenues.getValue("vat")
        val before = vat.amount
        val proposal = session.policy.proposeTaxRate("vat", 21.0)
        clock.advanceWorldDays(15.0)
        session.advanceToNow()
        assertEquals(PolicyStatus.ADOPTED, proposal.status)
        assertTrue(vat.amount > before, "Recettes TVA ${vat.amount} vs $before")
    }

    @Test
    fun `le nombre de priorités hautes est limité`() {
        val session = TestData.newSession()
        val ministries = listOf("health", "education", "interior", "justice")
        val results = ministries.map { session.government.setPriority(it, Priority.HIGH) }
        assertTrue(results.take(3).all { it.isSuccess })
        assertTrue(results.last().isFailure)
    }

    @Test
    fun `nommer un ministre remplace le titulaire`() {
        val session = TestData.newSession()
        val candidate = session.government.candidates("health").first()
        session.government.appoint("health", candidate.id).getOrThrow()
        assertEquals(candidate.id, session.state.government.ministers["health"])
    }

    @Test
    fun `un président très impopulaire perd l'élection`() {
        val clock = TestData.FakeClock()
        val session = TestData.newSession(seed = 8L, clock = clock)
        val target = session.state.elections.nextElection
        val days = session.state.time.daysUntil(target)
        clock.advanceWorldDays(days - 3)
        session.advanceToNow()
        assertNull(session.state.player.gameOver, "Pas de fin de partie avant le scrutin")
        // On fige une opinion désastreuse à l'approche du scrutin.
        session.state.opinion.groups.replaceAll { _, _ -> GroupOpinion(0.1, -0.1) }
        session.state.opinion.honeymoon = 0.0
        clock.advanceWorldDays(20.0)
        session.advanceToNow()
        assertNotNull(session.state.player.gameOver)
        assertTrue(session.state.elections.results.isNotEmpty())
    }

    @Test
    fun `un président très populaire est réélu`() {
        val clock = TestData.FakeClock()
        val session = TestData.newSession(seed = 8L, clock = clock)
        val target = session.state.elections.nextElection
        clock.advanceWorldDays(session.state.time.daysUntil(target) - 3)
        session.advanceToNow()
        session.state.opinion.groups.replaceAll { _, _ -> GroupOpinion(0.85, 0.1) }
        clock.advanceWorldDays(20.0)
        session.advanceToNow()
        assertNull(session.state.player.gameOver, session.state.player.gameOver?.reason)
        assertEquals(2, session.state.player.termNumber)
    }
}
