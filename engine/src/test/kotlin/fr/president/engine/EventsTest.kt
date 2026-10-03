package fr.president.engine

import fr.president.engine.events.EventLauncher
import fr.president.engine.events.EventScope
import fr.president.engine.events.ScopeRef
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventsTest {

    private fun scopeFor(session: fr.president.engine.session.GameSession, scope: EventScope, infraTypes: List<String>): List<ScopeRef> {
        val s = session.state
        return when (scope) {
            EventScope.NATIONAL -> listOf(ScopeRef(scope, null))
            // Départements avec et sans grande ville référencée.
            EventScope.DEPARTMENT -> listOf("34", "52", "93").map { ScopeRef(scope, it) }
            EventScope.CITY -> listOf(ScopeRef(scope, "montpellier"))
            EventScope.INFRASTRUCTURE -> listOf(ScopeRef(scope, s.infrastructure.values.first { infraTypes.isEmpty() || it.type in infraTypes }.id))
            EventScope.MINISTER -> listOf(ScopeRef(scope, s.government.ministers.values.first()))
            EventScope.FOREIGN_COUNTRY -> listOf(ScopeRef(scope, "DEU"))
        }
    }

    @Test
    fun `chaque événement produit des textes entièrement résolus`() {
        val session = TestData.newSession(seed = 77L)
        // Une guerre étrangère, pour les sommets et résolutions qui en parlent.
        fr.president.engine.military.WarService(session.context).declare("RUS", "UKR", "Test")
        for (def in TestData.db.events) {
            for (scope in scopeFor(session, def.scope, def.infraTypes)) {
                val before = session.state.inbox.messages.size
                EventLauncher(session.context).launch(def, scope)
                val texts = session.state.events.news.takeLast(1).map { it.headline } +
                    session.state.inbox.messages.drop(before).flatMap { listOf(it.subject, it.body) + it.options.flatMap { o -> listOf(o.label, o.hint) } } +
                    session.state.notifications.feed.takeLast(2).flatMap { listOf(it.title, it.body) }
                texts.forEach { t ->
                    assertFalse(t.contains("{") || t.contains("[["), "Texte non résolu pour ${def.id} (${scope.id}) : $t")
                }
            }
        }
    }

    @Test
    fun `chaque option de chaque événement s'applique sans erreur`() {
        for (def in TestData.db.events.filter { it.message != null }) {
            for (option in def.message!!.options) {
                val clock = TestData.FakeClock()
                val session = TestData.newSession(seed = 13L, clock = clock)
                val scope = scopeFor(session, def.scope, def.infraTypes).first()
                val instance = EventLauncher(session.context).launch(def, scope)
                session.answer(instance.messageId!!, option.id)
                clock.advanceWorldDays(60.0)
                session.advanceToNow()
                assertTrue(session.state.time > session.state.meta.startTime)
            }
        }
    }
}
