package fr.president.engine.diplomacy

import fr.president.engine.dialogue.DialogueContextBuilder
import fr.president.engine.effects.EffectSpec
import fr.president.engine.events.InteractionOutcome
import fr.president.engine.inbox.InboxMessage
import fr.president.engine.inbox.MessageOption
import fr.president.engine.inbox.MessageOrigin
import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import fr.president.engine.simulation.ScheduledAction
import fr.president.engine.simulation.SimulationContext

/**
 * Négociation structurée : propositions, analyse par l'IA, réponses différées
 * (acceptation, refus motivé ou contre-proposition), signature et rupture d'accords.
 */
class DiplomacyService(private val ctx: SimulationContext) {
    private val player get() = ctx.state.player.countryId
    private val describer = ProposalDescriber(ctx.db)

    fun submitPlayerProposal(to: String, clauses: List<Clause>, years: Int, counterOfId: String? = null): Proposal {
        val p = ctx.db.diplomacy.evaluation
        val proposal = Proposal(ctx.state.newId("prop"), player, to, clauses, years, ctx.now, counterOfId = counterOfId)
        val delay = ctx.rng.nextDouble(p.responseDelayDaysMin, p.responseDelayDaysMax)
        proposal.responseDue = ctx.now.plusDays(delay)
        ctx.state.diplomacy.proposals.add(proposal)
        ctx.scheduler.schedule(ScheduledAction.ProposalResponse(proposal.responseDue!!, proposal.id))
        counterOfId?.let { id -> findProposal(id)?.status = ProposalStatus.COUNTERED }
        ctx.log("diplomacy", "Proposition ${proposal.id} envoyée à $to, réponse dans %.1f jours".format(delay))
        return proposal
    }

    /** Réponse de l'IA à une proposition du joueur (déclenchée par le planificateur). */
    fun respond(proposalId: String) {
        val proposal = findProposal(proposalId) ?: return
        if (proposal.status != ProposalStatus.PENDING) return
        val ai = proposal.to
        val evaluation = ProposalEvaluator(ctx).evaluate(ai, player, proposal.clauses, proposal.durationYears)
        proposal.reasons += evaluation.reasons
        when {
            evaluation.acceptable -> {
                proposal.status = ProposalStatus.ACCEPTED
                sign(proposal)
                sendResponse(proposal, "response:accepted", emptyList())
            }
            else -> {
                val counter = CounterProposalBuilder(ctx).build(ai, player, proposal.clauses, proposal.durationYears)
                if (counter != null) {
                    proposal.status = ProposalStatus.COUNTERED
                    val counterProposal = Proposal(
                        ctx.state.newId("prop"), ai, player, counter.clauses, counter.durationYears, ctx.now,
                        counterOfId = proposal.id,
                    )
                    ctx.state.diplomacy.proposals.add(counterProposal)
                    sendResponse(counterProposal, "response:countered", evaluation.reasons, withOptions = true)
                } else {
                    proposal.status = ProposalStatus.REFUSED
                    sendResponse(proposal, "response:refused", evaluation.reasons)
                }
            }
        }
    }

    /** Proposition spontanée d'un gouvernement IA au joueur. */
    fun aiPropose(from: String, clauses: List<Clause>, years: Int, motive: String): Proposal {
        val proposal = Proposal(ctx.state.newId("prop"), from, player, clauses, years, ctx.now)
        ctx.state.diplomacy.proposals.add(proposal)
        sendResponse(proposal, "proposal:new", emptyList(), withOptions = true, template = PROPOSAL_TEMPLATE, motive = motive)
        return proposal
    }

    fun answerMessage(message: InboxMessage, optionId: String, byDefault: Boolean) {
        message.chosenOptionId = optionId
        message.answeredByDefault = byDefault
        message.read = true
        val proposal = message.originId?.let { findProposal(it) } ?: return
        if (proposal.status != ProposalStatus.PENDING) return
        val other = proposal.from
        message.senderId?.let { leader ->
            val outcome = when (optionId) {
                OPTION_ACCEPT -> InteractionOutcome.ACCEPTED
                OPTION_NEGOTIATE -> InteractionOutcome.PARTIAL
                else -> InteractionOutcome.REFUSED
            }
            ctx.memory.record(leader, "diplomacy", outcome, "l'accord envisagé (${mainClauseLabel(proposal)})")
        }
        when (optionId) {
            OPTION_ACCEPT -> {
                proposal.status = ProposalStatus.ACCEPTED
                sign(proposal)
            }
            OPTION_NEGOTIATE -> {
                // Le joueur prépare une contre-proposition depuis l'écran de diplomatie.
                remember(other, "NEGOTIATION_GOODWILL", "")
            }
            else -> {
                proposal.status = if (byDefault) ProposalStatus.EXPIRED else ProposalStatus.REFUSED
                val kind = if (byDefault) "PROPOSAL_IGNORED" else "PROPOSAL_REFUSED"
                remember(other, kind, mainClauseLabel(proposal))
            }
        }
    }

    fun breakAgreement(agreementId: String) {
        val agreement = ctx.state.diplomacy.agreements.firstOrNull { it.id == agreementId && it.active } ?: return
        agreement.active = false
        agreement.brokenBy = player
        agreement.parties.filter { it != player }.forEach {
            remember(it, "AGREEMENT_BROKEN", ctx.db.diplomacy.clause(agreement.clauses.first().type).label.lowercase())
        }
        ctx.notifications.post(NotificationCategory.DIPLOMACY, Urgency.IMPORTANT, "Accord rompu",
            "La rupture de l'accord sera durablement retenue par nos partenaires.")
    }

