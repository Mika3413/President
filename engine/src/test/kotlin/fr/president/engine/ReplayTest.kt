package fr.president.engine

import fr.president.engine.military.StrikeLog
import fr.president.engine.readout.ReplayReadout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReplayTest {

    @Test
    fun `chaque semaine une image de la carte est gardée pour la relecture`() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(seed = 4L, clock = clock)
        repeat(8) { clock.advanceWorldDays(7.0); s.advanceToNow() }
        val stats = s.state.stats
        assertTrue(stats.replay.size >= 8, "Une image par semaine (${stats.replay.size})")
        assertEquals(s.state.territory.departments.size, stats.replayDepartments.size)
        assertTrue(stats.replay.all { it.approval.length == stats.replayDepartments.size })
        val readout = ReplayReadout(s.context)
        val step = readout.step(readout.count - 1)!!
        assertTrue(step.metrics.any { it.label == "Popularité" })
        assertTrue(step.approval.values.all { it in 0.0..1.0 })
        // Les courbes et les images restent alignées.
        assertEquals(step.metrics.first { it.key == "approval" }.history.size, readout.count)
    }

    @Test
    fun `les frappes récentes sont gardées pour être animées`() {
        val s = TestData.newSession()
        val unit = s.state.military.units.values.first { it.countryId == "FRA" }
        val target = s.db.zones.zones.keys.first { it != unit.zoneId }
        repeat(30) { StrikeLog.add(s.state.military, s.db.zones, s.context.now, "FRA", target) }
        assertEquals(20, s.state.military.strikes.size)
        assertTrue(s.state.military.strikes.all { it.to == target && it.from != target })
    }
}
