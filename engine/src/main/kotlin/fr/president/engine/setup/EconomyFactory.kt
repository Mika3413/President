package fr.president.engine.setup

import fr.president.engine.data.EconomySnapshot
import fr.president.engine.data.TaxPayer
import fr.president.engine.economy.AggregateBudgetState
import fr.president.engine.economy.BudgetCalculator
import fr.president.engine.economy.BudgetState
import fr.president.engine.economy.EconomyReference
import fr.president.engine.economy.EconomyState
import fr.president.engine.economy.RevenueItemState
import fr.president.engine.economy.SpendingItemState

/** Copie un snapshot économique en état de simulation autonome. */
object EconomyFactory {

    fun create(s: EconomySnapshot): EconomyState {
        val budget = s.budget?.let { b ->
            BudgetState(
                revenues = b.revenues.associate {
                    it.id to RevenueItemState(it.id, it.base, it.payer, it.amountBillions, it.rate, it.rate, it.behaviouralLoss, it.amountBillions)
                }.toMutableMap(),
                spending = b.spending.associate {
                    it.id to SpendingItemState(it.id, it.ministry, it.domain, it.indexation, it.amountBillions, amount = it.amountBillions)
                }.toMutableMap(),
            )
        }
        val provisional = build(s, budget, EconomyReference(s.gdpBillions, s.unemployment, s.publicDebtBillions / s.gdpBillions, 0.0, 0.0))
        BudgetCalculator.recompute(provisional)
        val reference = provisional.reference.copy(
            householdTaxBurden = BudgetCalculator.taxBurden(provisional, TaxPayer.HOUSEHOLDS),
            businessTaxBurden = BudgetCalculator.taxBurden(provisional, TaxPayer.BUSINESSES),
        )
        return build(s, budget, reference).also { BudgetCalculator.recompute(it) }
    }

    private fun build(s: EconomySnapshot, budget: BudgetState?, reference: EconomyReference) = EconomyState(
        gdpBillions = s.gdpBillions,
        realGrowth = s.realGrowth,
        potentialGrowth = s.potentialGrowth,
        inflation = s.inflation,
        inflationTarget = s.inflationTarget,
        unemployment = s.unemployment,
        naturalUnemployment = s.naturalUnemployment,
        debtBillions = s.publicDebtBillions,
        averageDebtRate = s.averageDebtRate,
        marketRate = s.averageDebtRate,
        riskFreeRate = s.riskFreeRate,
        consumerConfidence = s.consumerConfidence,
        businessConfidence = s.businessConfidence,
        averageNetMonthlyWage = s.averageNetMonthlyWage,
        tradeBalanceBillions = s.tradeBalanceBillions,
        reference = reference,
        budget = budget,
        aggregate = s.aggregateBudget?.let { AggregateBudgetState(it.revenueRatio, it.spendingRatio) },
    )
}
