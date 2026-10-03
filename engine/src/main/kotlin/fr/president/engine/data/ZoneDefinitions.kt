package fr.president.engine.data

import kotlinx.serialization.Serializable

/** Grille des zones de théâtre (générée par tools/mapgen/build_zones.py). */
@Serializable
data class ZonesFile(val stepDegrees: Double, val zones: List<ZoneDef>)

@Serializable
data class ZoneDef(
    val id: String,
    val lon: Double,
    val lat: Double,
    /** Pays propriétaire (vide pour une zone maritime). */
    val owner: String,
    val sea: Boolean,
    val department: String? = null,
    val coastal: Boolean = false,
    val n: List<String> = emptyList(),
)
