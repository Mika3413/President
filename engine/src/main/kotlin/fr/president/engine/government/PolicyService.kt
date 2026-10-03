package fr.president.engine.government

import fr.president.engine.data.TaxPayer
import fr.president.engine.economy.BudgetCalculator
import fr.president.engine.economy.GrowthImpulse
import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import fr.president.engine.simulation.ScheduledAction
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.util.Formatting

/**
 * Mesures budgétaires : dépôt au Parlement, vote après un délai, puis effets
 * directs (recettes/dépenses) et indirects différés (demande, confiance, opinion).
 */
class PolicyService(private val ctx: SimulationContext) {
    private val gov = ctx.playerData.government!!

    fun proposeTaxRate(itemId: String, newRate: Double): PolicyProposal {
        val item = ctx.state.playerCountry.economy.budget!!.revenues.getValue(itemId)
        val def = ctx.playerData.economy.budget!!.revenues.first { it.id == itemId }
        val rate = newRate.coerceIn(def.minRate, def.maxRate)
        return submit(PolicyKind.TAX_RATE, itemId, pendingValue(itemId) ?: item.rate, rate, gov.parliament.taxVoteDelayDays)
    }

    fun proposeSpending(itemId: String, newFactor: Double): PolicyProposal {
        val item = ctx.state.playerCountry.economy.budget!!.spending.getValue(itemId)
        val factor = newFactor.coerceIn(MIN_SPENDING_FACTOR, MAX_SPENDING_FACTOR)
        return submit(PolicyKind.SPENDING, itemId, pendingValue(itemId) ?: item.policyFactor, factor, gov.parliament.spendingVoteDelayDays)
    }

    /** Valeur visée par une mesure déjà en attente sur le même poste, s'il y en a une. */
    fun pendingValue(itemId: String): Double? = ctx.state.policy.proposals
        .lastOrNull { it.itemId == itemId && it.status == PolicyStatus.PENDING_VOTE }?.newValue

    private fun submit(kind: PolicyKind, itemId: String, old: Double, new: Double, delayDays: Int): PolicyProposal {
        // Une nouvelle mesure sur le même poste remplace la précédente.
        ctx.state.policy.proposals.filter { it.itemId == itemId && it.status == PolicyStatus.PENDING_VOTE }
            .forEach { it.status = PolicyStatus.REJECTED }
        val proposal = PolicyProposal(ctx.state.newId("pol"), kind, itemId, old, new, ctx.now, ctx.now.plusDays(delayDays.toLong()))
        ctx.state.policy.proposals += proposal
        ctx.scheduler.schedule(ScheduledAction.PolicyVote(proposal.voteAt, proposal.id))
        ctx.notifications.news(NotificationCategory.POLITICS, "Le gouvernement dépose une mesure : ${label(proposal)}")
        return proposal
    }

    fun vote(proposalId: String) {
        val proposal = ctx.state.policy.proposals.firstOrNull { it.id == proposalId } ?: return
        if (proposal.status != PolicyStatus.PENDING_VOTE) return
        val p = gov.parliament
        val support = ctx.state.government.parliamentSupport + ctx.rng.nextGaussian() * p.voteNoise
        proposal.supportAtVote = support
        if (support >= p.passThreshold) {
            proposal.status = PolicyStatus.ADOPTED
            apply(proposal)
            ctx.notifications.post(NotificationCategory.POLITICS, Urgency.IMPORTANT, "Mesure adoptée", label(proposal))
        } else {
            proposal.status = PolicyStatus.REJECTED
            ctx.notifications.post(NotificationCategory.POLITICS, Urgency.IMPORTANT, "Mesure rejetée par le Parlement",
                "${label(proposal)}. Vous pouvez la redéposer ou la faire passer en force, au prix d'un coût politique.")
        }
    }

    /** Adoption sans vote (procédure d'exception) : coûteuse en popularité et en soutien parlementaire. */
    fun forcePass(proposalId: String) {
        val proposal = ctx.state.policy.proposals.firstOrNull { it.id == proposalId } ?: return
        if (proposal.status != PolicyStatus.REJECTED && proposal.status != PolicyStatus.PENDING_VOTE) return
        val p = gov.parliament
        proposal.status = PolicyStatus.FORCED
        ctx.state.opinion.groups.values.forEach { it.shock -= p.forcePassApprovalCost }
        ctx.state.government.parliamentSupport = (ctx.state.government.parliamentSupport - p.forcePassSupportCost).coerceAtLeast(0.0)
        apply(proposal)
        ctx.notifications.post(NotificationCategory.POLITICS, Urgency.IMPORTANT, "Mesure adoptée sans vote",
            "${label(proposal)}. L'opposition dénonce un passage en force.")
    }

    private fun apply(proposal: PolicyProposal) {
        val economy = ctx.state.playerCountry.economy
        val params = ctx.db.economyParameters
        val revenueBefore = economy.revenueBillions
        val spendingBefore = economy.spendingBillions
        val householdBefore = BudgetCalculator.taxBurden(economy, TaxPayer.HOUSEHOLDS)
        val businessBefore = BudgetCalculator.taxBurden(economy, TaxPayer.BUSINESSES)
        when (proposal.kind) {
            PolicyKind.TAX_RATE -> economy.budget!!.revenues.getValue(proposal.itemId).rate = proposal.newValue
            PolicyKind.SPENDING -> economy.budget!!.spending.getValue(proposal.itemId).policyFactor = proposal.newValue
        }
        BudgetCalculator.recompute(economy)
        // Impulsion budgétaire : moins de demande quand l'État prélève plus ou dépense moins.
        val impulse = ((economy.spendingBillions - spendingBefore) - (economy.revenueBillions - revenueBefore)) / economy.gdpBillions
        economy.impulses += GrowthImpulse(params.fiscalMultiplier * impulse, params.fiscalImpulseMonths, proposal.id)
        val householdDelta = BudgetCalculator.taxBurden(economy, TaxPayer.HOUSEHOLDS) - householdBefore
        val businessDelta = BudgetCalculator.taxBurden(economy, TaxPayer.BUSINESSES) - businessBefore
        economy.consumerConfidence = (economy.consumerConfidence - params.householdTaxConfidenceShock * householdDelta).coerceIn(0.0, 1.0)
        economy.businessConfidence = (economy.businessConfidence - params.businessTaxConfidenceShock * businessDelta).coerceIn(0.0, 1.0)
        ctx.log("policy", "Mesure ${proposal.id} appliquée : impulsion %.4f, Δpression ménages %.4f, entreprises %.4f"
            .format(impulse, householdDelta, businessDelta))
    }

    fun label(p: PolicyProposal): String {
        val budget = ctx.playerData.economy.budget!!
        return when (p.kind) {
            PolicyKind.TAX_RATE -> {
                val def = budget.revenues.first { it.id == p.itemId }
                "${def.label} : ${Formatting.amount(p.oldValue)} → ${Formatting.amount(p.newValue)} ${def.rateLabel}"
            }
            PolicyKind.SPENDING -> {
                val def = budget.spending.first { it.id == p.itemId }
                "${def.label} : budget ${Formatting.signedPercent(p.newValue - 1.0)} par rapport au budget initial"
            }
        }
    }

    private companion object {
        const val MIN_SPENDING_FACTOR = 0.5
        const val MAX_SPENDING_FACTOR = 1.6
    }
}
