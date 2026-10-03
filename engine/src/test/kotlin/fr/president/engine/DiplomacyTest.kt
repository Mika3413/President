package fr.president.engine

import fr.president.engine.diplomacy.Clause
import fr.president.engine.diplomacy.ClauseValuator
import fr.president.engine.diplomacy.ProposalStatus
import fr.president.engine.inbox.MessageOrigin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DiplomacyTest {

    @Test
    fun `l'Allemagne répond à une proposition énergétique après un délai`() {
        val clock = TestData.FakeClock()
        val session = TestData.newSession(seed = 11L, clock = clock)
        val proposal = session.diplomacy.propose(
            "DEU",
            listOf(Clause(ClauseValuator.ELECTRICITY, "FRA", mapOf("volumeTWh" to 10.0, "pricePercent" to 100.0))),
            5,
        )
        assertEquals(ProposalStatus.PENDING, proposal.status)
        clock.advanceWorldDays(6.0)
        session.advanceToNow()
        assertNotEquals(ProposalStatus.PENDING, proposal.status)
        val response = session.state.inbox.messages.last { it.origin != MessageOrigin.EVENT && it.category.name == "DIPLOMACY" }
        assertTrue(response.body.isNotBlank())
    }

    @Test
    fun `une offre déraisonnable est refusée ou amendée`() {
        val clock = TestData.FakeClock()
        val session = TestData.newSession(seed = 3L, clock = clock)
        val proposal = session.diplomacy.propose(
            "DEU",
            listOf(Clause(ClauseValuator.ELECTRICITY, "FRA", mapOf("volumeTWh" to 30.0, "pricePercent" to 130.0))),
            10,
        )
        clock.advanceWorldDays(6.0)
        session.advanceToNow()
        assertTrue(proposal.status == ProposalStatus.REFUSED || proposal.status == ProposalStatus.COUNTERED)
        assertTrue(proposal.reasons.isNotEmpty(), "Un refus doit être motivé")
    }

    @Test
    fun `rompre un accord dégrade durablement la relation`() {
        val clock = TestData.FakeClock()
        val session = TestData.newSession(seed = 11L, clock = clock)
        val before = session.diplomacy.relation("DEU").label
        session.diplomacy.propose("DEU", listOf(Clause(ClauseValuator.TARIFFS, "FRA", mapOf("percent" to 2.0))), 3)
        clock.advanceWorldDays(6.0)
        session.advanceToNow()
        val agreement = session.diplomacy.agreementsWith("DEU").firstOrNull() ?: return
        session.diplomacy.breakAgreement(agreement.id)
        val factors = session.diplomacy.relation("DEU").factors
        assertTrue(factors.any { it.weight < 0 && it.label.contains("rompu") }, "Facteurs : $factors (avant : $before)")
    }
}
