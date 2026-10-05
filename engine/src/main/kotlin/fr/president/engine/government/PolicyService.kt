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

    /** Un seul taux, voté dans un budget rectificatif (les écrans passent par le projet de budget). */
    fun proposeTaxRate(itemId: String, newRate: Double): PolicyProposal {
        val item = ctx.state.playerCountry.economy.budget!!.revenues.getValue(itemId)
        val def = ctx.playerData.economy.budget!!.revenues.first { it.id == itemId }
        val rate = newRate.coerceIn(def.minRate, def.maxRate)
        return fr.president.engine.legislation.LegislationService(ctx).singleBudgetBill("tax:$itemId", rate)
            .getOrElse { submit(PolicyKind.TAX_RATE, itemId, pendingValue(itemId) ?: item.rate, rate, gov.parliament.taxVoteDelayDays) }
    }

    fun proposeSpending(itemId: String, newFactor: Double): PolicyProposal {
        val item = ctx.state.playerCountry.economy.budget!!.spending.getValue(itemId)
        val factor = newFactor.coerceIn(MIN_SPENDING_FACTOR, MAX_SPENDING_FACTOR)
        return fr.president.engine.legislation.LegislationService(ctx).singleBudgetBill("spend:$itemId", factor)
            .getOrElse { submit(PolicyKind.SPENDING, itemId, pendingValue(itemId) ?: item.policyFactor, factor, gov.parliament.spendingVoteDelayDays) }
    }

    fun reforms(): List<ReformDef> = ctx.playerData.reforms?.reforms.orEmpty()

    /** Une réforme peut-elle être déposée ? (null = oui, sinon la raison) */
    fun reformBlocker(id: String): String? {
        val def = reforms().firstOrNull { it.id == id } ?: return "Réforme inconnue"
        val policy = ctx.state.policy
        if (id in policy.adoptedReforms) return "Déjà adoptée"
        if (policy.proposals.any { it.itemId == id && (it.status == PolicyStatus.PENDING_VOTE || it.status == PolicyStatus.PENDING_CENSURE) }) return "Vote en attente"
        val legislation = fr.president.engine.legislation.LegislationService(ctx)
        val lever = legislation.levers.leverForReform(id)?.first ?: "reform:$id"
        if (legislation.pendingFor(lever) != null) return "Vote en attente"
        val parliament = ctx.state.parliament
        if (parliament.referendumReform == id) return "Soumise à référendum"
        parliament.lockedReforms[id]?.let { until ->
            if (ctx.now < until) return "Rejetée par référendum : pas avant ${fr.president.engine.util.Formatting.date(until)}"
        }
        def.exclusiveWith.firstOrNull { it in policy.adoptedReforms }?.let { other ->
            return "Incompatible avec « ${reforms().first { it.id == other }.title} »"
        }
        return null
    }

    /** Une réforme du catalogue : un projet de loi qui la contient (ou le réglage chiffré qui la remplace). */
    fun proposeReform(id: String): Result<PolicyProposal> = runCatching {
        reformBlocker(id)?.let { error(it) }
        val legislation = fr.president.engine.legislation.LegislationService(ctx)
        val (lever, value) = legislation.levers.leverForReform(id) ?: ("reform:$id" to 1.0)
        legislation.singleLawBill(lever, value).getOrThrow()
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
        val support = ctx.state.government.parliamentSupport + proposal.supportBonus + ctx.rng.nextGaussian() * p.voteNoise
        proposal.supportAtVote = support
        val difficulty = when (proposal.kind) {
            PolicyKind.BUDGET_BILL, PolicyKind.LAW_BILL -> fr.president.engine.legislation.LegislationService(ctx).difficulty(proposal)
            PolicyKind.REFORM -> reforms().firstOrNull { it.id == proposal.itemId }?.difficulty ?: 0.0
            PolicyKind.LAW -> LawService(ctx).law(proposal.itemId)?.let { l ->
                // Une réforme constitutionnelle exige la majorité des trois cinquièmes au Congrès.
                (l.options.getOrNull(proposal.newValue.toInt())?.difficulty ?: 0.0) + if (l.constitutional) CONSTITUTIONAL_EXTRA else 0.0
            } ?: 0.0
            else -> 0.0
        }
        if (support >= p.passThreshold + difficulty) {
            proposal.status = PolicyStatus.ADOPTED
            apply(proposal)
            ctx.notifications.post(NotificationCategory.POLITICS, Urgency.IMPORTANT, "Mesure adoptée", label(proposal), journal = false)
            fr.president.engine.stats.JournalService(ctx).add("Loi", "Adoptée : ${label(proposal)}", fr.president.engine.readout.Tone.GOOD)
        } else {
            proposal.status = PolicyStatus.REJECTED
            if (proposal.kind == PolicyKind.BUDGET_BILL || proposal.kind == PolicyKind.LAW_BILL) fr.president.engine.legislation.LegislationService(ctx).billRejected(proposal)
            fr.president.engine.stats.JournalService(ctx).add("Loi", "Rejetée : ${label(proposal)}", fr.president.engine.readout.Tone.BAD)
            ctx.notifications.post(NotificationCategory.POLITICS, Urgency.IMPORTANT, "Mesure rejetée par le Parlement",
                "${label(proposal)}. Vous pouvez la redéposer ou la faire passer en force, au prix d'un coût politique.", journal = false)
        }
    }

    /** Adoption sans vote (procédure d'exception) : coûteuse en popularité et en soutien parlementaire. */
    fun forcePass(proposalId: String) {
        val proposal = ctx.state.policy.proposals.firstOrNull { it.id == proposalId } ?: return
        if (forceBlocker(proposal) != null) return
        if (proposal.status != PolicyStatus.REJECTED && proposal.status != PolicyStatus.PENDING_VOTE) return
        val p = gov.parliament
        ctx.state.opinion.groups.values.forEach { it.shock -= p.forcePassApprovalCost }
        ctx.state.government.parliamentSupport = (ctx.state.government.parliamentSupport - p.forcePassSupportCost).coerceAtLeast(0.0)
        val parliament = ParliamentService(ctx)
        val censure = parliament.legislative
        // Sans majorité solide, l'engagement de responsabilité expose le gouvernement à la censure.
        val censurePossible = LawService(ctx).flag("censure") != 0.0
        if (censure != null && censurePossible && parliament.isActive && ctx.state.government.parliamentSupport < censure.censureThreshold + CENSURE_MARGIN) {
            proposal.status = PolicyStatus.PENDING_CENSURE
            ctx.notifications.post(NotificationCategory.POLITICS, Urgency.IMPORTANT, "Responsabilité du gouvernement engagée",
                "${label(proposal)}. Le texte sera adopté sauf si une motion de censure est votée.")
            parliament.requestCensure(proposal.id)
            return
        }
        enactForced(proposal)
    }

    /** Pourquoi le 49.3 est impossible (null : possible). */
    fun forceBlocker(proposal: PolicyProposal): String? {
        val budget = proposal.kind in setOf(PolicyKind.BUDGET_BILL, PolicyKind.TAX_RATE, PolicyKind.SPENDING, PolicyKind.FISCAL)
        // 49.3 limité aux budgets par la Constitution.
        if (LawService(ctx).flag("forcePass") == 0.0 && !budget) return "Depuis la révision constitutionnelle, le 49.3 est réservé aux budgets."
        val levers = fr.president.engine.legislation.LeverService(ctx)
        if (proposal.kind == PolicyKind.LAW && LawService(ctx).law(proposal.itemId)?.constitutional == true) return "Une révision de la Constitution ne passe jamais sans vote."
        if (proposal.changes.any { levers.lever(it.lever)?.constitutional == true }) return "Une révision de la Constitution ne passe jamais sans vote."
        if (proposal.status == PolicyStatus.PENDING_REFERENDUM) return "Le peuple tranchera."
        return null
    }

    /** Application d'un texte adopté sans vote (directement ou après le rejet d'une censure). */
    fun enactForced(proposal: PolicyProposal) {
        proposal.status = PolicyStatus.FORCED
        apply(proposal)
        ctx.notifications.post(NotificationCategory.POLITICS, Urgency.IMPORTANT, "Mesure adoptée sans vote",
            "${label(proposal)}. L'opposition dénonce un passage en force.")
    }

    /** Réforme approuvée directement par les électeurs. */
    fun adoptByReferendum(id: String) {
        if (id in ctx.state.policy.adoptedReforms) return
        val levers = fr.president.engine.legislation.LeverService(ctx)
        levers.leverForReform(id)?.let { (lever, value) ->
            levers.apply(fr.president.engine.legislation.LeverChange(lever, levers.current(lever), value))
            return
        }
        applyReform(id, viaParliament = false)
    }

    private fun apply(proposal: PolicyProposal) {
        if (proposal.kind == PolicyKind.BUDGET_BILL || proposal.kind == PolicyKind.LAW_BILL) {
            fr.president.engine.legislation.LegislationService(ctx).enactBill(proposal,
                if (proposal.status == PolicyStatus.FORCED) fr.president.engine.legislation.BillStatus.FORCED else fr.president.engine.legislation.BillStatus.ADOPTED)
            return
        }
        if (proposal.kind == PolicyKind.REFORM) {
            applyReform(proposal.itemId, scale = proposal.effectScale)
            fr.president.engine.politics.UnrestService(ctx).onPolicy(proposal.kind, proposal.itemId, 0)
            return
        }
        if (proposal.kind == PolicyKind.LAW) {
            LawService(ctx).enact(proposal.itemId, proposal.newValue.toInt(), proposal.effectScale)
            fr.president.engine.politics.UnrestService(ctx).onPolicy(proposal.kind, proposal.itemId, proposal.newValue.toInt())
            return
        }
        if (proposal.kind == PolicyKind.FISCAL) {
            // Anciennes sauvegardes : l'option visée devient la valeur correspondante.
            val f = fr.president.engine.economy.FiscalService(ctx)
            f.def(proposal.itemId)?.let { d -> f.enact(proposal.itemId, d.legacy.getOrNull(proposal.newValue.toInt()) ?: d.reference) }
            return
        }
        val economy = ctx.state.playerCountry.economy
        val params = ctx.db.economyParameters
        val revenueBefore = economy.revenueBillions
        val spendingBefore = economy.spendingBillions
        val householdBefore = BudgetCalculator.taxBurden(economy, TaxPayer.HOUSEHOLDS)
        val businessBefore = BudgetCalculator.taxBurden(economy, TaxPayer.BUSINESSES)
        when (proposal.kind) {
            PolicyKind.TAX_RATE -> economy.budget!!.revenues.getValue(proposal.itemId).rate = proposal.newValue
            PolicyKind.SPENDING -> economy.budget!!.spending.getValue(proposal.itemId).policyFactor = proposal.newValue
            PolicyKind.REFORM, PolicyKind.LAW, PolicyKind.FISCAL, PolicyKind.BUDGET_BILL, PolicyKind.LAW_BILL -> Unit
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

    fun applyReform(id: String, viaParliament: Boolean = true, scale: Double = 1.0) {
        val def = reforms().first { it.id == id }
        ctx.state.policy.adoptedReforms[id] = ctx.now
        // Navette : un Sénat hostile retarde la mise en œuvre (pas pour un référendum).
        val delay = if (viaParliament) SenateService(ctx).reviewReform(def.title).toDouble() else 0.0
        // Un texte amendé garde sa logique mais avec une portée réduite.
        def.immediateEffects.forEach { ctx.effects.trigger(it.copy(amount = it.amount * scale), null, emptyMap(), id) }
        def.longTermEffects.forEach { ctx.effects.trigger(it.copy(amount = it.amount * scale, delayDays = it.delayDays + delay), null, emptyMap(), id) }
        ctx.notifications.news(NotificationCategory.POLITICS, "Réforme adoptée : ${def.title}")
    }

    fun label(p: PolicyProposal): String {
        val budget = ctx.playerData.economy.budget!!
        return when (p.kind) {
            PolicyKind.BUDGET_BILL, PolicyKind.LAW_BILL -> p.title
            PolicyKind.REFORM -> "Réforme : " + (reforms().firstOrNull { it.id == p.itemId }?.title ?: p.itemId)
            PolicyKind.FISCAL -> fr.president.engine.economy.FiscalService(ctx).def(p.itemId)?.let { t -> "Fiscalité : ${t.label}" } ?: p.itemId
            PolicyKind.LAW -> LawService(ctx).law(p.itemId)?.let { l -> "Loi : ${l.title} — ${l.options.getOrNull(p.newValue.toInt())?.label ?: ""}" } ?: p.itemId
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

    companion object {
        private const val MIN_SPENDING_FACTOR = 0.5
        private const val MAX_SPENDING_FACTOR = 1.6
        /** Marge au-dessus du seuil de censure en deçà de laquelle l'opposition tente sa chance. */
        private const val CENSURE_MARGIN = 0.05
        private const val LAW_DELAY_DAYS = 30
        /** Majorité renforcée du Congrès pour réviser la Constitution. */
        const val CONSTITUTIONAL_EXTRA = 0.08
    }
}
