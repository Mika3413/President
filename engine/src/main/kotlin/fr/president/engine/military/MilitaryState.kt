package fr.president.engine.military

import fr.president.engine.time.WorldTime
import kotlinx.serialization.Serializable

@Serializable
enum class UnitOrder(val label: String) {
    HOLD("En position"),
    MOVE("Déplacement"),
    DEFEND("Défense"),
    ATTACK("Attaque"),
    RETREAT("Repli"),
    SUPPORT("Soutien"),
    PATROL("Patrouille"),
    /** Opérations spéciales (ciblées sur la carte, exécutées par OperationsService). */
    AIRBORNE("Opération aéroportée"),
    AMPHIBIOUS("Débarquement"),
}

/** Unité cohérente (brigade, escadre, groupe naval) : jamais de soldat individuel. */
@Serializable
class UnitState(
    val id: String,
    val branch: String,
    val type: String,
    var baseId: String,
    var personnel: Int,
    val equipment: MutableMap<String, Int>,
    var readiness: Double,
    var morale: Double,
    var ammunition: Double,
    var fuel: Double,
    var fatigue: Double = 0.2,
    var name: String = id,
    var countryId: String = "",
    /** Zone de théâtre où se trouve l'unité. */
    var zoneId: String = "",
    /** Zone de garnison (retour en cas de repli). */
    var homeZoneId: String = "",
    var order: UnitOrder = UnitOrder.HOLD,
    var targetZoneId: String? = null,
    val path: MutableList<String> = mutableListOf(),
    var legProgressKm: Double = 0.0,
    /** Effectifs et matériel restants (1 = complet). */
    var strength: Double = 1.0,
    var experience: Double = 0.3,
    var supplied: Boolean = true,
    var inCombat: Boolean = false,
    var destroyed: Boolean = false,
    /** Dernière opération spéciale (parachutage, débarquement) : délai avant la suivante. */
    var lastOperationAt: fr.president.engine.time.WorldTime? = null,
    /** Zone d'où vient l'unité (franchissement d'un fleuve, débarquement). */
    var cameFrom: String? = null,
    /** Jours passés encerclée, sans ravitaillement. */
    var isolatedDays: Int = 0,
)

@Serializable
enum class WarStatus { ACTIVE, CEASEFIRE, ENDED }

@Serializable
class War(
    val id: String,
    val attackers: MutableList<String>,
    val defenders: MutableList<String>,
    val startedAt: WorldTime,
    val cause: String,
    var status: WarStatus = WarStatus.ACTIVE,
    var ceasefireUntil: WorldTime? = null,
    val casualties: MutableMap<String, Int> = mutableMapOf(),
    /** Lassitude par pays (0..1) : pèse sur la volonté de poursuivre. */
    val weariness: MutableMap<String, Double> = mutableMapOf(),
    val casualtiesCounted: MutableMap<String, Int> = mutableMapOf(),
    var endedAt: WorldTime? = null,
    var outcome: String = "",
) {
    val participants: List<String> get() = attackers + defenders
    fun sideOf(country: String): Int = when (country) {
        in attackers -> ATTACKER
        in defenders -> DEFENDER
        else -> NONE
    }

    companion object {
        const val ATTACKER = 1
        const val DEFENDER = 2
        const val NONE = 0
    }
}

/** Stocks nationaux (0..1 de la capacité de réserve) et production du pays joueur. */
@Serializable
class StockState(
    var ammunition: Double = 0.55,
    var fuel: Double = 0.7,
    var spareParts: Double = 0.6,
    var warEconomy: Boolean = false,
    var reservists: Int = 0,
)

@Serializable
data class ProductionOrder(val id: String, val unitType: String, val readyAt: WorldTime, val costBillions: Double)

@Serializable
class MilitaryState(
    val units: MutableMap<String, UnitState> = mutableMapOf(),
    var overallReadiness: Double = 0.6,
    val wars: MutableList<War> = mutableListOf(),
    /** Zones occupées : zone -> pays contrôleur (si différent du propriétaire). */
    val occupied: MutableMap<String, String> = mutableMapOf(),
    /** Dégâts de guerre par zone (0..1). */
    val damage: MutableMap<String, Double> = mutableMapOf(),
    /** Changements de frontières entérinés par un traité : zone -> nouveau propriétaire. */
    val annexed: MutableMap<String, String> = mutableMapOf(),
    val stocks: StockState = StockState(),
    val production: MutableList<ProductionOrder> = mutableListOf(),
    /** Lassitude de guerre de l'opinion du pays joueur (0..1). */
    var warWeariness: Double = 0.0,
    /** Dernier contrôleur connu de chaque ville (pour annoncer les prises et les pertes). */
    val cityControl: MutableMap<String, String> = mutableMapOf(),
    var recentCasualties: Int = 0,
    /** Durée d'occupation de la capitale du joueur (jours). */
    var capitalOccupiedDays: Int = 0,
    var unitCounter: Int = 0,
    /** Fortifications et bâtiments militaires de tous les pays. */
    val works: MutableList<Fortification> = mutableListOf(),
    var worksSeeded: Boolean = false,
    /** Vie des zones occupées : moral de la population, résistance. */
    val occupation: MutableMap<String, OccupationState> = mutableMapOf(),
    /** Zones frappées par l'arme nucléaire (retombées). */
    val nuclearZones: MutableMap<String, WorldTime> = mutableMapOf(),
    /** Batailles récentes et en cours (rapports de combat). */
    val battles: MutableList<BattleRecord> = mutableListOf(),
)

/** Rapport d'une bataille : qui, où, avec quels avantages, et à quel prix. */
@Serializable
class BattleRecord(
    val id: String,
    val zoneId: String,
    val startedAt: WorldTime,
    var lastAt: WorldTime,
    val attackers: MutableList<String>,
    val defenders: MutableList<String>,
    var attackerLosses: Int = 0,
    var defenderLosses: Int = 0,
    var attackerPower: Double = 0.0,
    var defenderPower: Double = 0.0,
    /** Avantages et handicaps en jeu (terrain, fortifications, fleuve...), dans l'ordre d'importance. */
    val modifiers: MutableList<String> = mutableListOf(),
    var outcome: String = "",
    var hours: Int = 0,
) {
    fun active(now: WorldTime): Boolean = outcome.isEmpty() && lastAt.daysUntil(now) * 24 < 2.5
}
