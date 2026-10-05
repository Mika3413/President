package fr.president.engine.economy

import fr.president.engine.data.Indexation
import fr.president.engine.data.TaxPayer

/** Calcule recettes, dépenses, intérêts et déficit à partir de l'état économique courant. */
object BudgetCalculator {

    fun recompute(economy: EconomyState) {
        val detailed = economy.budget
        if (detailed != null) recomputeDetailed(economy, detailed) else recomputeAggregate(economy)
        economy.interestBillions = economy.debtBillions * economy.averageDebtRate
        economy.deficitBillions = economy.spendingBillions + economy.interestBillions - economy.revenueBillions
    }

    private fun recomputeDetailed(economy: EconomyState, budget: BudgetState) {
        var revenue = 0.0
        for (item in budget.revenues.values) {
            val rateRatio = item.rate / item.referenceRate
            // Une partie de la hausse de taux est perdue en changements de comportement.
            val effectiveRatio = Math.pow(rateRatio, 1.0 - item.behaviouralLoss)
            item.amount = item.referenceAmount * effectiveRatio * TaxBaseIndex.of(item.base, economy)
            revenue += item.amount
        }
        revenue += (economy.fiscalAdjustmentBillions + economy.measureRevenueBillions) * TaxBaseIndex.of(fr.president.engine.data.TaxBase.GDP, economy)
        var spending = 0.0
        for (item in budget.spending.values) {
            item.amount = item.referenceAmount * item.policyFactor * indexFactor(item.indexation, economy)
            spending += item.amount
        }
        spending += economy.measureSpendingBillions * economy.priceLevel
        economy.revenueBillions = revenue
        economy.spendingBillions = spending
    }

    /** Recette d'un impôt à un taux donné (comportements et assiette compris). */
    fun taxAmount(item: RevenueItemState, rate: Double, economy: EconomyState): Double =
        item.referenceAmount * Math.pow(rate / item.referenceRate, 1.0 - item.behaviouralLoss) * TaxBaseIndex.of(item.base, economy)

    /** Dépense d'un poste pour un multiplicateur donné (indexation comprise). */
    fun spendingAmount(item: SpendingItemState, factor: Double, economy: EconomyState): Double =
        item.referenceAmount * factor * indexFactor(item.indexation, economy)

    private fun indexFactor(indexation: Indexation, economy: EconomyState): Double = when (indexation) {
        Indexation.NONE -> 1.0
        Indexation.PRICES -> economy.priceLevel
        Indexation.UNEMPLOYMENT -> economy.priceLevel * economy.unemployment / economy.reference.unemployment
    }

    private fun recomputeAggregate(economy: EconomyState) {
        val aggregate = economy.aggregate ?: return
        economy.revenueBillions = economy.gdpBillions * aggregate.revenueRatio
        economy.spendingBillions = economy.gdpBillions * aggregate.spendingRatio
    }

    /** Pression fiscale pesant sur un type de contribuable, en part du PIB. */
    fun taxBurden(economy: EconomyState, payer: TaxPayer): Double {
        val budget = economy.budget ?: return 0.0
        val mixedShare = 0.5
        val total = budget.revenues.values.sumOf {
            when (it.payer) {
                payer -> it.amount
                TaxPayer.MIXED -> it.amount * mixedShare
                else -> 0.0
            }
        }
        return total / economy.gdpBillions
    }

    /** Dépense réelle d'un domaine rapportée au niveau initial (1.0 = financement inchangé). */
    fun realFundingRatio(economy: EconomyState, domain: String): Double? {
        val items = economy.budget?.spending?.values?.filter { it.domain == domain } ?: return null
        if (items.isEmpty()) return null
        val reference = items.sumOf { it.referenceAmount } * economy.priceLevel
        return items.sumOf { it.amount } / reference
    }
}
