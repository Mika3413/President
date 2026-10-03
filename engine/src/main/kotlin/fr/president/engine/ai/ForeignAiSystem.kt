package fr.president.engine.ai

import fr.president.engine.diplomacy.Clause
import fr.president.engine.diplomacy.ClauseValuator
import fr.president.engine.diplomacy.DiplomacyService
import fr.president.engine.diplomacy.ProposalStatus
import fr.president.engine.diplomacy.RelationCalculator
import fr.president.engine.politics.Traits
import fr.president.engine.simulation.Cadence
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.simulation.SimulationSystem
import fr.president.engine.util.approach
import fr.president.engine.util.clamp01
import fr.president.engine.world.CountryState
import kotlin.math.roundToInt

/**
 * Décisions des gouvernements étrangers. Chaque pays poursuit ses propres objectifs
 * (énergie, commerce, situation économique) à partir de ce qu'il perçoit,
 * et journalise son raisonnement pour le debug.
 */
class ForeignAiSystem : SimulationSystem {
    override val name = "foreign-ai"
    override val cadence = Cadence.DAILY

    override fun run(ctx: SimulationContext) {
        val interval = ctx.db.config.simulation.aiDecisionIntervalDays.toDouble()
        for (country in ctx.state.countries.values) {
            if (country.id == ctx.state.player.countryId) continue
            val due = country.nextAiDecision
            if (due != null && ctx.now < due) continue
            country.nextAiDecision = ctx.now.plusDays(interval * ctx.rng.nextDouble(MIN_INTERVAL_FACTOR, MAX_INTERVAL_FACTOR))
            if (due == null) continue
            updateDomesticSituation(country)
            WarDecisions(ctx).run(country.id)
            decide(ctx, country)
        }
    }

    private fun updateDomesticSituation(country: CountryState) {
        val e = country.economy
        val target = (BASE_APPROVAL - UNEMPLOYMENT_WEIGHT * (e.unemployment - e.naturalUnemployment) -
            INFLATION_WEIGHT * (e.inflation - e.inflationTarget) + GROWTH_WEIGHT * e.realGrowth).clamp01()
        country.leaderApproval = approach(country.leaderApproval, target, APPROVAL_ADJUSTMENT)
    }

    private fun decide(ctx: SimulationContext, country: CountryState) {
        val player = ctx.state.player.countryId
        val relation = RelationCalculator(ctx).score(country.id, player)
        if (fr.president.engine.military.Geopolitics(ctx).atWar(country.id, player)) return
        val minimum = ctx.db.diplomacy.evaluation.aiProposalRelationMinimum
        if (relation < minimum) {
            ctx.log("ai", "${country.id} : relation trop faible (%.2f) pour proposer quoi que ce soit".format(relation))
            return
        }
        if (hasPendingWithPlayer(ctx, country.id)) return
        if (recentlyRebuffed(ctx, country.id)) {
            ctx.log("ai", "${country.id} : attend avant de relancer la France après un refus récent")
            return
        }
        val leader = ctx.state.characters.getValue(country.leaderId)
        val need = if (ctx.db.country(country.id).definition.strategic.electricityInterconnected) electricityNeed(ctx, country) else 0.0
        val geo = fr.president.engine.military.Geopolitics(ctx)
        when {
            geo.isAtWar(country.id) && !geo.isAtWar(player) && relation > AID_RELATION && !geo.atWar(country.id, player) -> {
                ctx.log("ai", "${country.id} en guerre demande une aide militaire à la France")
                DiplomacyService(ctx).aiPropose(
                    country.id,
                    listOf(Clause("MILITARY_AID", player, mapOf("amountBillions" to AID_REQUEST))),
                    1, "tenir face à l'agression dont nous sommes victimes",
                )
            }
            need >= ELECTRICITY_NEED_THRESHOLD && !hasAgreement(ctx, country.id, ClauseValuator.ELECTRICITY) -> {
                val volume = (need * NEED_COVERAGE).roundToInt().coerceIn(MIN_VOLUME, MAX_VOLUME).toDouble()
                // Un dirigeant exigeant propose un prix plus bas.
                val price = (PRICE_BASE - PRICE_TOUGHNESS * leader.trait(Traits.TOUGHNESS)).roundTo(PRICE_STEP)
                val years = if (leader.trait(Traits.CAUTION) > CAUTIOUS) SHORT_DEAL else LONG_DEAL
                ctx.log("ai", "${country.id} : besoin électrique %.1f TWh, propose %.0f TWh à %.0f %%".format(need, volume, price))
                DiplomacyService(ctx).aiPropose(
                    country.id,
                    listOf(Clause(ClauseValuator.ELECTRICITY, player, mapOf("volumeTWh" to volume, "pricePercent" to price))),
                    years, "sécuriser notre approvisionnement électrique",
                )
            }
            relation > TRADE_RELATION && !hasAgreement(ctx, country.id, ClauseValuator.TARIFFS) &&
                ctx.rng.chance(TRADE_INITIATIVE_CHANCE * leader.trait(Traits.OPENNESS)) -> {
                ctx.log("ai", "${country.id} : relation bonne (%.2f), propose une baisse tarifaire".format(relation))
                DiplomacyService(ctx).aiPropose(
                    country.id,
                    listOf(Clause(ClauseValuator.TARIFFS, country.id, mapOf("percent" to TRADE_PERCENT))),
                    LONG_DEAL, "approfondir nos échanges commerciaux",
                )
            }
            ctx.db.country(country.id).definition.strategic.militaryBudgetBillions > ARMS_BUYER_BUDGET && relation > TRADE_RELATION &&
                !ctx.db.country(country.id).definition.strategic.nuclear && ctx.rng.chance(ARMS_CHANCE * leader.trait(Traits.MILITARISM)) -> {
                val amount = (ARMS_MIN + ctx.rng.nextDouble() * ARMS_SPREAD).roundTo(1.0)
                ctx.log("ai", "${country.id} souhaite acheter des armements français (%.0f Md€)".format(amount))
                DiplomacyService(ctx).aiPropose(
                    country.id, listOf(Clause("ARMS_SALE", player, mapOf("amountBillions" to amount))), 1,
                    "moderniser nos forces armées avec des équipements français",
                )
            }
            country.economy.realGrowth < CRISIS_GROWTH && relation > AID_RELATION && ctx.rng.chance(ARMS_CHANCE) -> {
                ctx.log("ai", "${country.id} en crise économique demande une aide financière")
                DiplomacyService(ctx).aiPropose(
                    country.id, listOf(Clause(ClauseValuator.AID, player, mapOf("amountBillions" to AID_REQUEST))), 1,
                    "surmonter la grave crise économique que nous traversons",
                )
            }
            else -> ctx.log("ai", "${country.id} : aucune initiative (besoin électrique %.1f TWh, relation %.2f)".format(need, relation))
        }
    }

