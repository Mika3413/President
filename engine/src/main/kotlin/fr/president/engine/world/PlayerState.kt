package fr.president.engine.world

import fr.president.engine.time.WorldTime
import kotlinx.serialization.Serializable

@Serializable
data class GameOver(val time: WorldTime, val reason: String)

@Serializable
class PlayerState(
    val countryId: String,
    val presidentId: String,
    var termStart: WorldTime,
    var termNumber: Int = 1,
    var gameOver: GameOver? = null,
    /** Promesses de campagne et valeurs de référence au début du mandat. */
    val promises: MutableList<String> = mutableListOf(),
    val promiseBaselines: MutableMap<String, Double> = mutableMapOf(),
    var warsThisTerm: Int = 0,
    /** Étape du tutoriel guidé (-1 : terminé ou ancienne partie). Une nouvelle partie commence à 0. */
    var tourStep: Int = -1,
    /** Scénario de départ choisi. */
    var scenario: String? = null,
    /** Académie : module en cours, étape, modules terminés. */
    var academyModule: String? = null,
    var academyStep: Int = 0,
    val academyDone: MutableSet<String> = mutableSetOf(),
    /** Parcours du président avant son élection. */
    var career: String? = null,
    /** Premier matin : message de la première crise, département touché, bilan déjà montré. */
    var firstCrisis: String? = null,
    var firstCrisisDept: String? = null,
    var firstCrisisShown: Boolean = false,
    var firstCrisisAmount: Double = 0.0,
    /** Écrans ouverts au fil du mandat (nouvelles parties) ; les anciennes parties voient tout. */
    var progressiveScreens: Boolean = false,
    val unlockedScreens: MutableSet<String> = mutableSetOf(),
    /** Écrans ouverts par le temps mais pas encore visités (pastille « Nouveau »). */
    val newScreens: MutableSet<String> = mutableSetOf(),
)
