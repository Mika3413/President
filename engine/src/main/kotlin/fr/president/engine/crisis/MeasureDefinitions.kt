package fr.president.engine.crisis

import fr.president.engine.effects.EffectSpec
import fr.president.engine.events.Condition
import fr.president.engine.territory.ActionCategory
import fr.president.engine.time.WorldTime
import kotlinx.serialization.Serializable

/**
 * Mesure de crise ou de prévention qui dure : confinement, couvre-feu, plan ORSEC, fermeture des
 * frontières, débroussaillage obligatoire... Elle a un coût au lancement et par mois, des effets
 * au début, chaque jour et à la levée, et elle change la probabilité et l'ampleur d'événements.
 * Dans les effets, « local.<champ> » désigne le département choisi (mesures locales).
 */
@Serializable
data class MeasureDef(
    val id: String,
    val label: String,
    val icon: String = "",
    val family: String,
    val description: String,
    /** Mesure appliquée à un département (confinement local, évacuation...). */
    val local: Boolean = false,
    val costBillions: Double = 0.0,
    val costPerMonthBillions: Double = 0.0,
    /** Durée proposée (jours) ; 0 = jusqu'à ce que le président la lève. */
    val defaultDays: Int = 0,
    val start: List<EffectSpec> = emptyList(),
    /** Effets appliqués chaque jour tant que la mesure est active. */
    val daily: List<EffectSpec> = emptyList(),
    val end: List<EffectSpec> = emptyList(),
    /** Influence sur les événements tant que la mesure est active. */
    val affects: List<EventImpact> = emptyList(),
    val requires: List<Condition> = emptyList(),
    val requiresText: String = "",
    val confirm: Boolean = false,
    val cooldownDays: Int = 0,
    /** Mesures incompatibles (une seule à la fois). */
    val exclusive: List<String> = emptyList(),
    /** Lassitude : perte de respect de la mesure par mois (0 = aucune). */
    val fatigue: Double = 0.0,
    /** Régime d'exception : au-delà de 12 jours, le Parlement doit voter la prorogation. */
    val emergency: Boolean = false,
    /** Mesure attentatoire aux libertés : un recours devant le Conseil d'État peut la suspendre. */
    val contestable: Boolean = false,
)

/** Multiplicateurs appliqués à un événement : probabilité de survenue et ampleur. */
@Serializable
data class EventImpact(val event: String, val probability: Double = 1.0, val intensity: Double = 1.0)

/** Risque suivi par le président : un ensemble d'événements, et les mesures qui le réduisent. */
@Serializable
data class RiskDef(val id: String, val label: String, val icon: String = "", val description: String = "", val events: List<String>)

@Serializable
data class MeasuresFile(val families: List<ActionCategory>, val measures: List<MeasureDef>, val risks: List<RiskDef> = emptyList())

@Serializable
data class ActiveMeasure(
    val id: String,
    val startedAt: WorldTime,
    /** Fin prévue, ou null si la mesure dure jusqu'à sa levée. */
    val endsAt: WorldTime?,
    val department: String? = null,
    /** Part de la population qui respecte la mesure (s'use avec la lassitude). */
    var compliance: Double = 1.0,
    /** Prorogation votée par le Parlement (régime d'exception). */
    var extended: Boolean = false,
)

@Serializable
class MeasureState(
    val active: MutableList<ActiveMeasure> = mutableListOf(),
    /** Dernière levée de chaque mesure (délais avant de la relancer). */
    val lastEnded: MutableMap<String, WorldTime> = mutableMapOf(),
)
