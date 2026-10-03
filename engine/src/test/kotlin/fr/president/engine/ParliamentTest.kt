package fr.president.engine

import fr.president.engine.government.PolicyStatus
import fr.president.engine.opinion.GroupOpinion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParliamentTest {
    @Test
    fun `une nouvelle partie constitue une Assemblée favorable au président`() {
        val session = TestData.newSession(seed = 3L)
        val parliament = session.state.parliament
        assertEquals(577, parliament.seats.values.sum())
        val presidentFamily = session.parliament.presidentFamily().id
        assertEquals(parliament.seats.maxBy { it.value }.key, presidentFamily, "Le parti présidentiel arrive en tête")
        assertTrue(session.state.government.parliamentSupport >= 0.5, "Soutien ${session.state.government.parliamentSupport}")
        assertNotNull(parliament.nextLegislative)
        assertEquals(1, parliament.legislativeResults.size)
    }

    @Test
    fun `la dissolution est interdite la première année puis provoque des législatives`() {
        val clock = TestData.FakeClock()
        val session = TestData.newSession(seed = 4L, clock = clock)
        assertNotNull(session.parliament.dissolutionBlocker())
        assertTrue(session.parliament.dissolve().isFailure)
        clock.advanceWorldDays(400.0)
        session.advanceToNow()
        session.parliament.dissolve().getOrThrow()
        assertTrue(session.state.parliament.dissolutionPending)
        clock.advanceWorldDays(25.0)
        session.advanceToNow()
        val parliament = session.state.parliament
        assertTrue(!parliament.dissolutionPending)
        assertEquals(2, parliament.legislativeResults.size)
        assertTrue(parliament.legislativeResults.last().afterDissolution)
        assertEquals(577, parliament.seats.values.sum())
    }

    @Test
    fun `un passage en force sans majorité peut faire tomber le gouvernement`() {
        val clock = TestData.FakeClock()
        val session = TestData.newSession(seed = 5L, clock = clock)
        val proposal = session.policy.proposeTaxRate("vat", 22.0)
        session.state.government.parliamentSupport = 0.2
        session.policy.forcePass(proposal.id)
        assertEquals(PolicyStatus.PENDING_CENSURE, proposal.status)
        assertTrue(session.state.parliament.censurePending)
        clock.advanceWorldDays(5.0)
        session.advanceToNow()
        assertEquals(PolicyStatus.REJECTED, proposal.status)
        assertNull(session.state.government.primeMinisterId, "Le Premier ministre est renversé")
        assertEquals(1, session.state.parliament.governmentsFallen)
        // Un nouveau Premier ministre peut être nommé.
        val candidate = session.government.candidates("pm").first()
        session.government.appoint("pm", candidate.id).getOrThrow()
        assertEquals(candidate.id, session.state.government.primeMinisterId)
    }

    @Test
    fun `un passage en force avec une majorité solide est appliqué sans censure`() {
        val session = TestData.newSession(seed = 6L)
        val proposal = session.policy.proposeTaxRate("vat", 21.0)
        session.state.government.parliamentSupport = 0.8
        session.policy.forcePass(proposal.id)
        assertEquals(PolicyStatus.FORCED, proposal.status)
        assertTrue(!session.state.parliament.censurePending)
    }

    @Test
    fun `un référendum gagné adopte la réforme, un référendum perdu la bloque`() {
        val clock = TestData.FakeClock()
        val session = TestData.newSession(seed = 7L, clock = clock)
        session.parliament.callReferendum("hospital_plan").getOrThrow()
        assertNotNull(session.parliament.referendumBlocker("teachers_plan"), "Un seul référendum à la fois")
        session.state.opinion.groups.replaceAll { _, _ -> GroupOpinion(0.9, 0.0) }
        clock.advanceWorldDays(31.0)
        session.advanceToNow()
        assertTrue("hospital_plan" in session.state.policy.adoptedReforms)

        clock.advanceWorldDays(370.0)
        session.advanceToNow()
        session.parliament.callReferendum("pension_age_65").getOrThrow()
        session.state.opinion.groups.replaceAll { _, _ -> GroupOpinion(0.1, 0.0) }
        clock.advanceWorldDays(31.0)
        session.advanceToNow()
        assertTrue("pension_age_65" !in session.state.policy.adoptedReforms)
        assertNotNull(session.policy.reformBlocker("pension_age_65"), "Réforme verrouillée après un « non »")
        assertEquals(2, session.state.parliament.referendumResults.size)
    }
}
