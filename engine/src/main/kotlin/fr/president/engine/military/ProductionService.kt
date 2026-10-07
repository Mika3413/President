package fr.president.engine.military

import fr.president.engine.effects.EffectSpec
import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import fr.president.engine.simulation.ScheduledAction
import fr.president.engine.simulation.SimulationContext

/** Production d'unités, achats de munitions et de carburant, économie de guerre, mobilisation. */
class ProductionService(private val ctx: SimulationContext) {
    private val p get() = ctx.db.militaryParameters
    private val player get() = ctx.state.player.countryId

    fun buildable() = ctx.db.unitTypes.values.filter { it.buildable }

    fun order(typeId: String): Result<ProductionOrder> = runCatching {
        val type = ctx.db.unitType(typeId)
        require(type.buildable) { "Ce type d'unité ne peut pas être commandé." }
        val order = ProductionOrder(ctx.state.newId("prod"), typeId, ctx.now.plusDays(buildDays(typeId)), type.costBillions)
        ctx.state.military.production += order
        ctx.effects.trigger(EffectSpec("budget.oneOff", type.costBillions, days = type.buildDays.toDouble()), null, emptyMap(), order.id)
        ctx.scheduler.schedule(ScheduledAction.UnitDelivery(order.readyAt, order.id))
        // Commandes publiques : un peu d'activité industrielle.
        ctx.effects.trigger(EffectSpec("economy.output", type.costBillions / ctx.state.playerCountry.economy.gdpBillions * INDUSTRY_MULTIPLIER, days = type.buildDays.toDouble()), null, emptyMap(), order.id)
        order
    }

    /** Délai de production, raccourci par les casernes et centres d'entraînement. */
    fun buildDays(typeId: String): Long {
        val type = ctx.db.unitType(typeId)
        val bonus = if (type.domain == fr.president.engine.data.Domain.LAND) FortificationService(ctx).productionBonus(player) else 0.0
        return (type.buildDays * (1 - bonus)).toLong().coerceAtLeast(1)
    }

    fun deliver(orderId: String) {
        val order = ctx.state.military.production.firstOrNull { it.id == orderId } ?: return
        ctx.state.military.production.remove(order)
        val type = ctx.db.unitType(order.unitType)
        val unit = MilitarySetup(ctx).createUnit(player, type, homeZoneFor(type.domain), ctx.db.militaryParameters.aiUnitStartReadiness)
        unit.experience = (unit.experience + FortificationService(ctx).trainingBonus(player)).coerceAtMost(1.0)
        ctx.notifications.post(NotificationCategory.MILITARY, Urgency.IMPORTANT, "Nouvelle unité : ${unit.name}",
            "L'unité rejoint les forces armées.", unit.id)
    }

    fun purchase(ammunition: Boolean) {
        ctx.state.playerCountry.economy.pendingOneOffBillions += p.purchaseCostBillions
        ctx.scheduler.schedule(ScheduledAction.StockDelivery(ctx.now.plusDays(p.purchaseDays.toLong()),
            if (ammunition) PURCHASE_AMOUNT else 0.0, if (ammunition) 0.0 else PURCHASE_AMOUNT))
    }

    fun receiveStocks(ammunition: Double, fuel: Double) {
        val s = ctx.state.military.stocks
        s.ammunition = (s.ammunition + ammunition).coerceAtMost(1.0)
        s.fuel = (s.fuel + fuel).coerceAtMost(1.0)
        ctx.notifications.post(NotificationCategory.MILITARY, Urgency.INFO, "Livraison reçue",
            if (ammunition > 0) "Les stocks de munitions ont été complétés." else "Les stocks de carburant ont été complétés.")
    }

    fun setWarEconomy(enabled: Boolean) {
        ctx.state.military.stocks.warEconomy = enabled
        if (enabled) ctx.effects.apply("economy.businessConfidence", WAR_ECONOMY_CONFIDENCE)
    }

    fun mobilize(): Result<Unit> = runCatching {
        val s = ctx.state.military.stocks
        require(s.reservists <= 0) { "Une mobilisation est déjà en cours ou effective." }
        s.reservists = p.mobilizationUnits
        ctx.state.playerCountry.economy.pendingOneOffBillions += p.mobilizationCostBillions
        val atWar = Geopolitics(ctx).isAtWar(player)
        ctx.state.opinion.groups.values.forEach { it.shock += if (atWar) MOBILIZATION_AT_WAR else MOBILIZATION_IN_PEACE }
        ctx.effects.trigger(EffectSpec("economy.output", MOBILIZATION_OUTPUT, days = p.mobilizationDays.toDouble()), null, emptyMap(), "mobilization")
        ctx.scheduler.schedule(ScheduledAction.MobilizationComplete(ctx.now.plusDays(p.mobilizationDays.toLong()), p.mobilizationUnits))
        ctx.notifications.post(NotificationCategory.MILITARY, Urgency.URGENT, "Mobilisation décrétée",
            "Les réservistes rejoignent leurs unités : ${p.mobilizationUnits} brigades seront opérationnelles dans ${p.mobilizationDays} jours.")
    }

    fun completeMobilization(units: Int) {
        val type = ctx.db.unitType(RESERVE)
        repeat(units) { MilitarySetup(ctx).createUnit(player, type, homeZoneFor(type.domain), RESERVE_READINESS) }
        ctx.notifications.post(NotificationCategory.MILITARY, Urgency.IMPORTANT, "Mobilisation achevée", "$units brigades de réserve sont opérationnelles.")
    }

    fun demobilize() {
        ctx.state.military.units.values.filter { it.countryId == player && it.type == RESERVE && !it.destroyed && !it.inCombat }
            .forEach { it.destroyed = true }
        ctx.state.military.stocks.reservists = 0
        ctx.effects.trigger(EffectSpec("economy.output", -MOBILIZATION_OUTPUT, days = DEMOBILIZATION_DAYS), null, emptyMap(), "demobilization")
    }

    private fun homeZoneFor(domain: fr.president.engine.data.Domain): String {
        val bases = ctx.playerData.military?.bases.orEmpty()
        val base = bases.firstOrNull { (domain == fr.president.engine.data.Domain.SEA) == (it.branch == "Marine") } ?: bases.first()
        return MilitarySetup(ctx).zoneFor(player, base.lon, base.lat, domain == fr.president.engine.data.Domain.SEA)
    }

    private companion object {
        const val RESERVE = "RESERVE_BRIGADE"
        const val RESERVE_READINESS = 0.5
        const val PURCHASE_AMOUNT = 0.25
        const val INDUSTRY_MULTIPLIER = 0.5
        const val WAR_ECONOMY_CONFIDENCE = -0.01
        const val MOBILIZATION_AT_WAR = -0.01
        const val MOBILIZATION_IN_PEACE = -0.05
        const val MOBILIZATION_OUTPUT = -0.002
        const val DEMOBILIZATION_DAYS = 60.0
    }
}
