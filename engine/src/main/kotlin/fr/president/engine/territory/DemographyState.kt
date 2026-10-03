package fr.president.engine.territory

import kotlinx.serialization.Serializable

@Serializable
class DemographyState(
    /** Multiplicateur de l'immigration nette décidé par la politique migratoire (1 = tendance initiale). */
    var immigrationFactor: Double = 1.0,
    var birthsLastYear: Long = 0,
    var deathsLastYear: Long = 0,
    var netMigrationLastYear: Long = 0,
)
