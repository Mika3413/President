package fr.president.engine.economy

import fr.president.engine.data.EconomyParameters
import fr.president.engine.util.GameRandom
import fr.president.engine.util.approach
import fr.president.engine.util.clamp01

/**
 * Modèle macroéconomique mensuel, générique pour tous les pays.
 * Les effets passent par des canaux indirects et différés : confiance, écart de production,
 * impulsions budgétaires, prix de l'énergie, taux d'intérêt.
 */
class MacroEconomyModel(private val p: EconomyParameters) {

    /** Entrées externes au modèle pour un mois donné. */
    data class MonthInputs(
        val governmentApproval: Double,
        val externalGrowthBoost: Double = 0.0,
        val tradeBalanceDelta: Double = 0.0,
    )

    fun step(e: EconomyState, inputs: MonthInputs, rng: GameRandom) {
        BudgetCalculator.recompute(e)
        updateConfidence(e, inputs)
        val growth = computeGrowth(e, inputs, rng)
        applyGrowth(e, growth)
        updateLabourMarket(e, growth)
        updateInflation(e)
        updateRates(e)
        BudgetCalculator.recompute(e)
        applyPublicFinances(e)
        e.tradeBalanceBillions += inputs.tradeBalanceDelta
        recordHistory(e)
    }

    private fun updateConfidence(e: EconomyState, inputs: MonthInputs) {
        val unemploymentGap = e.unemployment - e.naturalUnemployment
        val inflationGap = e.inflation - e.inflationTarget
        val debtStress = (e.debtRatio - p.debtPremiumThreshold).coerceAtLeast(0.0)
        val growthMomentum = e.realGrowth - e.potentialGrowth
        val approvalGap = inputs.governmentApproval - NEUTRAL

        val consumerTarget = NEUTRAL -
            p.confidenceUnemploymentWeight * unemploymentGap -
            p.confidenceInflationWeight * inflationGap +
            p.confidenceGrowthWeight * growthMomentum
        val businessTarget = NEUTRAL -
            p.confidenceDebtWeight * debtStress +
            p.confidenceGrowthWeight * growthMomentum +
            p.confidenceApprovalWeight * approvalGap -
            p.confidenceInflationWeight * (e.energyPriceIndex - 1.0) * p.energyPassThrough

        e.consumerConfidence = approach(e.consumerConfidence, consumerTarget.clamp01(), p.confidenceAdjustmentMonthly)
        e.businessConfidence = approach(e.businessConfidence, businessTarget.clamp01(), p.confidenceAdjustmentMonthly)
    }

    private fun computeGrowth(e: EconomyState, inputs: MonthInputs, rng: GameRandom): Double {
        val gapReversion = -p.gapReversionMonthly * e.outputGap * MONTHS
        val confidence = p.confidenceGrowthEffect *
            ((e.consumerConfidence - NEUTRAL) + (e.businessConfidence - NEUTRAL))
        val impulses = e.impulses.sumOf { it.monthlyAnnualizedGrowth }
        e.impulses.forEach { it.monthsRemaining-- }
        e.impulses.removeAll { it.monthsRemaining <= 0 }
        val shock = e.pendingOutputShock * MONTHS
        e.pendingOutputShock = 0.0
        val energy = -p.energyPassThrough * (e.energyPriceIndex - 1.0)
        val noise = p.growthNoise * rng.nextGaussian()
        return e.potentialGrowth + gapReversion + confidence + impulses + shock + energy +
            inputs.externalGrowthBoost + noise
    }

    private fun applyGrowth(e: EconomyState, growth: Double) {
        e.realGrowth = approach(e.realGrowth, growth, GROWTH_SMOOTHING)
        e.outputGap += (growth - e.potentialGrowth) / MONTHS
        e.gdpBillions *= 1.0 + (growth + e.inflation) / MONTHS
        e.priceLevel *= 1.0 + e.inflation / MONTHS
        val wageGrowth = e.inflation * p.wageInflationPassThrough + e.potentialGrowth
        e.wageIndex *= 1.0 + wageGrowth / MONTHS
        e.averageNetMonthlyWage *= 1.0 + wageGrowth / MONTHS
    }

    private fun updateLabourMarket(e: EconomyState, growth: Double) {
        val okun = -p.okunCoefficient * (growth - e.potentialGrowth) / MONTHS
        val reversion = p.unemploymentReversionMonthly * (e.naturalUnemployment - e.unemployment)
        e.unemployment = (e.unemployment + okun + reversion).coerceIn(MIN_UNEMPLOYMENT, MAX_UNEMPLOYMENT)
    }

    private fun updateInflation(e: EconomyState) {
        val target = e.inflationTarget + p.phillipsSlope * e.outputGap +
            p.energyPassThrough * (e.energyPriceIndex - 1.0)
        e.inflation = approach(e.inflation, target, p.inflationAdjustmentMonthly)
    }

    private fun updateRates(e: EconomyState) {
        val debtPremium = p.debtPremiumSlope * (e.debtRatio - p.debtPremiumThreshold).coerceAtLeast(0.0)
        val deficitPremium = p.deficitPremiumSlope * e.deficitRatio.coerceAtLeast(0.0)
        val confidencePremium = p.confidencePremiumSlope * (NEUTRAL - e.businessConfidence).coerceAtLeast(0.0)
        e.marketRate = e.riskFreeRate + debtPremium + deficitPremium + confidencePremium
        // La dette se refinance progressivement : le taux moyen suit lentement le taux de marché.
        e.averageDebtRate = approach(e.averageDebtRate, e.marketRate + e.debtRateOffset, (p.rateRolloverMonthly * e.debtRolloverFactor).coerceAtMost(1.0))
    }

    private fun applyPublicFinances(e: EconomyState) {
        e.debtBillions += e.deficitBillions / MONTHS + e.pendingOneOffBillions
        e.oneOffThisYearBillions += e.pendingOneOffBillions
        e.pendingOneOffBillions = 0.0
    }

    private fun recordHistory(e: EconomyState) {
        e.growthHistory.push(e.realGrowth)
        e.unemploymentHistory.push(e.unemployment)
        e.inflationHistory.push(e.inflation)
        e.debtRatioHistory.push(e.debtRatio)
        e.deficitRatioHistory.push(e.deficitRatio)
        e.debtHistory.push(e.debtBillions)
    }

    private companion object {
        const val MONTHS = 12.0
        const val NEUTRAL = 0.5
        const val GROWTH_SMOOTHING = 0.5
        const val MIN_UNEMPLOYMENT = 0.015
        const val MAX_UNEMPLOYMENT = 0.35
    }
}
