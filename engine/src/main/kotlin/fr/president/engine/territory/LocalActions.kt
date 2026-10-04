package fr.president.engine.territory

import fr.president.engine.effects.EffectSpec
import kotlinx.serialization.Serializable

/**
 * Action que le président peut lancer directement sur un département depuis la carte :
 * construire un hôpital, aider une usine, se rendre sur place... Coût, durée et effets sont
 * des données. Dans les effets, la cible « local.<champ> » désigne le département choisi.
 */
@Serializable
data class LocalActionDef(
    val id: String,
    val label: String,
    val icon: String = "",
    /** Rubrique d'affichage (décisions nationales : « Économie », « Social »...). */
    val category: String = "",
    val description: String,
    val costBillions: Double = 0.0,
    /** 0 : effet immédiat ; sinon durée du chantier en jours. */
    val durationDays: Int = 0,
    /** Délai minimal entre deux réalisations dans le même département. */
    val cooldownDays: Int = 0,
    /** Délai minimal entre deux réalisations, tous départements confondus. */
    val globalCooldownDays: Int = 0,
    val immediate: List<EffectSpec> = emptyList(),
    val onCompletion: List<EffectSpec> = emptyList(),
)

@Serializable
data class LocalActionsFile(val actions: List<LocalActionDef>, val categories: List<ActionCategory> = emptyList())

/**
 * Décisions nationales du président (décrets, plans, déplacements, annonces) : même format que
 * les actions locales, sans cible « local.* ». Les rubriques donnent l'ordre d'affichage.
 */
@Serializable
data class NationalActionsFile(val categories: List<ActionCategory>, val actions: List<LocalActionDef>)

@Serializable
data class ActionCategory(val id: String, val label: String, val icon: String = "", val description: String = "")
