package fr.president.engine

import fr.president.engine.presidency.MajorEventsService
import fr.president.engine.presidency.MomentKind
import kotlin.test.Test
import kotlin.test.assertTrue

class MajorEventsTest {

    @Test
    fun `le calendrier sportif se joue et les JO 2030 se préparent`() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(seed = 9L, clock = clock)
        val major = MajorEventsService(s.context)
        // Jusqu'au dossier du budget des JO 2030 (mars 2027).
        while (s.context.now < major.date("2027-03-02")) { clock.advanceWorldDays(10.0); s.advanceToNow() }
        val dossier = s.moments.offers().firstOrNull { it.kind == MomentKind.DOSSIER && it.ref == "jo2030_budget" }
        assertTrue(dossier != null, "Le dossier du budget des JO doit être ouvert")
        val before = major.readiness()
        assertTrue(s.moments.start(MomentKind.DOSSIER, "jo2030_budget").isSuccess)
        assertTrue(s.moments.choose(0).getOrThrow() != null)
        assertTrue(major.readiness() > before)
        // Coupe du monde de rugby 2027 : jouée jusqu'au bout.
        while (s.context.now < major.date("2027-11-05")) { clock.advanceWorldDays(10.0); s.advanceToNow() }
        val rugby = s.state.majorEvents.tournaments["rugby_wc_2027"]
        assertTrue(rugby != null && rugby.done, "La compétition doit être terminée")
        assertTrue(s.state.events.news.any { it.headline.contains("Coupe du monde de rugby") })
    }

    @Test
    fun `un appel à l'aide après une catastrophe devient un arbitrage`() {
        val s = TestData.newSession()
        s.state.majorEvents.aidRequests["megaquake|JPN"] = s.context.now.plusDays(10)
        val offer = s.moments.offers().first { it.kind == MomentKind.DOSSIER && it.ref.startsWith("aid|") }
        assertTrue(offer.title.contains("Japon"))
        val memories = s.state.diplomacy.relation("JPN", "FRA").memories.size
        assertTrue(s.moments.start(MomentKind.DOSSIER, offer.ref).isSuccess)
        s.moments.choose(0)
        assertTrue(s.state.diplomacy.relation("JPN", "FRA").memories.size > memories)
        assertTrue(s.state.majorEvents.aidRequests.isEmpty())
    }
}
