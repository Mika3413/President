package fr.president.engine.session

import fr.president.engine.diplomacy.Agreement
import fr.president.engine.diplomacy.Clause
import fr.president.engine.diplomacy.DiplomacyService
import fr.president.engine.diplomacy.Proposal
import fr.president.engine.diplomacy.RelationCalculator
import fr.president.engine.simulation.SimulationContext

/** Actions diplomatiques du joueur et lecture des relations. */
class DiplomacyCommands(private val ctx: SimulationContext) {
    private val service = DiplomacyService(ctx)
    private val player get() = ctx.state.player.countryId

    data class RelationView(val label: String, val factors: List<RelationCalculator.Factor>, val trustLabel: String)

    fun foreignCountries(): List<String> = ctx.state.countries.keys.filter { it != player }

    fun relation(countryId: String): RelationView {
        val calc = RelationCalculator(ctx)
        val score = calc.score(countryId, player)
        return RelationView(calc.label(score), calc.factors(countryId, player), ctx.db.readouts.describe("trust", calc.trust(countryId, player)).label)
    }

    fun propose(to: String, clauses: List<Clause>, years: Int, counterOfId: String? = null): Proposal =
        service.submitPlayerProposal(to, clauses, years, counterOfId)

    fun agreementsWith(countryId: String): List<Agreement> =
        ctx.state.diplomacy.agreements.filter { it.active && countryId in it.parties }

    fun pendingProposals(): List<Proposal> = ctx.state.diplomacy.proposals.filter { it.status == fr.president.engine.diplomacy.ProposalStatus.PENDING }

    fun breakAgreement(id: String) = service.breakAgreement(id)

    fun proposal(id: String): Proposal? = service.findProposal(id)
}
