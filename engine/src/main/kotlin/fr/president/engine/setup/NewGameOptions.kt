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
    /** Position sociétale, de -1 (progressiste) à +1 (conservatrice) ; dérivée de l'économique si absente. */
    val socialLeaning: Double? = null,
    val countryId: String? = null,
    val promises: List<String> = emptyList(),
    /** Scénario de départ (null : la situation réelle). */
    val scenarioId: String? = null,
    /** Âge du président au début du mandat (null : tiré au hasard). */
    val presidentAge: Int? = null,
    /** Parcours avant l'élection. */
    val careerId: String? = null,
    /** Traits de caractère choisis (0..1), qui remplacent le tirage. */
    val presidentTraits: Map<String, Double> = emptyMap(),
    /** Visage choisi. */
    val appearance: fr.president.engine.politics.Appearance? = null,
)
