package fr.president.engine.government

import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import fr.president.engine.politics.CharacterRole
import fr.president.engine.simulation.Cadence
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.simulation.SimulationSystem
import fr.president.engine.util.approach
import fr.president.engine.util.clamp01
import kotlin.math.abs

/**
 * Vie mensuelle du gouvernement : soutien parlementaire, loyauté, expérience
 * et éventuelles démissions de ministres.
 */
class GovernmentSystem : SimulationSystem {
    override val name = "government"
    override val cadence = Cadence.MONTHLY

    override fun run(ctx: SimulationContext) {
        val def = ctx.playerData.government ?: return
        updateParliament(ctx)
        val approval = ctx.state.opinion.nationalApproval
        val drift = def.ministerDrift
        for ((ministry, ministerId) in ctx.state.government.ministers.toMap()) {
            val minister = ctx.state.characters[ministerId] ?: continue
            val loyaltyTarget = (minister.relationWithPlayer + drift.loyaltyApprovalWeight * (approval - NEUTRAL)).clamp01()
            minister.loyalty = approach(minister.loyalty, loyaltyTarget, drift.loyaltyAdjustmentMonthly)
            minister.experience = (minister.experience + drift.experienceGainMonthly).clamp01()
            minister.popularity = approach(minister.popularity, (minister.competence + approval) / 2, POPULARITY_DRIFT)
            if (minister.loyalty < drift.resignationLoyaltyThreshold) resign(ctx, ministry, ministerId)
        }
    }

    private fun updateParliament(ctx: SimulationContext) {
        val parliament = ParliamentService(ctx)
        if (parliament.isActive) {
            ctx.state.government.parliamentSupport = approach(ctx.state.government.parliamentSupport, parliament.supportTarget(), PARLIAMENT_DRIFT)
            parliament.monthlyCheck()
            return
        }
        val p = ctx.playerData.government!!.parliament
        val president = ctx.state.characters.getValue(ctx.state.player.presidentId)
        val pm = ctx.state.government.primeMinisterId?.let { ctx.state.characters[it] }
        val distance = pm?.let { abs(it.economicLeaning - president.economicLeaning) } ?: 0.0
        // Un Premier ministre d'une autre sensibilité élargit la majorité mais fragilise la cohésion.
        val target = p.baseSupport + p.approvalWeight * (ctx.state.opinion.nationalApproval - NEUTRAL) +
            p.openingBonus * distance - p.primeMinisterDistanceWeight * distance * distance
        ctx.state.government.parliamentSupport = approach(ctx.state.government.parliamentSupport, target.clamp01(), PARLIAMENT_DRIFT)
    }

    private fun resign(ctx: SimulationContext, ministry: String, ministerId: String) {
        val minister = ctx.state.characters.getValue(ministerId)
        val title = ctx.playerData.government!!.ministries.first { it.id == ministry }.title
        ctx.state.government.ministers.remove(ministry)
        minister.role = CharacterRole.FORMER
        minister.active = false
        ctx.state.opinion.groups.values.forEach { it.shock -= RESIGNATION_SHOCK }
        ctx.notifications.post(
            NotificationCategory.GOVERNMENT, Urgency.URGENT,
            "Démission : ${minister.fullName}",
            "${minister.fullName} quitte le poste de $title, invoquant des désaccords avec la présidence. Le ministère est géré par intérim : nommez un successeur.",
        )
        ctx.log("government", "Démission de ${minister.fullName} ($ministry), loyauté ${"%.2f".format(minister.loyalty)}")
    }

    private companion object {
        const val NEUTRAL = 0.5
        const val POPULARITY_DRIFT = 0.1
        const val PARLIAMENT_DRIFT = 0.3
        const val RESIGNATION_SHOCK = 0.01
    }
}
