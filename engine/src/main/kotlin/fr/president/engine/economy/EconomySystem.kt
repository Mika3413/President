package fr.president.engine.economy

import fr.president.engine.diplomacy.AgreementEffects
import fr.president.engine.simulation.Cadence
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.simulation.SimulationSystem

/** Pas mensuel de l'économie de chaque pays, joueur comme IA. */
class EconomySystem : SimulationSystem {
    override val name = "economy"
    override val cadence = Cadence.MONTHLY

    override fun run(ctx: SimulationContext) {
        val model = MacroEconomyModel(ctx.db.economyParameters)
        val agreementEffects = AgreementEffects(ctx)
        for (country in ctx.state.countries.values) {
            val isPlayer = country.id == ctx.state.player.countryId
            val inputs = MacroEconomyModel.MonthInputs(
                governmentApproval = if (isPlayer) ctx.state.opinion.nationalApproval else country.leaderApproval,
                externalGrowthBoost = agreementEffects.growthBoost(country.id),
            )
            model.step(country.economy, inputs, ctx.rng)
        }
        if (ctx.now.month == JANUARY) {
            ctx.state.countries.values.forEach { it.economy.oneOffThisYearBillions = 0.0 }
        }
        val e = ctx.state.playerCountry.economy
        ctx.log(
            "economy",
            "Croissance %.2f%%, chômage %.2f%%, inflation %.2f%%, déficit %.1f Md (%.2f%% PIB), dette %.1f%%".format(
                e.realGrowth * PCT, e.unemployment * PCT, e.inflation * PCT, e.deficitBillions, e.deficitRatio * PCT, e.debtRatio * PCT,
            ),
        )
    }

    private companion object {
        const val JANUARY = 1
        const val PCT = 100.0
    }
}
