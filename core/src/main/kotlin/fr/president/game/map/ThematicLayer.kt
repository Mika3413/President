package fr.president.game.map

import fr.president.engine.territory.DepartmentState
import kotlin.math.roundToInt

/**
 * Couches thématiques proposées au joueur. Les couches « carte de chaleur » affichent aussi,
 * sur chaque département, le chiffre correspondant ([figure]) : on lit la carte sans la toucher.
 */
enum class ThematicLayer(
    val label: String,
    val heatmap: Boolean,
    val legendLow: String = "",
    val legendHigh: String = "",
    /** Signification des chiffres inscrits sur la carte. */
    val figures: String = "",
) {
    ADMIN("Administratif", false),
    OPINION("Opinion", true, "Hostile", "Favorable", "Chiffres : % d'opinions favorables"),
    UNEMPLOYMENT("Chômage", true, "Élevé", "Faible", "Chiffres : taux de chômage (%)"),
    POPULATION("Population", true, "Peu dense", "Très dense"),
    INCOME("Revenus", true, "Modestes", "Élevés", "Chiffres : indice (100 = moyenne)"),
    HEALTH("Santé", true, "Désert médical", "Bien doté", "Chiffres : accès aux soins (100 = moyenne)"),
    SECURITY("Sécurité", true, "Délinquance forte", "Calme", "Chiffres : délinquance (100 = moyenne)"),
    INDUSTRY("Industrie", true, "Peu industriel", "Très industriel", "Chiffres : % des emplois"),
    AGRICULTURE("Agriculture", true, "Peu agricole", "Très agricole", "Chiffres : % des emplois"),
    POLLUTION("Pollution", true, "Air pollué", "Air pur", "Chiffres : pollution (100 = moyenne)"),
    ENERGY("Énergie", false),
    TRANSPORT("Transports", false),
    MILITARY("Militaire", false),
    EVENTS("Crises", false);

    /** Chiffre court à inscrire sur un département, ou null si la couche n'en affiche pas. */
    fun figure(d: DepartmentState): String? = when (this) {
        OPINION -> pct(d.approval)
        UNEMPLOYMENT -> String.format(java.util.Locale.FRENCH, "%.1f", d.unemployment * PERCENT)
        INCOME -> pct(d.incomeIndex)
        HEALTH -> pct(d.healthAccess)
        SECURITY -> pct(d.crime)
        INDUSTRY -> pct(d.industryShare)
        AGRICULTURE -> pct(d.agricultureShare)
        POLLUTION -> pct(d.pollution)
        else -> null
    }

    private fun pct(v: Double) = (v * PERCENT).roundToInt().toString()

    private companion object {
        const val PERCENT = 100.0
    }
}
