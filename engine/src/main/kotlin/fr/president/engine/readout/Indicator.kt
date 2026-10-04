package fr.president.engine.readout

/**
 * Information lisible : un libellé qualitatif, une phrase d'explication,
 * et des détails chiffrés disponibles sur demande (bouton « Détails »).
 */
data class Indicator(
    val label: String,
    val status: String,
    val tone: Tone,
    val explanation: String = "",
    val details: List<Pair<String, String>> = emptyList(),
    /** Chiffre clé affiché en grand (« 48 % », « +0,8 % »), vide si l'indicateur est qualitatif. */
    val value: String = "",
    /** Sens de l'évolution récente : -1 en baisse, 0 stable, +1 en hausse. */
    val trend: Int = 0,
    /** Une hausse est-elle une bonne nouvelle ? (couleur de la flèche) */
    val higherIsBetter: Boolean = true,
)

/** Sens d'évolution entre deux valeurs, avec une tolérance. */
fun trendOf(now: Double?, before: Double?, epsilon: Double): Int {
    if (now == null || before == null) return 0
    return when {
        now - before > epsilon -> 1
        before - now > epsilon -> -1
        else -> 0
    }
}
