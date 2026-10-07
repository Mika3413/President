package fr.president.engine.data

import kotlinx.serialization.Serializable

@Serializable
enum class Domain { LAND, AIR, SEA, STRATEGIC }

/** Caractéristiques d'un type d'unité (brigade, escadre, groupe naval...). */
@Serializable
data class UnitTypeDef(
    val id: String,
    val label: String,
    val domain: Domain,
    val attack: Double,
    val defense: Double,
    val airDefense: Double = 0.0,
    val speedKmPerDay: Double,
    /** Rayon d'action autour de la base (unités aériennes). */
    val rangeKm: Double = 0.0,
    val ammoPerCombatDay: Double,
    val fuelPer100Km: Double,
    val upkeepMillionsPerDayDeployed: Double,
    val costBillions: Double,
    val buildDays: Int,
    val personnel: Int,
    /** Prolonge le rayon de ravitaillement des unités voisines. */
    val logistics: Int = 0,
    val buildable: Boolean = true,
    /** Famille (blindés, infanterie, montagne, artillerie...) : forces et faiblesses selon le terrain et l'adversaire. */
    val category: String = "",
)

/** Coefficients militaires (combat, logistique, usure). */
@Serializable
data class MilitaryParameters(
    val combatLossPerHour: Double,
    val defendBonus: Double,
    val homeTerritoryBonus: Double,
    val retreatStrength: Double,
    val retreatMorale: Double,
    val supplyRangeZones: Int,
    val unsuppliedDecayPerDay: Double,
    val resupplyPerDay: Double,
    val fatiguePerCombatDay: Double,
    val fatigueRecoveryPerDay: Double,
    val moraleSwingPerDay: Double,
    val visibilityZones: Int,
    val zoneDamagePerCombatDay: Double,
    val warWearinessPerCasualty: Double,
    val warWearinessPerDay: Double,
    val warWearinessDecayPerDay: Double,
    val stockRegenPerMonth: Double,
    val warEconomyRegenBonus: Double,
    val warEconomyCostBillionsPerMonth: Double,
    val purchaseCostBillions: Double,
    val purchaseDays: Int,
    val mobilizationDays: Int,
    val mobilizationUnits: Int,
    val mobilizationCostBillions: Double,
    val aiUnitStartReadiness: Double,
    val casualtyRateFactor: Double,
)

@Serializable
data class UnitTypesFile(val types: List<UnitTypeDef>, val parameters: MilitaryParameters)
