package fr.president.engine.diplomacy

import fr.president.engine.military.Geopolitics
import fr.president.engine.military.MilitarySetup
import fr.president.engine.military.WarService
import fr.president.engine.simulation.SimulationContext

/** Effets immédiats des clauses de guerre, de sécurité et d'armement à la signature. */
class AgreementEnactment(private val ctx: SimulationContext) {
    private val geo = Geopolitics(ctx)

    fun enact(agreement: Agreement) {
        val (a, b) = agreement.parties[0] to agreement.parties[1]
        for (c in agreement.clauses) {
            when (c.type) {
                "CEASEFIRE" -> geo.warBetween(a, b)?.let { WarService(ctx).ceasefire(it, (c.params["days"] ?: DEFAULT_DAYS).toInt()) }
                "PEACE_TREATY" -> geo.warBetween(a, b)?.let {
                    WarService(ctx).peace(it, (c.params["keepOccupied"] ?: 0.0) >= 1, "Traité de paix entre ${name(a)} et ${name(b)}.")
                }
                "PRISONER_EXCHANGE" -> ctx.state.opinion.groups.values.forEach { it.shock += PRISONER_OPINION }
                "MILITARY_AID" -> militaryAid(c.giver, other(agreement, c.giver), c.params["amountBillions"] ?: 0.0)
                "ARMS_SALE" -> armsSale(c.giver, other(agreement, c.giver), c.params["amountBillions"] ?: 0.0)
            }
        }
    }

    private fun militaryAid(giver: String, receiver: String, amount: Double) {
        val player = ctx.state.player.countryId
        if (giver == player) {
            val s = ctx.state.military.stocks
            s.ammunition = (s.ammunition - amount * STOCK_PER_BILLION).coerceAtLeast(0.0)
        }
        val units = ctx.state.military.units.values.filter { it.countryId == receiver && !it.destroyed }
        units.forEach {
            it.ammunition = (it.ammunition + amount * UNIT_BOOST_PER_BILLION).coerceAtMost(1.0)
            it.strength = (it.strength + amount * UNIT_BOOST_PER_BILLION / 2).coerceAtMost(1.0)
        }
        ctx.state.diplomacy.relation(receiver, giver).memories += DiplomaticMemory("MILITARY_SUPPORT", MILITARY_SUPPORT_WEIGHT, ctx.now, "aide militaire")
    }

    /** L'acheteur paie, le vendeur encaisse ; les équipements renforcent l'acheteur. */
    private fun armsSale(seller: String, buyer: String, amount: Double) {
        val sellerEconomy = ctx.state.countries.getValue(seller).economy
        val buyerEconomy = ctx.state.countries.getValue(buyer).economy
        buyerEconomy.pendingOneOffBillions += amount
        sellerEconomy.pendingOutputShock += amount / sellerEconomy.gdpBillions * INDUSTRY_SHARE
        if (seller == ctx.state.player.countryId) sellerEconomy.pendingOneOffBillions -= amount * STATE_SHARE
        val units = (amount / BILLIONS_PER_UNIT).toInt()
        if (units > 0 && ctx.db.zones.ownedBy(buyer).isNotEmpty()) {
            val capital = ctx.db.country(buyer).definition.strategic.capital
            val setup = MilitarySetup(ctx)
            val zone = capital?.let { setup.zoneFor(buyer, it.lon, it.lat, false) } ?: ctx.db.zones.ownedBy(buyer).first().id
            repeat(units) { setup.createUnit(buyer, ctx.db.unitType(SOLD_TYPE), zone, ctx.db.militaryParameters.aiUnitStartReadiness) }
        }
    }

    private fun other(a: Agreement, c: String) = a.parties.first { it != c }
    private fun name(c: String) = ctx.db.country(c).definition.name

    private companion object {
        const val DEFAULT_DAYS = 90.0
        const val PRISONER_OPINION = 0.01
        const val STOCK_PER_BILLION = 0.03
        const val UNIT_BOOST_PER_BILLION = 0.04
        const val MILITARY_SUPPORT_WEIGHT = 0.12
        const val INDUSTRY_SHARE = 0.6
        /** Part du contrat revenant directement à l'État (entreprises publiques, taxes). */
        const val STATE_SHARE = 0.2
        const val BILLIONS_PER_UNIT = 3.0
        const val SOLD_TYPE = "MECHANIZED_BRIGADE"
    }
}
