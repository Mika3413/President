package fr.president.engine

import fr.president.engine.military.NuclearPosture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefenseTest {
    @Test
    fun equipmentIsDeliveredAndRaisesCapabilities() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        val before = s.defense.capability("airDefense")
        assertTrue(s.defense.order("mistral").isSuccess)
        assertTrue(s.defense.order("mistral").isSuccess)
        assertTrue(s.defense.order("mistral").isFailure, "deux commandes au plus")
        val units = s.state.military.units.values.count { it.countryId == "FRA" }
        assertTrue(s.defense.order("rafale").isSuccess)
        clock.advanceWorldDays(950.0); s.advanceToNow()
        assertTrue(s.defense.capability("airDefense") > before + 0.09)
        assertTrue(s.state.military.units.values.count { it.countryId == "FRA" && !it.destroyed } > units - 3)
        assertEquals(0, s.defense.pending("rafale"))
    }

    @Test
    fun basesCostAndCanBeClosedOrOpened() {
        val s = TestData.newSession()
        val rows = s.defense.bases()
        assertTrue(rows.any { it.open && it.def.id == "djibouti" })
        assertTrue(s.defense.closeBase("djibouti").isSuccess)
        assertTrue(s.defense.closeBase("djibouti").isFailure)
        assertTrue(s.defense.bases().first { it.def.id == "djibouti" }.blocker == null)
        assertTrue(s.defense.openBase("djibouti").isSuccess)
        assertTrue(s.defense.openBase("djibouti").isFailure, "négociation toute récente")
    }

    @Test
    fun deterrence() {
        val s = TestData.newSession()
        val credibility = s.defense.state.credibility
        assertTrue(s.defense.modernize().isSuccess)
        assertTrue(s.defense.modernize().isFailure)
        assertTrue(s.defense.state.credibility > credibility)
        assertTrue(s.defense.setUmbrella(true).isSuccess)
        assertTrue(s.defense.setPosture(NuclearPosture.REINFORCED).isSuccess)
        assertTrue(fr.president.engine.military.DefenseService.hybridFactor(s.context) < 1.0)
        assertTrue(s.defense.reduceArsenal().isSuccess)
        assertEquals(260, s.defense.state.warheads)
        assertTrue(s.defense.solemnWarning().isFailure, "seulement en cas d'invasion")
        assertTrue(s.defense.nuclearTest().isSuccess)
        assertTrue(s.defense.nuclearTest().isFailure)
    }

    @Test
    fun armsSalesAreEmbargoedForAggressors() {
        val s = TestData.newSession()
        val p = s.trade.product("arms_rafale")!!
        assertTrue(p.arms)
        val option = s.trade.bids(p).first { it.blocker == null }
        assertTrue(s.trade.bid(p.id, option.client, guaranteed = false).isSuccess)
    }
}
