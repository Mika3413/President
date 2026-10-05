package fr.president.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntelTest {
    @Test
    fun operationsResolveAndRevealLeaders() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        s.state.intel.capacity = -1.0
        s.intel.state.capacity = 0.9
        assertTrue(s.intel.start("spy_leader", "FRA").isFailure)
        assertTrue(s.intel.start("coup", "DEU").isFailure, "jamais contre un allié")
        assertTrue(s.intel.start("spy_leader", "BRA").isSuccess)
        assertTrue(s.intel.start("spy_leader", "BRA").isFailure)
        clock.advanceWorldDays(40.0); s.advanceToNow()
        assertTrue(s.state.intel.operations.isEmpty())
        assertEquals(1, s.state.intel.history.size)
    }

    @Test
    fun groupsDriveTerrorRisk() {
        val s = TestData.newSession()
        val before = fr.president.engine.military.SecretService.eventFactor(s.context, "terror_attack")
        assertEquals(1.0, before, 1e-9)
        s.intel.state.groups.getValue("ei").strength = 0.9
        assertTrue(fr.president.engine.military.SecretService.eventFactor(s.context, "terror_attack") > 1.2)
        s.intel.setSurveillance(true)
        val surveilled = fr.president.engine.military.SecretService.eventFactor(s.context, "terror_attack")
        s.intel.setSurveillance(false)
        assertTrue(surveilled < fr.president.engine.military.SecretService.eventFactor(s.context, "terror_attack"))
        assertTrue(s.intel.actOnGroup("ultra_right", "strike").isFailure, "action non prévue")
        assertTrue(s.intel.actOnGroup("pkk", "negotiate").isSuccess)
        assertTrue(s.intel.actOnGroup("pkk", "negotiate").isFailure, "délai")
        assertTrue(s.intel.actOnGroup("ei", "infiltrate").isSuccess)
    }

    @Test
    fun hostileCountriesSpyAndCanBeExpelled() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        s.intel.state
        s.state.intel.foreignSpying["RUS"] = 0.4
        assertTrue(s.intel.counterEspionage("RUS").isSuccess)
        assertTrue(s.intel.counterEspionage("RUS").isFailure)
        assertTrue(s.intel.recruit().isSuccess)
        assertTrue(s.intel.recruit().isFailure)
    }
}
