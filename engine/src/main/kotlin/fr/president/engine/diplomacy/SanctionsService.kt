package fr.president.engine.diplomacy

import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import fr.president.engine.simulation.SimulationContext

/** Sanctions économiques : coupent une partie des échanges et pèsent sur les deux économies. */
class SanctionsService(private val ctx: SimulationContext) {

    fun impose(by: String, target: String, announce: Boolean = true) {
        if (isSanctioning(by, target)) return
        ctx.state.diplomacy.sanctions += Sanction(by, target, ctx.now)
        ctx.state.diplomacy.relation(target, by).memories += DiplomaticMemory("SANCTION", SANCTION_WEIGHT, ctx.now, "sanctions économiques")
        ctx.state.diplomacy.agreements.filter { it.active && by in it.parties && target in it.parties && it.clauses.any { c -> c.type == ClauseValuator.TARIFFS } }
            .forEach { it.active = false }
        if (announce) ctx.notifications.post(NotificationCategory.DIPLOMACY, Urgency.IMPORTANT,
            "${name(by)} sanctionne ${name(target)}", "Les échanges commerciaux sont fortement réduits.", target)
        if (by == ctx.state.player.countryId) followers(target)
    }

    fun lift(by: String, target: String) {
        ctx.state.diplomacy.sanctions.removeAll { it.by == by && it.target == target }
        ctx.state.diplomacy.relation(target, by).memories += DiplomaticMemory("SANCTION", LIFT_WEIGHT, ctx.now, "levée des sanctions")
    }

    fun isSanctioning(by: String, target: String) = ctx.state.diplomacy.sanctions.any { it.by == by && it.target == target }

    /** Les partenaires hostiles à la cible s'associent aux sanctions du joueur. */
    private fun followers(target: String) {
        val calc = RelationCalculator(ctx)
        ctx.state.countries.keys.filter { it != target && it != ctx.state.player.countryId }.forEach { c ->
            val towardsPlayer = calc.score(c, ctx.state.player.countryId)
            val towardsTarget = calc.score(c, target)
            if (towardsPlayer - towardsTarget > FOLLOW_GAP && ctx.rng.chance(FOLLOW_CHANCE)) impose(c, target, announce = false)
        }
        val count = ctx.state.diplomacy.sanctions.count { it.target == target }
        ctx.notifications.post(NotificationCategory.DIPLOMACY, Urgency.INFO, "Sanctions contre ${name(target)}", "$count pays appliquent désormais des sanctions.")
    }

    /** Effet annuel sur la croissance d'un pays (négatif) dû aux sanctions qu'il subit ou impose. */
    fun growthEffect(country: String): Double {
        val gdp = ctx.state.countries.getValue(country).economy.gdpBillions
        var effect = 0.0
        for (s in ctx.state.diplomacy.sanctions) {
            val partner = when (country) { s.target -> s.by; s.by -> s.target; else -> continue }
            val trade = ctx.db.country(country).definition.strategic.tradeWithPartnersBillions[partner]
                ?: ctx.db.country(partner).definition.strategic.tradeWithPartnersBillions[country] ?: DEFAULT_TRADE
            effect -= trade / gdp * (if (country == s.target) TARGET_LOSS else SANCTIONER_LOSS)
        }
        return effect
    }

    private fun name(c: String) = ctx.db.country(c).definition.name

    private companion object {
        const val SANCTION_WEIGHT = -0.15
        const val LIFT_WEIGHT = 0.08
        const val FOLLOW_GAP = 0.15
        const val FOLLOW_CHANCE = 0.6
        const val DEFAULT_TRADE = 2.0
        const val TARGET_LOSS = 0.3
        const val SANCTIONER_LOSS = 0.15
    }
}
