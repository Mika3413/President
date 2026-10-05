package fr.president.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalActionsTest {
    @Test
    fun visitHasCooldowns() {
        val s = TestData.newSession()
        val before = s.state.territory.departments.getValue("34").localShock
        assertTrue(s.localActions.perform("34", "visit").isSuccess)
        assertTrue(s.state.territory.departments.getValue("34").localShock > before)
        // Même département : délai local ; autre département : délai national d'une semaine.
        assertNotNull(s.localActions.actionsFor("34").first { it.def.id == "visit" }.blocker)
        assertNotNull(s.localActions.actionsFor("13").first { it.def.id == "visit" }.blocker)
        assertTrue(s.localActions.perform("13", "visit").isFailure)
    }

    @Test
    fun hospitalIsBuiltThenImprovesHealth() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        val health = s.state.territory.departments.getValue("34").healthAccess
        val view = s.localActions.actionsFor("34").first { it.def.id == "hospital" }
        assertNull(view.blocker)
        assertTrue(view.effects.any { it.text.startsWith("Santé +15") && it.good })
        assertTrue(s.localActions.perform("34", "hospital").isSuccess)
        assertEquals(1, s.state.projects.count { it.kind == "local:hospital" })
        assertNotNull(s.localActions.actionsFor("34").first { it.def.id == "hospital" }.blocker)
        clock.advanceWorldDays(560.0)
        s.advanceToNow()
        assertTrue(s.state.territory.departments.getValue("34").healthAccess > health + 0.1)
    }

    @Test
    fun actionsFitTheTerritory() {
        val s = TestData.newSession()
        fun ids(code: String) = s.localActions.actionsFor(code).map { it.def.id }.toSet()
        assertTrue("coast_protection" in ids("29") && "coast_protection" !in ids("15"), "le littoral seulement sur les côtes")
        assertTrue("ski_transition" in ids("73") && "ski_transition" !in ids("75"), "la montagne seulement en montagne")
        assertTrue("border_police" in ids("67") && "border_police" !in ids("45"))
        assertTrue("epr_reactor" in ids(s.context.catalog.items.values.first { it.type == "NUCLEAR_PLANT" }.department))
        assertTrue(ids("75").size >= 40, "beaucoup d'actions possibles partout : ${ids("75").size}")
        // Une action liée à la situation est montrée verrouillée, avec sa condition.
        val locked = s.localActions.actionsFor("75").firstOrNull { it.def.id == "factory_visit" }
        assertTrue(locked == null || locked.blocker != null)
        // Toutes les actions disponibles s'exécutent sans erreur.
        for (code in listOf("29", "73", "67", "93", "971")) {
            s.localActions.actionsFor(code).filter { it.blocker == null }.forEach { v ->
                val r = s.localActions.perform(code, v.def.id)
                assertTrue(r.isSuccess || r.exceptionOrNull()?.message?.contains("Possible") == true, "${v.def.id} $code : ${r.exceptionOrNull()?.message}")
            }
        }
    }
}
