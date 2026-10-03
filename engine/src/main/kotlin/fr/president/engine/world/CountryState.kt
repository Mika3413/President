package fr.president.engine.world

import fr.president.engine.data.DetailLevel
import fr.president.engine.economy.EconomyState
import fr.president.engine.time.WorldTime
import kotlinx.serialization.Serializable

/** État commun à tous les pays (joueur ou IA). */
@Serializable
class CountryState(
    val id: String,
    val detail: DetailLevel,
    var leaderId: String,
    val economy: EconomyState,
    var population: Long,
    /** Qualité des services publics par domaine (0..1), pays détaillés uniquement. */
    val services: MutableMap<String, Double> = mutableMapOf(),
    /** Solde électrique annuel (TWh) pour les pays simulés en mode allégé. */
    var electricityBalanceTWh: Double = 0.0,
    var leaderApproval: Double = 0.5,
    var militaryBudgetBillions: Double = 0.0,
    var nextAiDecision: WorldTime? = null,
    var nextLeadershipChange: WorldTime? = null,
)
