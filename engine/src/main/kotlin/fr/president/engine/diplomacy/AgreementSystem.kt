package fr.president.engine.diplomacy

import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import fr.president.engine.simulation.Cadence
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.simulation.SimulationSystem

/**
 * Suivi mensuel des accords : engagements électriques, recettes, respect des livraisons,
 * anniversaires (confiance accrue) et expiration.
 */
class AgreementSystem : SimulationSystem {
    override val name = "agreements"
    override val cadence = Cadence.MONTHLY

    override fun run(ctx: SimulationContext) {
        val player = ctx.state.player.countryId
        val effects = AgreementEffects(ctx)
        val service = DiplomacyService(ctx)
        ctx.state.energy.committedExportTWh = effects.committedElectricity(player)
        val economy = ctx.state.playerCountry.economy
        economy.pendingOutputShock += effects.electricityRevenue(player) / economy.gdpBillions / MONTHS

        for (agreement in ctx.state.diplomacy.agreements.filter { it.active }) {
            val partners = agreement.parties.filter { it != player }
            if (ctx.now >= agreement.expiresAt) {
                agreement.active = false
                partners.forEach { service.remember(it, "AGREEMENT_RESPECTED", "accord mené à son terme") }
                ctx.notifications.post(NotificationCategory.DIPLOMACY, Urgency.INFO, "Accord arrivé à échéance",
                    "L'accord avec ${partners.joinToString { ctx.db.country(it).definition.name }} a pris fin. Il peut être renégocié.")
                continue
            }
            val monthsSinceSigning = ctx.now.monthIndex - agreement.signedAt.monthIndex
            if (monthsSinceSigning > 0 && monthsSinceSigning % MONTHS.toInt() == 0) {
                partners.forEach { service.remember(it, "AGREEMENT_RESPECTED", "engagements tenus") }
            }
        }
        checkDeliveries(ctx, service)
        RelationPruner.prune(ctx)
    }

    /** Si la production ne suffit plus à honorer les contrats, les partenaires s'en souviennent. */
    private fun checkDeliveries(ctx: SimulationContext, service: DiplomacyService) {
        if (ctx.state.energy.committedExportTWh <= 0 || ctx.state.energy.margin >= 0) return
        val receivers = ctx.state.diplomacy.agreements.filter { a -> a.active && a.clauses.any { it.type == ClauseValuator.ELECTRICITY && it.giver == ctx.state.player.countryId } }
            .flatMap { it.parties }.filter { it != ctx.state.player.countryId }.distinct()
        receivers.forEach { service.remember(it, "DELIVERY_SHORTFALL", "livraisons d'électricité") }
        ctx.notifications.post(NotificationCategory.ENERGY, Urgency.URGENT, "Livraisons d'électricité compromises",
            "La production nationale ne permet plus d'honorer nos contrats d'exportation. Nos partenaires s'inquiètent.")
    }

    private companion object {
        const val MONTHS = 12.0
    }
}

/** Nettoyage périodique des souvenirs diplomatiques devenus négligeables. */
object RelationPruner {
    fun prune(ctx: SimulationContext) {
        val calculator = RelationCalculator(ctx)
        ctx.state.diplomacy.relations.keys.toList().forEach { key ->
            val (observer, target) = key.split('>')
            calculator.prune(observer, target)
        }
    }
}
