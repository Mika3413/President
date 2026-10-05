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
        val change = p.changes.single()
        val old = change.to
        assertTrue(s.amendments.amend(p.id, "water_down").isSuccess)
        assertTrue(s.amendments.chance(p.id) > before)
        assertTrue(kotlin.math.abs(change.to - change.from) < kotlin.math.abs(old - change.from), "le taux visé se rapproche du taux actuel")
        assertTrue(s.amendments.amend(p.id, "water_down").isFailure, "un amendement ne se négocie qu'une fois")
        assertTrue(p.status == PolicyStatus.PENDING_VOTE)
    }
}
