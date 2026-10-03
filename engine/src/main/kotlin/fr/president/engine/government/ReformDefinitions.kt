package fr.president.engine.government

import fr.president.engine.effects.EffectSpec
import kotlinx.serialization.Serializable

@Serializable
data class ReformsFile(val reforms: List<ReformDef>, val voteDelayDays: Int)

/** Réforme structurelle : conséquences immédiates (opinion) et de long terme (économie, services). */
@Serializable
data class ReformDef(
    val id: String,
    val title: String,
    val category: String,
    val description: String,
    /** Exigence parlementaire supplémentaire (s'ajoute au seuil d'adoption). */
    val difficulty: Double,
    val immediateEffects: List<EffectSpec> = emptyList(),
    /** Effets progressifs pendant la mise en œuvre (champ [EffectSpec.days]). */
    val longTermEffects: List<EffectSpec> = emptyList(),
    val exclusiveWith: List<String> = emptyList(),
    val summary: String = "",
)

@Serializable
data class PromisesFile(val promises: List<PromiseDef>, val maxPromises: Int, val keptBonus: Double, val brokenPenalty: Double)

@Serializable
enum class PromiseKind { BELOW, ABOVE, NOT_ABOVE_START, ABOVE_START, BELOW_START, REFORM_ADOPTED, REFORM_NOT_ADOPTED, NO_WAR }

/** Promesse de campagne, jugée par les électeurs au scrutin suivant. */
@Serializable
data class PromiseDef(
    val id: String,
    val label: String,
    val kind: PromiseKind,
    val variable: String? = null,
    val threshold: Double = 0.0,
    val reform: String? = null,
    /** Groupes sociaux particulièrement attentifs à cette promesse. */
    val groups: List<String> = emptyList(),
)

@Serializable
data class DemographyDef(
    val birthRate: Double,
    val deathRate: Double,
    val netMigrationRate: Double,
    val agingPerYear: Double,
)
