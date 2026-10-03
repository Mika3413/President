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
)
