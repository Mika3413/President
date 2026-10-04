package fr.president.engine

import fr.president.engine.government.PolicyStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MediaAndAmendmentsTest {
    @Test
    fun papersAndPollsArePublished() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        clock.advanceWorldDays(21.0)
        s.advanceToNow()
        val pages = s.media.frontPages()
        assertEquals(s.db.media!!.papers.size, pages.size)
        assertTrue(pages.all { it.second.headline.isNotBlank() && !it.second.headline.contains("{") })
        assertTrue(s.media.polls().size >= 3)
        assertTrue(s.media.concerns().isNotEmpty())
    }

    @Test
    fun amendmentsBuyVotesAndSoftenTheText() {
        val s = TestData.newSession()
        val p = s.policy.proposeTaxRate("vat", 25.0)
        val before = s.amendments.chance(p.id)
        val old = p.newValue
        assertTrue(s.amendments.amend(p.id, "water_down").isSuccess)
        assertTrue(s.amendments.chance(p.id) > before)
        assertTrue(kotlin.math.abs(p.newValue - p.oldValue) < kotlin.math.abs(old - p.oldValue))
        assertTrue(s.amendments.amend(p.id, "water_down").isFailure, "un amendement ne se négocie qu'une fois")
        assertTrue(p.status == PolicyStatus.PENDING_VOTE)
    }
}
