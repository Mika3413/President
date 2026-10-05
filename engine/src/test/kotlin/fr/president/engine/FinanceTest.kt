package fr.president.engine

import fr.president.engine.economy.DebtStrategy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FinanceTest {
    @Test
    fun detailedTaxesChangeRevenue() {
        val s = TestData.newSession()
        val before = s.state.playerCountry.economy.revenueBillions
        s.fiscal.enact("wealth", 1.0)
        assertTrue(s.state.playerCountry.economy.revenueBillions > before + 3)
        assertEquals(1.0, s.fiscal.value("wealth"))
        assertTrue("wealth_tax" in s.state.policy.adoptedReforms, "l'ISF rétabli vaut réforme adoptée")
        // Effet Laffer : au-delà d'un certain taux, la recette baisse.
        val d = s.fiscal.def("wealth")!!
        assertTrue(s.fiscal.revenueAt(d, 3.0) < s.fiscal.revenueAt(d, 1.5))
        val first = s.fiscal.propose("tax_niches", 10.0).getOrThrow()
        // Un nouveau texte sur le même dispositif remplace le précédent.
        s.fiscal.propose("tax_niches", 15.0).getOrThrow()
        assertEquals(fr.president.engine.government.PolicyStatus.REJECTED, first.status)
        assertEquals(15.0, s.legislation.pendingFor("fiscal:tax_niches")!!.changes.single().to)
    }

    @Test
    fun theEcbAndTheEuroMoveOverTime() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        clock.advanceWorldDays(400.0); s.advanceToNow()
        val m = s.state.monetary
        assertTrue(m.ecbRate in 0.0..0.08)
        assertTrue(m.eurUsd in 0.8..1.6)
        assertTrue(m.ecbHistory.size >= 12)
        s.monetary.setStrategy(DebtStrategy.LONG)
        assertTrue(s.state.playerCountry.economy.debtRolloverFactor < 1.0)
    }

    @Test
    fun theStateBuysAndSellsCompanies() {
        val s = TestData.newSession()
        val debt = s.state.playerCountry.economy.pendingOneOffBillions
        assertTrue(s.market.nationalize("delmas").isSuccess)
        assertTrue(s.state.playerCountry.economy.pendingOneOffBillions > debt)
        assertTrue(s.market.privatize("energiefrance", 0.1).isSuccess)
        assertEquals(0.9, s.state.market.stakes.getValue("energiefrance"), 1e-9)
    }
}
