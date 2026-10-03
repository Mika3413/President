package fr.president.engine

import fr.president.engine.inbox.InboxSystem
import kotlin.test.Test
import kotlin.test.assertTrue

/** Parties complètes simulées avec un joueur qui répond au hasard : aucune erreur ne doit survenir. */
class EnduranceTest {
    @Test
    fun `plusieurs mandats simulés sans erreur`() {
        for (seed in listOf(101L, 202L, 303L)) {
            val clock = TestData.FakeClock()
            val s = TestData.newSession(seed = seed, clock = clock)
            val rng = java.util.Random(seed)
            var days = 0
            while (days < 6 * 365 && !s.isGameOver) {
                clock.advanceWorldDays(10.0)
                days += 10
                s.advanceToNow()
                // Joueur actif : répond à la moitié des messages, propose parfois des mesures.
                s.state.inbox.messages.filter { it.awaitingAnswer }.forEach { m ->
                    if (rng.nextBoolean()) InboxSystem.answer(s.context, m, m.options[rng.nextInt(m.options.size)].id, byDefault = false)
                }
                if (days % 200 == 0) s.policy.reforms().getOrNull(rng.nextInt(s.policy.reforms().size))?.let { s.policy.proposeReform(it.id) }
                if (days % 150 == 0) s.military.ownUnits().filter { it.type.endsWith("BRIGADE") }.randomOrNull()?.let { u ->
                    s.military.order(u.id, fr.president.engine.military.UnitOrder.MOVE, s.military.zoneAt(2.0 + rng.nextDouble() * 4, 44.0 + rng.nextDouble() * 4))
                }
            }
            val e = s.state.playerCountry.economy
            println("seed $seed : jours=$days fin=${s.state.player.gameOver?.reason} mandat=${s.state.player.termNumber} " +
                "chômage=%.3f dette=%.2f opinion=%.2f guerres=%d".format(e.unemployment, e.debtRatio, s.state.opinion.nationalApproval, s.state.military.wars.size))
            assertTrue(e.unemployment in 0.01..0.35 && e.debtRatio in 0.5..3.0)
            assertTrue(s.state.elections.results.isNotEmpty(), "Au moins une élection en 6 ans")
        }
    }
}
