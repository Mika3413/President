package fr.president.engine

import fr.president.engine.diplomacy.RelationCalculator
import fr.president.engine.events.EventLauncher
import fr.president.engine.events.EventScope
import fr.president.engine.events.ScopeRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ForeignRippleTest {
    @Test
    fun disastersOnlyStrikeWhereTheRiskExists() {
        val s = TestData.newSession()
        val quake = { c: String -> s.context.variables.resolve("scope.hazard_earthquake", ScopeRef(EventScope.FOREIGN_COUNTRY, c)) }
        assertEquals(0.0, quake("UKR"), "pas de séisme majeur en Ukraine")
        assertEquals(1.0, quake("TUR"))
        assertEquals(1.0, s.context.variables.resolve("scope.hazard_flood", ScopeRef(EventScope.FOREIGN_COUNTRY, "UKR")))
    }

    private fun afterChoice(option: String): Pair<fr.president.engine.session.GameSession, Double> {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        val def = s.db.event("disaster_abroad")
        val instance = EventLauncher(s.context).launch(def, ScopeRef(EventScope.FOREIGN_COUNTRY, "TUR"))
        s.answer(instance.messageId!!, option)
        clock.advanceWorldDays(5.0)
        s.advanceToNow()
        return s to RelationCalculator(s.context).score("TUR", "FRA")
    }

    @Test
    fun helpingOrNotChangesTheCountryItsAlliesAndUs() {
        val base = TestData.newSession()
        val relationBefore = RelationCalculator(base.context).score("TUR", "FRA")
        val (helped, helpedRelation) = afterChoice("major")
        val (ignored, ignoredRelation) = afterChoice("none")
        assertTrue(helpedRelation > relationBefore, "l'aide rapproche")
        assertTrue(ignoredRelation < relationBefore, "l'indifférence éloigne")
        // Les alliés de la Turquie (OTAN) retiennent notre attitude.
        val ally = "DEU"
        assertTrue(RelationCalculator(helped.context).score(ally, "FRA") > RelationCalculator(ignored.context).score(ally, "FRA"))
        // Le séisme frappe l'économie turque, et d'autres pays lui portent secours.
        assertTrue(ignored.state.events.news.any { it.headline.contains("envoient de l'aide") })
        assertTrue(ignored.state.diplomacy.relation("TUR", ally).memories.any { it.kind == "CRISIS_SOLIDARITY" } ||
            ignored.state.diplomacy.relation("TUR", "GBR").memories.any { it.kind == "CRISIS_SOLIDARITY" } ||
            ignored.state.events.news.any { it.headline.contains("aide") })
    }
}
