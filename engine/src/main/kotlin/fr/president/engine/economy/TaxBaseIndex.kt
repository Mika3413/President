package fr.president.engine.economy

import fr.president.engine.data.TaxBase

/** Évolution de chaque assiette fiscale par rapport au début de partie. */
object TaxBaseIndex {
    private const val PROFIT_FLOOR = 0.6
    private const val PROFIT_CONFIDENCE_WEIGHT = 0.8
    private const val NEUTRAL_CONFIDENCE = 0.5
    private const val CONSUMPTION_CONFIDENCE_WEIGHT = 0.2

    fun of(base: TaxBase, economy: EconomyState): Double {
        val nominalGdpIndex = economy.gdpBillions / economy.reference.gdpBillions
        return when (base) {
            TaxBase.WAGES -> economy.employmentIndex * economy.wageIndex
            TaxBase.CONSUMPTION -> nominalGdpIndex *
                (1.0 + CONSUMPTION_CONFIDENCE_WEIGHT * (economy.consumerConfidence - NEUTRAL_CONFIDENCE))
            TaxBase.PROFITS -> nominalGdpIndex *
                (PROFIT_FLOOR + PROFIT_CONFIDENCE_WEIGHT * economy.businessConfidence) /
                (PROFIT_FLOOR + PROFIT_CONFIDENCE_WEIGHT * NEUTRAL_CONFIDENCE)
            TaxBase.GDP -> nominalGdpIndex
            TaxBase.PROPERTY -> economy.priceLevel
        }
    }
}
