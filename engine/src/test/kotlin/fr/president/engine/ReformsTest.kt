package fr.president.engine

import fr.president.engine.elections.PromiseEvaluator
import fr.president.engine.elections.PromiseStatus
import fr.president.engine.government.PolicyStatus
import fr.president.engine.session.GameSession
import fr.president.engine.setup.NewGameOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReformsTest {
    @Test
    fun `une réforme des retraites adoptée réduit la dépense de retraite`() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        s.state.government.parliamentSupport = 0.95
        val pensions = s.state.playerCountry.economy.budget!!.spending.getValue("pensions")
        val proposal = s.policy.proposeReform("pension_age_65").getOrThrow()
        clock.advanceWorldDays(400.0)
        s.advanceToNow()
        assertEquals(PolicyStatus.ADOPTED, proposal.status)
        assertTrue(pensions.policyFactor < 1.0)
        assertNotNull(s.policy.reformBlocker("pension_age_62"), "Réforme incompatible bloquée")
    }

    @Test
    fun `les promesses sont suivies`() {
        val clock = TestData.FakeClock()
        val s = GameSession.newGame(TestData.db, NewGameOptions("normal", 4L, clock.now, promises = listOf("no_war", "no_tax_increase", "nuclear")), clock)
        val eval = PromiseEvaluator(s.context)
        assertEquals(3, eval.chosen().size)
        assertEquals(PromiseStatus.ON_TRACK, eval.status(eval.chosen().first { it.id == "no_war" }))
        s.diplomacy.declareWar("CHE")
        assertEquals(PromiseStatus.BROKEN, eval.status(eval.chosen().first { it.id == "no_war" }))
        assertEquals(PromiseStatus.AT_RISK, eval.status(eval.chosen().first { it.id == "nuclear" }))
    }

    @Test
    fun `la population évolue`() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        val before = s.state.playerCountry.population
        clock.advanceWorldDays(365.0)
        s.advanceToNow()
        assertTrue(s.state.playerCountry.population > before)
    }
}
