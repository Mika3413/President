package fr.president.engine.dialogue

import fr.president.engine.politics.Character
import fr.president.engine.politics.Traits

/**
 * Manière d'écrire d'un personnage, stable pendant toute la partie : deux maires ne rédigent pas
 * de la même façon. Déduite de son tempérament, avec une part propre à chaque individu.
 */
enum class Voice(val tag: String) {
    /** Protocolaire, phrases longues et formules de politesse soignées. */
    FORMAL("voice:formal"),
    /** Va droit au but, phrases courtes. */
    DIRECT("voice:direct"),
    /** Chaleureux, personnel. */
    WARM("voice:warm"),
    /** Chiffres, procédures, vocabulaire administratif. */
    TECHNICAL("voice:technical"),
    /** Images, envolées, références au territoire et à l'histoire. */
    LYRICAL("voice:lyrical"),
    /** Sec, parfois abrupt. */
    BLUNT("voice:blunt");

    companion object {
        private const val DOMINANT = 0.8
        private val BY_TRAIT = listOf(
            Traits.AGGRESSIVENESS to BLUNT,
            Traits.OPENNESS to WARM,
            Traits.PRAGMATISM to DIRECT,
            Traits.EGO to LYRICAL,
            Traits.CAUTION to TECHNICAL,
        )

        fun of(c: Character): Voice {
            // Un trait très marqué impose son style ; sinon l'individualité (graine du portrait) décide.
            val (trait, voice) = BY_TRAIT.maxBy { c.trait(it.first) }
            if (c.trait(trait) > DOMINANT) return voice
            return entries[Math.floorMod(c.portraitSeed, entries.size.toLong()).toInt()]
        }
    }
}
