package fr.president.engine.energy

import kotlinx.serialization.Serializable

/** Bilan électrique du pays joueur (rythme annuel, TWh). */
@Serializable
class EnergyState(
    var demandTWh: Double,
    var productionTWh: Double = 0.0,
    /** Volumes vendus par accords internationaux. */
    var committedExportTWh: Double = 0.0,
    var netExportTWh: Double = 0.0,
    /** Marge = (production - demande - engagements) / demande. */
    var margin: Double = 0.0,
    var priceIndex: Double = 1.0,
    val referenceDemandTWh: Double = demandTWh,
    /** Capacités ajoutées par les grands programmes (MW par parc agrégé). */
    val extraCapacityMW: MutableMap<String, Double> = mutableMapOf(),
    /** Dernière alerte sur les livraisons d'électricité non honorées. */
    var deliveryAlertAt: fr.president.engine.time.WorldTime? = null,
)
