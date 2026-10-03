package fr.president.engine.session

import fr.president.engine.diplomacy.Agreement
import fr.president.engine.diplomacy.Clause
import fr.president.engine.diplomacy.DiplomacyService
import fr.president.engine.diplomacy.Proposal
import fr.president.engine.diplomacy.RelationCalculator
import fr.president.engine.simulation.SimulationContext

/** Actions diplomatiques du joueur et lecture des relations. */
class DiplomacyCommands(private val ctx: SimulationContext) {
    private companion object {
        const val CONDEMN_FOLLOW = 0.4
        const val CONDEMN_OPINION = 0.004
    }

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

    fun sanction(target: String) = fr.president.engine.diplomacy.SanctionsService(ctx).impose(player, target)
    fun liftSanctions(target: String) = fr.president.engine.diplomacy.SanctionsService(ctx).lift(player, target)
    fun isSanctioning(target: String) = fr.president.engine.diplomacy.SanctionsService(ctx).isSanctioning(player, target)

    /** Condamnation publique d'un pays (médiatiser un conflit). */
    fun condemn(target: String) {
        service.remember(target, "CONDEMNATION", "condamnation publique")
        ctx.state.countries.keys.filter { it != target && it != player }.forEach { c ->
            if (RelationCalculator(ctx).score(c, target) < CONDEMN_FOLLOW) service.remember(c, "NEGOTIATION_GOODWILL", "fermeté partagée")
        }
        ctx.state.opinion.groups.values.forEach { it.shock += CONDEMN_OPINION }
        ctx.notifications.post(fr.president.engine.notifications.NotificationCategory.DIPLOMACY, fr.president.engine.notifications.Urgency.INFO,
            "Condamnation publique", "La France condamne publiquement ${ctx.db.country(target).definition.name}.", target)
    }

    fun ultimatum(target: String, demand: fr.president.engine.diplomacy.Demand) =
        fr.president.engine.diplomacy.UltimatumService(ctx).send(player, target, demand)

    fun declareWar(target: String) =
        fr.president.engine.military.WarService(ctx).declare(player, target, "La France déclare la guerre à ${ctx.db.country(target).definition.name}.")

    fun joinWar(warId: String, defenderSide: Boolean) {
        val war = ctx.state.military.wars.firstOrNull { it.id == warId } ?: return
        fr.president.engine.military.WarService(ctx).join(war, player, defenderSide)
    }
}
