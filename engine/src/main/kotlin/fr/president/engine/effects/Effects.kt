package fr.president.engine.effects

import fr.president.engine.time.WorldTime
import kotlinx.serialization.Serializable

/**
 * Effet de gameplay décrit dans les données.
 * [target] est une clé (ex. "economy.consumerConfidence", "scope.approval") ; la valeur est
 * soit [amount], soit la valeur du paramètre d'événement [param] multipliée par [factor].
 * L'effet est réparti sur [days] jours (0 = immédiat) après un délai de [delayDays].
 */
@Serializable
data class EffectSpec(
    val target: String,
    val amount: Double = 0.0,
    val param: String? = null,
    val factor: Double = 1.0,
    val days: Double = 0.0,
    val delayDays: Double = 0.0,
)

/** Effet en cours d'application progressive, avec une cible déjà résolue. */
@Serializable
data class ActiveEffect(
    val target: String,
    val total: Double,
    val startsAt: WorldTime,
    val endsAt: WorldTime,
    var applied: Double = 0.0,
    val source: String = "",
)
