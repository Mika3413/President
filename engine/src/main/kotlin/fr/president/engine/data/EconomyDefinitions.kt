package fr.president.engine.data

import kotlinx.serialization.Serializable

@Serializable
data class EconomySnapshot(
    val currency: String,
    val gdpBillions: Double,
    val realGrowth: Double,
    val potentialGrowth: Double,
    val inflation: Double,
    val inflationTarget: Double,
    val unemployment: Double,
    val naturalUnemployment: Double,
    val publicDebtBillions: Double,
    val averageDebtRate: Double,
    val riskFreeRate: Double,
    val consumerConfidence: Double,
    val businessConfidence: Double,
    val averageNetMonthlyWage: Double,
    val tradeBalanceBillions: Double,
    val budget: BudgetSnapshot? = null,
    val aggregateBudget: AggregateBudgetSnapshot? = null,
)

@Serializable
enum class TaxBase { WAGES, CONSUMPTION, PROFITS, GDP, PROPERTY }

@Serializable
enum class TaxPayer { HOUSEHOLDS, BUSINESSES, MIXED }

@Serializable
enum class Indexation { NONE, PRICES, UNEMPLOYMENT }

@Serializable
data class BudgetSnapshot(
    val revenues: List<RevenueItemDef>,
    val spending: List<SpendingItemDef>,
)

@Serializable
data class RevenueItemDef(
    val id: String,
    val label: String,
    val amountBillions: Double,
    val base: TaxBase,
    val payer: TaxPayer,
    val rate: Double,
    val rateLabel: String,
    val minRate: Double,
    val maxRate: Double,
    val step: Double,
    /** Part de la hausse de taux perdue par changement de comportement (évasion, moindre activité). */
    val behaviouralLoss: Double,
    val adjustable: Boolean = true,
    val description: String = "",
)

@Serializable
data class SpendingItemDef(
    val id: String,
    val label: String,
    val amountBillions: Double,
    val ministry: String,
    val domain: String? = null,
    val indexation: Indexation = Indexation.NONE,
    val adjustable: Boolean = true,
    val description: String = "",
)

@Serializable
data class AggregateBudgetSnapshot(
    val revenueRatio: Double,
    val spendingRatio: Double,
)

/** Coefficients du modèle macroéconomique (data/economy/model_parameters.json). */
@Serializable
data class EconomyParameters(
    val gapReversionMonthly: Double,
    val okunCoefficient: Double,
    val unemploymentReversionMonthly: Double,
    val phillipsSlope: Double,
    val inflationAdjustmentMonthly: Double,
    val energyPassThrough: Double,
    val confidenceAdjustmentMonthly: Double,
    val confidenceGrowthEffect: Double,
    val confidenceUnemploymentWeight: Double,
    val confidenceInflationWeight: Double,
    val confidenceGrowthWeight: Double,
    val confidenceDebtWeight: Double,
    val confidenceApprovalWeight: Double,
    val fiscalMultiplier: Double,
    val fiscalImpulseMonths: Int,
    val householdTaxConfidenceShock: Double,
    val businessTaxConfidenceShock: Double,
    val debtPremiumThreshold: Double,
    val debtPremiumSlope: Double,
    val deficitPremiumSlope: Double,
    val confidencePremiumSlope: Double,
    val rateRolloverMonthly: Double,
    val taxBurdenPotentialEffect: Double,
    val growthNoise: Double,
    val wageInflationPassThrough: Double,
    val serviceQualityAdjustmentMonthly: Double,
    val serviceQualityFundingSensitivity: Double,
    val serviceQualityMinisterSensitivity: Double,
    val energyPriceMarginSensitivity: Double,
    val electricityExportPriceEurPerMWh: Double,
)