    private fun sign(proposal: Proposal) {
        val agreement = Agreement(
            ctx.state.newId("agr"), listOf(proposal.from, proposal.to), proposal.clauses, ctx.now,
            ctx.now.plusYears(proposal.durationYears),
        )
        ctx.state.diplomacy.agreements.add(agreement)
        val other = if (proposal.from == player) proposal.to else proposal.from
        remember(other, "AGREEMENT_SIGNED", mainClauseLabel(proposal))
        applyOneOffClauses(agreement)
        ctx.notifications.post(
            NotificationCategory.DIPLOMACY, Urgency.IMPORTANT,
            "Accord signé avec ${ctx.db.country(other).definition.name}",
            describer.describeAll(proposal.clauses, proposal.from, proposal.to, proposal.durationYears), other,
        )
    }

    /** Versements ponctuels (aides, investissements) effectués à la signature. */
    private fun applyOneOffClauses(agreement: Agreement) {
        AgreementEnactment(ctx).enact(agreement)
        for (clause in agreement.clauses) {
            val amount = clause.params["amountBillions"] ?: continue
            if (clause.type == "ARMS_SALE") continue
            val receiver = agreement.parties.first { it != clause.giver }
            val giverEconomy = ctx.state.countries.getValue(clause.giver).economy
            val receiverEconomy = ctx.state.countries.getValue(receiver).economy
            giverEconomy.pendingOneOffBillions += amount
            receiverEconomy.pendingOutputShock += amount / receiverEconomy.gdpBillions
            if (clause.giver == player) {
                ctx.effects.trigger(EffectSpec("opinion.national", ONE_OFF_OPINION_COST * amount), null, emptyMap(), agreement.id)
            }
        }
    }

    private fun sendResponse(
        proposal: Proposal,
        responseTag: String,
        reasons: List<String>,
        withOptions: Boolean = false,
        template: String = RESPONSE_TEMPLATE,
        motive: String = "",
    ) {
        val foreign = if (proposal.from == player) proposal.to else proposal.from
        val country = ctx.state.countries.getValue(foreign)
        val leader = ctx.state.characters[country.leaderId]
        val def = ctx.db.country(foreign).definition
        val builder = DialogueContextBuilder(ctx)
            .sender(leader, "${def.institutions.headOfGovernmentTitle} (${def.name})")
            .tag(responseTag)
            .variables(fr.president.engine.data.CountryNames(def).variables("foreign"))
            .variable("clauses", describer.describeAll(proposal.clauses, proposal.from, proposal.to, proposal.durationYears))
            .variable("reasons", reasons.joinToString(", ").ifBlank { "plusieurs points restent à éclaircir" })
            .variable("motive", motive)
        val relation = RelationCalculator(ctx).score(foreign, player)
        builder.tag(if (relation >= GOOD_RELATION) "relation:good" else if (relation <= BAD_RELATION) "relation:bad" else "relation:neutral")
        val composed = ctx.messages.compose(template, builder.build())
        val options = if (withOptions) {
            listOfNotNull(
                MessageOption(OPTION_ACCEPT, "Accepter", "Signer l'accord en l'état"),
                MessageOption(OPTION_NEGOTIATE, "Négocier", "Préparer une contre-proposition").takeIf { responseTag == "proposal:new" },
                MessageOption(OPTION_REFUSE, "Refuser", "Décliner poliment"),
            )
        } else emptyList()
        ctx.state.inbox.messages.add(
            InboxMessage(
                id = ctx.state.newId("msg"),
                senderId = leader?.id,
                senderLabel = "${leader?.fullName ?: ""}, ${def.institutions.headOfGovernmentTitle} (${def.name})",
                subject = composed.subject,
                body = composed.body,
                time = ctx.now,
                category = NotificationCategory.DIPLOMACY,
                origin = if (withOptions) MessageOrigin.PROPOSAL else MessageOrigin.DIPLOMATIC_RESPONSE,
                originId = proposal.id,
                options = options,
                deadline = if (withOptions) ctx.now.plusDays(ctx.db.diplomacy.evaluation.proposalExpiryDays) else null,
                defaultOptionId = if (withOptions) OPTION_REFUSE else null,
                focusId = foreign,
            ),
        )
        val title = when (responseTag) {
            "response:accepted" -> "${def.name} accepte votre proposition"
            "response:refused" -> "${def.name} refuse votre proposition"
            "response:countered" -> "${def.name} formule une contre-proposition"
            else -> "Le gouvernement ${def.adjective} vous fait une proposition"
        }
        ctx.notifications.post(NotificationCategory.DIPLOMACY, if (withOptions) Urgency.URGENT else Urgency.IMPORTANT, title,
            "Consultez votre messagerie pour le détail.", foreign)
    }

    fun remember(observer: String, kind: String, detail: String, weight: Double? = null) {
        val value = weight ?: ctx.db.diplomacy.memoryKind(kind)?.defaultWeight ?: 0.0
        ctx.state.diplomacy.relation(observer, player).memories.add(DiplomaticMemory(kind, value, ctx.now, detail))
    }

    private fun mainClauseLabel(p: Proposal) = ctx.db.diplomacy.clause(p.clauses.first().type).label.lowercase()

    fun findProposal(id: String): Proposal? = ctx.state.diplomacy.proposals.firstOrNull { it.id == id }

    companion object {
        const val OPTION_ACCEPT = "accept"
        const val OPTION_REFUSE = "refuse"
        const val OPTION_NEGOTIATE = "negotiate"
        const val RESPONSE_TEMPLATE = "diplomatic_response"
        const val PROPOSAL_TEMPLATE = "diplomatic_proposal"
        private const val GOOD_RELATION = 0.62
        private const val BAD_RELATION = 0.4
        /** Coût politique d'une aide versée à l'étranger, par milliard. */
        private const val ONE_OFF_OPINION_COST = -0.003
    }
}
