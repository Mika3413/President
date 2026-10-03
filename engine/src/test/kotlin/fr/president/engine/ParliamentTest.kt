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

    @Test
    fun `une dissolution ratée impose une cohabitation, résolue par un Premier ministre de la majorité`() {
        val clock = TestData.FakeClock()
        val session = TestData.newSession(seed = 9L, clock = clock)
        clock.advanceWorldDays(400.0)
        session.advanceToNow()
        session.parliament.dissolve().getOrThrow()
        session.state.opinion.groups.replaceAll { _, _ -> GroupOpinion(0.15, 0.0) }
        session.state.opinion.honeymoon = 0.0
        clock.advanceWorldDays(22.0)
        session.advanceToNow()
        assertEquals(fr.president.engine.government.MajorityStatus.COHABITATION, session.parliament.majorityStatus())
        val before = session.parliament.supportTarget()
        val majority = session.parliament.largestFamily()
        val pm = session.government.candidates("pm").first { session.parliament.familyOf(it).id == majority.id }
        session.government.appoint("pm", pm.id).getOrThrow()
        assertTrue(session.parliament.supportTarget() > before + 0.1, "Soutien ${session.parliament.supportTarget()} vs $before")
    }

    @Test
    fun `les élections locales renouvellent les exécutifs départementaux`() {
        val clock = TestData.FakeClock()
        val session = TestData.newSession(seed = 10L, clock = clock)
        val next = session.state.localElections.next.getValue("departmental")
        val before = session.state.territory.departments.mapValues { it.value.presidentId }
        clock.advanceWorldDays(session.state.time.daysUntil(next) + 1)
        session.advanceToNow()
        val result = session.state.localElections.results.first { it.kindId == "departmental" }
        assertEquals(session.state.territory.departments.size, result.contested)
        val changed = session.state.territory.departments.count { (code, d) -> d.presidentId != before[code] }
        assertTrue(changed > 0, "Des exécutifs changent de titulaire")
        assertTrue(session.state.localElections.next.getValue("departmental") > next)
    }

    @Test
    fun `le Sénat est renouvelé et les élections européennes ont lieu`() {
        val clock = TestData.FakeClock()
        val session = TestData.newSession(seed = 11L, clock = clock)
        assertEquals(348, session.state.parliament.senateSeats.values.sum())
        val renewal = session.state.parliament.nextSenateRenewal!!
        clock.advanceWorldDays(session.state.time.daysUntil(renewal) + 1)
        session.advanceToNow()
        assertEquals(1, session.state.parliament.senateResults.size)
        assertEquals(348, session.state.parliament.senateSeats.values.sum())
        assertEquals(1, session.state.parliament.europeanResults.size, "Les européennes (juin 2029) précèdent les sénatoriales")
        assertEquals(81, session.state.parliament.europeanResults.first().seats.values.sum())
    }

    @Test
    fun `un Sénat hostile retarde la mise en oeuvre d'une réforme`() {
        val clock = TestData.FakeClock()
        val session = TestData.newSession(seed = 12L, clock = clock)
        // Sénat entièrement acquis à l'opposition la plus éloignée.
        val far = session.parliament.families.minBy { session.parliament.familySupport(it) }
        session.state.parliament.senateSeats.clear()
        session.state.parliament.senateSeats[far.id] = 348
        session.state.government.parliamentSupport = 0.95
        session.policy.proposeReform("hospital_plan").getOrThrow()
        clock.advanceWorldDays(35.0)
        session.advanceToNow()
        assertTrue("hospital_plan" in session.state.policy.adoptedReforms)
        val delayed = session.state.effects.filter { it.source == "hospital_plan" }
        assertTrue(delayed.isNotEmpty() && delayed.all { it.startsAt > session.state.time }, "Effets retardés par la navette")
    }
}
