package fr.president.engine

import fr.president.engine.dialogue.ConversationTopic
import fr.president.engine.politics.CharacterRole
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgendaTest {
    @Test
    fun aTripAbroadTakesTimeAndBlocksVisits() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        assertTrue(s.nationalActions.perform("beijing_visit").isSuccess)
        assertNotNull(s.agenda.abroad(), "en route pour Pékin")
        val mayor = s.state.characters.values.first { it.role == CharacterRole.MAYOR && it.active }
        assertTrue(s.conversations.talk(mayor.id, ConversationTopic.VISIT).isFailure, "pas de visite en France pendant le voyage")
        val wash = s.nationalActions.actions("international").first { it.def.id == "washington_visit" }
        assertNotNull(wash.blocker)
        assertNotNull(wash.agenda)
        clock.advanceWorldDays(3.5); s.advanceToNow()
        assertTrue(s.agenda.abroad()?.label != "Visite d'État à Pékin")
    }

    @Test
    fun theWeekHasOnlySoManyDays() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        s.nationalActions.perform("tour_of_france").getOrThrow()
        clock.advanceWorldDays(4.1); s.advanceToNow()
        // Seul le tour de France compte ici (pas de sommet tiré au hasard entre-temps).
        s.state.agenda.entries.retainAll { it.label == "Tour de France des territoires" }
        val blocked = s.nationalActions.actions("institutions").first { it.def.id == "great_debate" }.blocker
        assertNotNull(blocked, "4 + 3 jours dépassent la semaine")
        assertTrue("créneau" in blocked)
        clock.advanceWorldDays(3.0); s.advanceToNow()
        s.state.agenda.entries.retainAll { it.label == "Tour de France des territoires" }
        assertNull(s.nationalActions.actions("institutions").first { it.def.id == "great_debate" }.blocker)
    }

    @Test
    fun summitsFillTheAgenda() {
        val s = TestData.newSession()
        val def = s.db.event("g7_tax")
        fr.president.engine.events.EventLauncher(s.context).launch(def, fr.president.engine.events.ScopeRef(def.scope, null))
        assertNotNull(s.agenda.abroad())
    }
}
