package fr.president.engine.events

import fr.president.engine.effects.EffectSpec
import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import kotlinx.serialization.Serializable

@Serializable
enum class EventScope { NATIONAL, DEPARTMENT, CITY, INFRASTRUCTURE, MINISTER, FOREIGN_COUNTRY }

@Serializable
enum class SenderRole { NONE, MAYOR, PREFECT, REGION_PRESIDENT, DEPARTMENT_PRESIDENT, MINISTER, PRIME_MINISTER, SUBJECT, FOREIGN_LEADER }

/** Résultat d'une interaction, mémorisé par l'interlocuteur. */
@Serializable
enum class InteractionOutcome { ACCEPTED, PARTIAL, REFUSED, POSTPONED, NEUTRAL }

@Serializable
data class EventFile(val events: List<EventDefinition>)

/**
 * Événement dynamique piloté par les données.
 * Sa probabilité quotidienne dépend de l'état réel de la simulation via [modifiers].
 */
@Serializable
data class EventDefinition(
    val id: String,
    val category: NotificationCategory,
    val scope: EventScope,
    val baseDailyProbability: Double,
    val headline: String,
    val infraTypes: List<String> = emptyList(),
    val maxCityRank: Int = 3,
    val modifiers: List<ProbabilityModifier> = emptyList(),
    val conditions: List<Condition> = emptyList(),
    val cooldownDays: Double = 0.0,
    val scopeCooldownDays: Double = 0.0,
    val params: List<ParamDef> = emptyList(),
    val immediateEffects: List<EffectSpec> = emptyList(),
    val message: EventMessageDef? = null,
    val urgency: Urgency = Urgency.INFO,
    val notificationText: String = "",
    /** Mesures de crise proposées en complément de la réponse (ajoutées au chargement). */
    val measures: List<String> = emptyList(),
)

/** Multiplicateur de probabilité interpolé linéairement selon une variable de simulation. */
@Serializable
data class ProbabilityModifier(
    val variable: String,
    val from: Double,
    val to: Double,
    val factorAtFrom: Double,
    val factorAtTo: Double,
)

@Serializable
data class Condition(
    val variable: String,
    val min: Double? = null,
    val max: Double? = null,
    val oneOf: List<Double> = emptyList(),
)

/** Paramètre tiré à l'instanciation (montant demandé, durée...). */
@Serializable
data class ParamDef(
    val name: String,
    val min: Double,
    val max: Double,
    /** Variable multipliant la valeur tirée (ex. population de la ville en millions). */
    val scaleBy: String? = null,
    val minScale: Double = 0.0,
    val round: Double = 0.0,
)

@Serializable
data class EventMessageDef(
    val template: String,
    val sender: SenderRole,
    val ministry: String? = null,
    val responseDays: Double,
    val options: List<EventOptionDef>,
    val defaultOption: String,
    /** Fragment explicatif ajouté quand le joueur demande plus d'informations. */
    val detailsTemplate: String? = null,
)

@Serializable
data class EventOptionDef(
    val id: String,
    val label: String,
    val hint: String = "",
    val effects: List<EffectSpec> = emptyList(),
    val outcome: InteractionOutcome = InteractionOutcome.NEUTRAL,
    val project: ProjectSpec? = null,
    /** Redemande plus tard (reporter) : nombre de jours avant la relance. */
    val reaskAfterDays: Double? = null,
    /** Demander davantage d'informations : renvoie le message avec des détails. */
    val requestDetails: Boolean = false,
)

@Serializable
data class ProjectSpec(
    val name: String,
    val kind: String,
    val durationDays: Double,
    val costParam: String? = null,
    val costFactor: Double = 1.0,
    val onCompletion: List<EffectSpec> = emptyList(),
)