    private fun electricityNeed(ctx: SimulationContext, country: CountryState): Double {
        val received = ctx.state.diplomacy.agreements.filter { it.active && country.id in it.parties }
            .flatMap { it.clauses }.filter { it.type == ClauseValuator.ELECTRICITY && it.giver != country.id }
            .sumOf { it.params["volumeTWh"] ?: 0.0 }
        return -country.electricityBalanceTWh - received
    }

    private fun hasPendingWithPlayer(ctx: SimulationContext, id: String) = ctx.state.diplomacy.proposals.any {
        it.status == ProposalStatus.PENDING && (it.from == id || it.to == id)
    }

    private fun recentlyRebuffed(ctx: SimulationContext, id: String): Boolean {
        val cooldown = ctx.db.diplomacy.evaluation.aiProposalCooldownDays
        return ctx.state.diplomacy.proposals.any {
            it.from == id && (it.status == ProposalStatus.REFUSED || it.status == ProposalStatus.EXPIRED) &&
                it.createdAt.daysUntil(ctx.now) < cooldown
        }
    }

    private fun hasAgreement(ctx: SimulationContext, id: String, type: String) = ctx.state.diplomacy.agreements.any { a ->
        a.active && id in a.parties && a.clauses.any { it.type == type }
    }

    private fun Double.roundTo(step: Double) = (this / step).roundToInt() * step

    private companion object {
        const val MIN_INTERVAL_FACTOR = 0.5
        const val MAX_INTERVAL_FACTOR = 1.5
        const val BASE_APPROVAL = 0.45
        const val UNEMPLOYMENT_WEIGHT = 2.0
        const val INFLATION_WEIGHT = 2.0
        const val GROWTH_WEIGHT = 3.0
        const val APPROVAL_ADJUSTMENT = 0.2
        const val ELECTRICITY_NEED_THRESHOLD = 5.0
        const val NEED_COVERAGE = 0.5
        const val MIN_VOLUME = 2
        const val MAX_VOLUME = 25
        const val PRICE_BASE = 100.0
        const val PRICE_TOUGHNESS = 15.0
        const val PRICE_STEP = 5.0
        const val CAUTIOUS = 0.6
        const val SHORT_DEAL = 3
        const val LONG_DEAL = 5
        const val TRADE_RELATION = 0.6
        const val TRADE_INITIATIVE_CHANCE = 0.3
        const val TRADE_PERCENT = 3.0
        const val AID_RELATION = 0.55
        const val AID_REQUEST = 2.0
        const val ARMS_BUYER_BUDGET = 5.0
        const val ARMS_CHANCE = 0.12
        const val ARMS_MIN = 2.0
        const val ARMS_SPREAD = 6.0
        const val CRISIS_GROWTH = -0.015
    }
}
