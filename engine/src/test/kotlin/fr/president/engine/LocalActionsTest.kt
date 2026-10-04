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
}
