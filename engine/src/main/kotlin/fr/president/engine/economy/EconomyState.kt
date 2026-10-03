package fr.president.engine.economy

import fr.president.engine.data.Indexation
import fr.president.engine.data.TaxBase
import fr.president.engine.data.TaxPayer
import fr.president.engine.util.History
import kotlinx.serialization.Serializable

/**
 * État macroéconomique d'un pays. Les montants sont en milliards (devise du pays),
 * les flux budgétaires en rythme annuel.
 */
@Serializable
class EconomyState(
    var gdpBillions: Double,
    var realGrowth: Double,
    var potentialGrowth: Double,
    var inflation: Double,
    val inflationTarget: Double,
    var unemployment: Double,
    var naturalUnemployment: Double,
    var debtBillions: Double,
    var averageDebtRate: Double,
    var marketRate: Double,
    val riskFreeRate: Double,
    var consumerConfidence: Double,
    var businessConfidence: Double,
    var averageNetMonthlyWage: Double,
    var tradeBalanceBillions: Double,
    val reference: EconomyReference,
    val budget: BudgetState? = null,
    val aggregate: AggregateBudgetState? = null,
) {
    var outputGap: Double = 0.0
    var priceLevel: Double = 1.0
    var wageIndex: Double = 1.0
    var energyPriceIndex: Double = 1.0
    /** Choc de niveau de production (fraction du PIB) accumulé depuis le dernier pas mensuel. */
    var pendingOutputShock: Double = 0.0
    /** Dépenses exceptionnelles accumulées (milliards) depuis le dernier pas mensuel. */
    var pendingOneOffBillions: Double = 0.0
    var oneOffThisYearBillions: Double = 0.0
    val impulses: MutableList<GrowthImpulse> = mutableListOf()

    var revenueBillions: Double = 0.0
    var spendingBillions: Double = 0.0
    var interestBillions: Double = 0.0
    var deficitBillions: Double = 0.0

    val growthHistory = History()
    val unemploymentHistory = History()
    val inflationHistory = History()
    val debtRatioHistory = History()
    val deficitRatioHistory = History()
    val debtHistory = History()

    val debtRatio: Double get() = debtBillions / gdpBillions
    val deficitRatio: Double get() = deficitBillions / gdpBillions
    val employmentIndex: Double get() = (1.0 - unemployment) / (1.0 - reference.unemployment)
}

/** Valeurs de départ, servant de référence aux indices (assiettes fiscales, services publics). */
@Serializable
data class EconomyReference(
    val gdpBillions: Double,
    val unemployment: Double,
    val debtRatio: Double,
    val householdTaxBurden: Double,
    val businessTaxBurden: Double,
)

/** Impulsion de croissance différée (ex. effet d'une hausse d'impôt réparti sur 12 mois). */
@Serializable
data class GrowthImpulse(val monthlyAnnualizedGrowth: Double, var monthsRemaining: Int, val source: String)

@Serializable
class BudgetState(
    val revenues: MutableMap<String, RevenueItemState>,
    val spending: MutableMap<String, SpendingItemState>,
)

@Serializable
class RevenueItemState(
    val id: String,
    val base: TaxBase,
    val payer: TaxPayer,
    val referenceAmount: Double,
    val referenceRate: Double,
    var rate: Double,
    val behaviouralLoss: Double,
    var amount: Double,
)

@Serializable
class SpendingItemState(
    val id: String,
    val ministry: String,
    val domain: String?,
    val indexation: Indexation,
    val referenceAmount: Double,
    /** Multiplicateur décidé par le pouvoir politique (1.0 = budget initial). */
    var policyFactor: Double = 1.0,
    var amount: Double,
)

/** Budget simplifié des pays simulés en mode allégé. */
@Serializable
class AggregateBudgetState(
    var revenueRatio: Double,
    var spendingRatio: Double,
)
