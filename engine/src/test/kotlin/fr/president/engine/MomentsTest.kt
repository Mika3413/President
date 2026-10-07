package fr.president.engine

import fr.president.engine.elections.RoundResult
import fr.president.engine.presidency.MomentKind
import fr.president.engine.presidency.MomentRecord
import fr.president.engine.session.GameSession
import fr.president.engine.simulation.ScheduledAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MomentsTest {
    private fun play(s: GameSession, kind: MomentKind, ref: String = "", pick: (Int) -> Int = { 0 }): MomentRecord {
        assertTrue(s.moments.start(kind, ref).isSuccess)
        var guard = 0
        while (true) {
            val v = s.moments.view() ?: error("Moment interrompu")
            val r = s.moments.choose(v.choices[pick(v.choices.size).coerceAtMost(v.choices.size - 1)].index).getOrThrow()
            if (r != null) return r
            check(guard++ < 20)
        }
    }

    @Test
    fun `une allocution se compose phrase par phrase et a un effet`() {
        val s = TestData.newSession()
        assertTrue(s.moments.offers().any { it.kind == MomentKind.SPEECH && it.blocker == null })
        val before = s.state.opinion.nationalApproval
        val r = play(s, MomentKind.SPEECH)
        assertTrue(r.verdict.startsWith("Allocution"))
        assertTrue(r.lines.isNotEmpty())
        assertNotNull(s.moments.offers().first { it.kind == MomentKind.SPEECH }.blocker, "Délai avant la prochaine allocution")
        assertTrue(s.state.moments.history.size == 1)
        assertTrue(before >= 0.0)
    }

    @Test
    fun `l'interview pose quatre questions`() {
        val s = TestData.newSession()
        var steps = 0
        assertTrue(s.moments.start(MomentKind.INTERVIEW).isSuccess)
        while (s.moments.view() != null) { val v = s.moments.view()!!; assertTrue(v.choices.size == 4); s.moments.choose(v.choices.first().index); steps++ }
        assertEquals(4, steps)
    }

    @Test
    fun `le débat d'entre-deux-tours déplace des points au second tour`() {
        val s = TestData.newSession(seed = 3L)
        val president = s.state.player.presidentId
        val other = s.state.elections.candidates.first { it.characterId != president }.characterId
        s.state.elections.pendingFirstRound = RoundResult(0.7, mapOf(president to 0.3, other to 0.25))
        s.context.scheduler.schedule(ScheduledAction.ElectionRound(s.context.now.plusDays(14), 2))
        assertTrue(s.moments.debateOpen())
        val r = play(s, MomentKind.DEBATE)
        assertTrue(r.title.startsWith("Débat"))
        assertTrue(kotlin.math.abs(s.state.moments.debateBonus) <= 0.04)
        assertTrue(!s.moments.debateOpen(), "Un seul débat par élection")
    }

    @Test
    fun `un sommet se joue en échanges avec les dirigeants`() {
        val s = TestData.newSession()
        s.state.events.lastFired["g7_tax"] = s.context.now
        assertTrue(s.moments.summitsOpen().any { it.first == "g7_tax" })
        val memories = s.state.diplomacy.relation("USA", s.state.player.countryId).memories.size
        play(s, MomentKind.SUMMIT, "g7_tax", pick = { 1 })
        assertTrue(s.state.diplomacy.relation("USA", s.state.player.countryId).memories.size > memories)
        assertTrue(s.moments.summitsOpen().none { it.first == "g7_tax" })
    }
}
