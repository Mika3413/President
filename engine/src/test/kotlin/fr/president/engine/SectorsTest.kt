package fr.president.engine

import fr.president.engine.events.EventLauncher
import fr.president.engine.events.ScopeRef
import kotlin.test.Test
import kotlin.test.assertTrue

class SectorsTest {
    @Test
    fun marketStaysPlausibleOverTwoYears() {
        for (seed in listOf(3L, 4L)) {
            val clock = TestData.FakeClock()
            val s = TestData.newSession(seed = seed, clock = clock)
            clock.advanceWorldDays(730.0)
            s.advanceToNow()
            val base = s.context.playerData.sectors!!.indexBase
            val sectorsLine = s.market.sectors().joinToString { "${it.def.id}=%.2f".format(it.activity) }
            println("SECTORS seed=$seed index=%.0f $sectorsLine".format(s.market.index))
            assertTrue(s.market.index in base * 0.5..base * 2.2, "indice ${s.market.index}")
            s.market.sectors().forEach { assertTrue(it.activity in 0.6..1.4, "${it.def.id} ${it.activity}") }
            assertTrue((s.state.stats.series["market"]?.all()?.size ?: 0) > 50)
        }
    }

    @Test
    fun lockdownAndAttacksHitTourism() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        clock.advanceWorldDays(2.0); s.advanceToNow()
        val before = s.state.market.sectors.getValue("tourism").activity
        s.measures.activate("lockdown").getOrThrow()
        val def = s.db.event("terror_attack")
        EventLauncher(s.context).launch(def, ScopeRef(def.scope, "paris"))
        clock.advanceWorldDays(20.0); s.advanceToNow()
        assertTrue(s.state.market.sectors.getValue("tourism").activity < before - 0.05)
    }

    @Test
    fun theStateCanBackACompany() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        clock.advanceWorldDays(1.0); s.advanceToNow()
        val price = s.state.market.companies.getValue("delmas").price
        assertTrue(s.market.support("delmas").isSuccess)
        assertTrue(s.state.market.companies.getValue("delmas").price > price)
        assertTrue(s.market.support("delmas").isFailure, "délai avant un nouveau soutien")
        assertTrue(s.market.summon("verlaine").isSuccess)
    }
}
