package fr.president.engine.diplomacy

import fr.president.engine.simulation.SimulationContext

/** Effets économiques récurrents des accords en vigueur. */
class AgreementEffects(private val ctx: SimulationContext) {

    /** Surcroît de croissance annuelle lié aux accords commerciaux d'un pays. */
    fun growthBoost(countryId: String): Double {
        var boost = SanctionsService(ctx).growthEffect(countryId)
        for (agreement in active(countryId)) {
            val partner = agreement.parties.first { it != countryId }
            for (clause in agreement.clauses.filter { it.type == ClauseValuator.TARIFFS }) {
                val def = ctx.db.diplomacy.clause(clause.type)
                val trade = ctx.db.country(countryId).definition.strategic.tradeWithPartnersBillions[partner] ?: 0.0
                val gdp = ctx.state.countries.getValue(countryId).economy.gdpBillions
                boost += (def.valuation["growthPerPercentOfTrade"] ?: 0.0) * (clause.params["percent"] ?: 0.0) * trade / gdp
            }
        }
        return boost
    }

    /** Électricité que le pays s'est engagé à livrer (TWh/an). */
    fun committedElectricity(countryId: String): Double = active(countryId)
        .flatMap { it.clauses }.filter { it.type == ClauseValuator.ELECTRICITY && it.giver == countryId }
        .sumOf { it.params["volumeTWh"] ?: 0.0 }

    /** Recettes annuelles (milliards) des ventes d'électricité contractuelles. */
    fun electricityRevenue(countryId: String): Double {
        val price = ctx.db.economyParameters.electricityExportPriceEurPerMWh
        return active(countryId).flatMap { it.clauses }
            .filter { it.type == ClauseValuator.ELECTRICITY && it.giver == countryId }
            .sumOf { (it.params["volumeTWh"] ?: 0.0) * MWH_PER_TWH * price * (it.params["pricePercent"] ?: PERCENT) / PERCENT / EUR_PER_BILLION }
    }

    private fun active(countryId: String) =
        ctx.state.diplomacy.agreements.filter { it.active && countryId in it.parties }

    private companion object {
        const val MWH_PER_TWH = 1_000_000.0
        const val EUR_PER_BILLION = 1_000_000_000.0
        const val PERCENT = 100.0
    }
}
