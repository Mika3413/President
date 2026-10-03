package fr.president.game.map

/** Niveaux de détail de la carte, du monde à la ville. */
enum class Lod(val label: String) { WORLD("Monde"), EUROPE("Europe"), FRANCE("France"), REGION("Région"), LOCAL("Local") }

object LodPolicy {
    /** Largeur visible (en degrés de longitude) au-delà de laquelle on passe au niveau supérieur. */
    private const val WORLD_DEGREES = 70f
    private const val EUROPE_DEGREES = 22f
    private const val FRANCE_DEGREES = 7f
    private const val REGION_DEGREES = 2.5f

    fun of(visibleWidthUnits: Float): Lod {
        val degrees = visibleWidthUnits / GeoProjection.UNITS_PER_DEGREE
        return when {
            degrees > WORLD_DEGREES -> Lod.WORLD
            degrees > EUROPE_DEGREES -> Lod.EUROPE
            degrees > FRANCE_DEGREES -> Lod.FRANCE
            degrees > REGION_DEGREES -> Lod.REGION
            else -> Lod.LOCAL
        }
    }
}
