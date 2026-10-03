package fr.president.engine.politics

import fr.president.engine.data.GameDatabase
import fr.president.engine.util.GameRandom
import fr.president.engine.util.clamp01

/** Paramètres de génération d'un personnage. */
data class CharacterSpec(
    val countryId: String,
    val role: CharacterRole,
    val roleRef: String? = null,
    val currentYear: Int,
    val ageRange: IntRange = DEFAULT_AGE_RANGE,
    val economicLeaning: Double? = null,
    val leaningSpread: Double = DEFAULT_LEANING_SPREAD,
    val traitRanges: Map<String, List<Double>> = emptyMap(),
    val female: Boolean? = null,
    /** Position sociétale visée ; par défaut corrélée à la position économique. */
    val socialLeaning: Double? = null,
) {
    companion object {
        val DEFAULT_AGE_RANGE = 38..68
        const val DEFAULT_LEANING_SPREAD = 0.35
    }
}

/** Génère des personnages fictifs : nom, âge, tempérament, compétences, orientation abstraite. */
class CharacterGenerator(private val db: GameDatabase) {

    fun generate(id: String, spec: CharacterSpec, rng: GameRandom): Character {
        val pool = db.names[db.country(spec.countryId).definition.namePool]
            ?: error("Réserve de noms absente pour ${spec.countryId}")
        val female = spec.female ?: rng.chance(FEMALE_SHARE)
        val first = rng.pick(if (female) pool.femaleFirstNames else pool.maleFirstNames)
        val last = rng.pick(pool.lastNames)
        val age = spec.ageRange.first + rng.nextInt(spec.ageRange.last - spec.ageRange.first + 1)
        val traits = Traits.ALL.associateWith { trait ->
            val range = spec.traitRanges[trait]
            if (range != null && range.size == 2) rng.nextDouble(range[0], range[1]) else rng.nextDouble()
        }.toMutableMap()
        val leaning = ((spec.economicLeaning ?: 0.0) + rng.nextGaussian() * spec.leaningSpread).coerceIn(-1.0, 1.0)
        val social = ((spec.socialLeaning ?: (leaning * SOCIAL_CORRELATION)) + rng.nextGaussian() * spec.leaningSpread).coerceIn(-1.0, 1.0)
        val experience = ((age - spec.ageRange.first).toDouble() / AGE_EXPERIENCE_SPAN + rng.nextDouble() * EXPERIENCE_NOISE).clamp01()
        return Character(
            id = id,
            firstName = first,
            lastName = last,
            female = female,
            birthYear = spec.currentYear - age,
            countryId = spec.countryId,
            role = spec.role,
            roleRef = spec.roleRef,
            traits = traits,
            competence = skill(rng),
            management = skill(rng),
            experience = experience,
            popularity = skill(rng),
            loyalty = rng.nextDouble(LOYALTY_MIN, LOYALTY_MAX),
            relationWithPlayer = rng.nextDouble(RELATION_MIN, RELATION_MAX),
            economicLeaning = leaning,
            socialLeaning = social,
            portraitSeed = rng.nextLong(),
        )
    }

    /** Compétences centrées sur la moyenne, rarement extrêmes. */
    private fun skill(rng: GameRandom): Double = (SKILL_MEAN + rng.nextGaussian() * SKILL_SPREAD).clamp01()

    private companion object {
        const val FEMALE_SHARE = 0.45
        const val SOCIAL_CORRELATION = 0.5
        const val AGE_EXPERIENCE_SPAN = 30.0
        const val EXPERIENCE_NOISE = 0.3
        const val SKILL_MEAN = 0.55
        const val SKILL_SPREAD = 0.17
        const val LOYALTY_MIN = 0.45
        const val LOYALTY_MAX = 0.8
        const val RELATION_MIN = 0.4
        const val RELATION_MAX = 0.65
    }
}
