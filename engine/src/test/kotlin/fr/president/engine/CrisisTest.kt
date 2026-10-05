package fr.president.engine

import fr.president.engine.events.EventLauncher
import fr.president.engine.events.EventScope
import fr.president.engine.events.EventSystem
import fr.president.engine.events.ScopeRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CrisisTest {
    @Test
    fun lockdownRunsThenEndsAndCannotBeRestartedAtOnce() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        val output = s.state.playerCountry.economy.gdpBillions
        assertTrue(s.measures.activate("lockdown").isSuccess)
        assertNotNull(s.state.measures.active.firstOrNull { it.id == "lockdown" })
        assertTrue(s.measures.activate("lockdown").isFailure, "déjà en vigueur")
        assertTrue(s.state.stats.journal.any { it.kind == "Mesure" })
        clock.advanceWorldDays(31.0)
        s.advanceToNow()
        assertNull(s.state.measures.active.firstOrNull { it.id == "lockdown" }, "fin au bout de 30 jours")
        val view = s.measures.view(s.measures.definitions.first { it.id == "lockdown" })
        assertNotNull(view.blocker, "délai avant de reconfiner")
        assertTrue(s.state.playerCountry.economy.gdpBillions < output * 1.01)
    }

    @Test
    fun liftingEndsAMeasureEarly() {
        val s = TestData.newSession()
        s.measures.activate("curfew").getOrThrow()
        assertTrue(s.measures.lift("curfew").isSuccess)
        assertTrue(s.state.measures.active.isEmpty())
    }

    @Test
    fun preventionLowersTheOddsOfAFire() {
        val s = TestData.newSession()
        val fire = s.db.event("wildfire")
        val dept = ScopeRef(EventScope.DEPARTMENT, "13")
        val before = EventSystem().probability(s.context, fire, dept)
        s.measures.activate("fire_prevention").getOrThrow()
        val after = EventSystem().probability(s.context, fire, dept)
        assertTrue(after <= before * 0.61 + 1e-12, "$before -> $after")
    }

    @Test
    fun localMeasuresNeedADepartmentAndStayThere() {
        val s = TestData.newSession()
        assertTrue(s.measures.activate("evacuation").isFailure, "département requis")
        s.measures.activate("evacuation", "13").getOrThrow()
        assertEquals("13", s.state.measures.active.single().department)
        assertTrue(s.measures.activate("evacuation", "83").isSuccess, "autre département possible")
    }

    @Test
    fun risksAreReadableAndMeasuresAreSuggested() {
        val s = TestData.newSession()
        val risks = s.risks.risks()
        assertEquals(s.context.playerData.measures!!.risks.size, risks.size)
        risks.forEach { r ->
            assertTrue(r.probability in 0.0..1.0)
            assertTrue(r.measures.isNotEmpty(), "aucune mesure contre ${r.def.id}")
        }
        val fireBefore = s.risks.risk("fire")!!.probability
        s.measures.activate("fire_prevention").getOrThrow()
        val fire = s.risks.risk("fire")!!
        assertTrue(fire.probability <= fireBefore)
        assertTrue(fire.activeMeasures.any { it.id == "fire_prevention" })
    }

    @Test
    fun everyEventOffersManyChoicesAndCrisisMeasures() {
        val s = TestData.newSession()
        s.db.events.forEach { e ->
            assertNotNull(e.message, "${e.id} : aucun choix proposé")
            assertTrue(e.message!!.options.size >= 4, "${e.id} : ${e.message!!.options.size} options")
            assertTrue(e.message!!.options.map { it.id }.toSet().size == e.message!!.options.size, "${e.id} : options en double")
        }
        val measures = s.context.playerData.measures!!.measures.map { it.id }.toSet()
        s.db.events.flatMap { it.measures }.forEach { assertTrue(it in measures, "mesure inconnue $it") }

        val instance = EventLauncher(s.context).launch(s.db.event("wildfire"), ScopeRef(EventScope.DEPARTMENT, "83"))
        val message = s.state.inbox.messages.first { it.id == instance.messageId }
        assertTrue(message.options.any { it.id == "x_army" })
        assertTrue("canadair" in message.measures && "orsec" in message.measures)
        assertEquals("83", message.measureDepartment)
        val readiness = s.state.military.units.values.filter { it.countryId == "FRA" }.sumOf { it.readiness }
        s.answer(message.id, "x_army")
        assertTrue(s.state.military.units.values.filter { it.countryId == "FRA" }.sumOf { it.readiness } < readiness)
    }

    @Test
    fun restrictionsWearOutAndFaceParliamentAndJudges() {
        var votes = 0
        for (seed in 1L..6L) {
            val clock = TestData.FakeClock()
            val s = TestData.newSession(seed = seed, clock = clock)
            s.measures.activate("curfew", days = 60).getOrThrow()
            val curfew = s.state.measures.active.single()
            clock.advanceWorldDays(11.0); s.advanceToNow()
            assertTrue(curfew.compliance < 1.0, "la lassitude s'installe")
            // Le juge peut suspendre la mesure avant le vote.
            if (s.state.measures.active.none { it.id == "curfew" }) {
                assertTrue(s.state.stats.journal.any { it.kind == "Mesure" && "Conseil" in it.text }); continue
            }
            val view = s.measures.view(s.measures.definitions.first { it.id == "curfew" })
            assertTrue(view.status.any { it.first.startsWith("Vote de prorogation") })
            clock.advanceWorldDays(3.0); s.advanceToNow()
            // Au 12e jour : prorogée par le Parlement, ou levée (rejet, recours).
            val still = s.state.measures.active.firstOrNull { it.id == "curfew" }
            if (still != null) { assertTrue(still.extended); votes++ }
            else assertTrue(s.state.stats.journal.any { it.kind == "Mesure" && ("Parlement" in it.text || "Conseil" in it.text) })
        }
        assertTrue(votes > 0, "le Parlement proroge parfois")
    }

    @Test
    fun aWornOutMeasureProtectsLess() {
        val s = TestData.newSession()
        val epidemic = s.db.event("epidemic")
        val scope = ScopeRef(EventScope.NATIONAL, null)
        s.measures.activate("lockdown").getOrThrow()
        val fresh = EventSystem().probability(s.context, epidemic, scope)
        s.state.measures.active.single().compliance = 0.3
        assertTrue(EventSystem().probability(s.context, epidemic, scope) > fresh)
    }
}
