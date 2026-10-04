package fr.president.engine

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StatsTest {
    @Test
    fun seriesJournalAndCausesAreRecorded() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        assertTrue(s.nationalActions.perform("tv_address").isSuccess)
        clock.advanceWorldDays(60.0)
        s.advanceToNow()
        val approval = assertNotNull(s.stats.series("approval"))
        assertTrue(approval.values.size >= 8, "relevés hebdomadaires : ${approval.values.size}")
        s.stats.mainKeys.forEach { assertNotNull(s.stats.series(it), it) }
        assertTrue(s.stats.groups().size >= 10)
        assertTrue(s.stats.countries().size >= 20)
        assertTrue(s.stats.journal().any { it.text == "Allocution télévisée" })
        listOf("approval", "unemployment", "growth", "deficit").forEach { key ->
            assertTrue(assertNotNull(s.stats.why(key)).causes.isNotEmpty(), key)
        }
    }
}
