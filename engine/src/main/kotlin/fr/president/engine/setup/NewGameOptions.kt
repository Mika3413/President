package fr.president.engine.setup

/** Choix du joueur au lancement d'une partie. */
data class NewGameOptions(
    val paceId: String,
    val seed: Long,
    val nowRealUtcMillis: Long,
    val presidentFirstName: String? = null,
    val presidentLastName: String? = null,
    val presidentFemale: Boolean? = null,
    /** Tendance politique abstraite, de -1 (gauche) à +1 (droite). */
    val economicLeaning: Double = 0.0,
    val countryId: String? = null,
    val promises: List<String> = emptyList(),
)
