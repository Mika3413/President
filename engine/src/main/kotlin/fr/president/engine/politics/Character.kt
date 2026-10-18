package fr.president.engine.politics

import kotlinx.serialization.Serializable

@Serializable
enum class CharacterRole {
    PRESIDENT, PRIME_MINISTER, MINISTER, MINISTER_CANDIDATE,
    MAYOR, REGION_PRESIDENT, DEPARTMENT_PRESIDENT, PREFECT,
    FOREIGN_LEADER, PRESIDENTIAL_CANDIDATE, FORMER,
}

/** Identifiants des traits de personnalité (valeurs internes 0..1, jamais affichées brutes). */
object Traits {
    const val AGGRESSIVENESS = "aggressiveness"
    const val PRAGMATISM = "pragmatism"
    const val NATIONALISM = "nationalism"
    const val OPENNESS = "openness"
    const val EGO = "ego"
    const val CAUTION = "caution"
    const val INTEGRITY = "integrity"
    const val CHARISMA = "charisma"
    const val TOUGHNESS = "toughness"
    const val MILITARISM = "militarism"

    val ALL = listOf(AGGRESSIVENESS, PRAGMATISM, NATIONALISM, OPENNESS, EGO, CAUTION, INTEGRITY, CHARISMA, TOUGHNESS, MILITARISM)
}

/** Souvenir d'un personnage à propos du président : « vous avez [text] », bon ou mauvais. */
@Serializable
data class CharacterMemory(val time: Long, val text: String, val weight: Double)

/**
 * Personnage fictif (dirigeants, ministres, élus locaux...).
 * Les pays et institutions sont réels, les personnes jamais.
 */
@Serializable
class Character(
    val id: String,
    var firstName: String,
    var lastName: String,
    var female: Boolean,
    var birthYear: Int,
    val countryId: String,
    var role: CharacterRole,
    /** Référence du poste : ministère, code de département, ville... */
    var roleRef: String? = null,
    val traits: MutableMap<String, Double> = mutableMapOf(),
    var competence: Double = 0.5,
    var management: Double = 0.5,
    var experience: Double = 0.5,
    var popularity: Double = 0.5,
    var loyalty: Double = 0.5,
    var relationWithPlayer: Double = 0.5,
    val economicLeaning: Double = 0.0,
    val socialLeaning: Double = 0.0,
    val portraitSeed: Long = 0L,
    /** Traits que le joueur a appris à connaître au fil des interactions. */
    val knownTraits: MutableSet<String> = mutableSetOf(),
    var scandals: Int = 0,
    /** Dates (secondes du monde) des scandales : leur effet électoral s'estompe avec le temps. */
    val scandalDates: MutableList<Long> = mutableListOf(),
    var active: Boolean = true,
    /** Visage choisi par le joueur (sinon dérivé de la graine du portrait). */
    var appearance: Appearance? = null,
    /** Ce que le personnage retient de vous (au plus une dizaine de souvenirs). */
    val memories: MutableList<CharacterMemory> = mutableListOf(),
    /** Humeur du moment (-1 furieux .. 1 ravi), qui retombe avec le temps. */
    var mood: Double = 0.0,
    /** Rivalité d'un ministre ambitieux : 0 aucune, 1 ambitions affichées, 2 joue sa carte, 3 rupture. */
    var rivalry: Int = 0,
    /** Dernière fois que ce ministre est monté au créneau pour vous (secondes du monde). */
    var lastDefense: Long = 0L,
) {
    val fullName: String get() = "$firstName $lastName"

    /** Donne au personnage un nom imposé (dirigeants et figures inspirés de personnalités réelles). */
    fun rename(figure: fr.president.engine.data.NamedFigure) {
        firstName = figure.firstName
        lastName = figure.lastName
        female = figure.female
        figure.birthYear?.let { birthYear = it }
    }
    fun trait(id: String): Double = traits[id] ?: NEUTRAL_TRAIT
    fun age(currentYear: Int): Int = currentYear - birthYear

    companion object {
        const val NEUTRAL_TRAIT = 0.5
    }
}
