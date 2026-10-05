package fr.president.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TradeTest {
    @Test
    fun commodityPricesMoveAndFeedEnergy() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        clock.advanceWorldDays(200.0); s.advanceToNow()
        val oil = s.state.trade.commodities.getValue("oil")
        assertTrue(oil.history.size >= 5)
        assertTrue(oil.price in 72 * 0.4..72 * 3.5)
        assertTrue(s.state.trade.energyFactor in 0.7..2.0)
    }

    @Test
    fun reservesAndContractsSoftenThePrice() {
        val s = TestData.newSession()
        s.advanceToNow()
        val gas = s.trade.commodity("gas")!!
        s.trade.state("gas")!!.price = 70.0
        val before = s.trade.effectivePrice(gas)
        assertTrue(s.trade.release("gas").isSuccess)
        assertTrue(s.trade.effectivePrice(gas) < before)
        assertTrue(s.trade.release("gas").isFailure)
        val offer = s.trade.contracts().first { it.def.commodity == "gas" && it.blocker == null }
        assertTrue(s.trade.signContract(offer.def.country, "gas").isSuccess)
        assertEquals(1, s.state.trade.contracts.size)
        assertTrue(s.trade.cover("gas") > 0.0)
    }

    @Test
    fun exportBidsAreDecided() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        val p = s.trade.product("airliners")!!
        val option = s.trade.bids(p).first { it.blocker == null }
        assertTrue(s.trade.bid(p.id, option.client, guaranteed = true).isSuccess)
        assertTrue(s.trade.bid(p.id, option.client, guaranteed = false).isFailure)
        clock.advanceWorldDays(p.delayDays + 40); s.advanceToNow()
        assertTrue(s.state.trade.bids.isEmpty())
        assertTrue(s.state.trade.results.any { it.product == p.id && it.client == option.client })
    }

    @Test
    fun institutions() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        assertTrue(s.trade.requestImf().isFailure)
        s.state.playerCountry.economy.marketRate = 0.08
        assertTrue(s.trade.requestImf().isSuccess)
        assertTrue(s.state.playerCountry.economy.imfRelief > 0)
        assertTrue(s.trade.contributeWorldBank().isSuccess)
        assertTrue(s.trade.contributeWorldBank().isFailure)
        val target = s.trade.wtoTargets().first()
        assertTrue(s.trade.fileWto(target.country).isSuccess)
        clock.advanceWorldDays(240.0); s.advanceToNow()
        assertTrue(s.state.trade.wto.isEmpty())
        assertEquals(1, s.state.trade.wtoHistory.size)
    }

    @Test
    fun shaleGasNeedsTheLawChanged() {
        val s = TestData.newSession()
        assertTrue(s.trade.authorizeShale().isFailure)
        assertTrue(s.trade.authorizeMine().isSuccess)
        assertTrue(s.trade.cover("lithium") > 0.0)
    }
}
