package fr.president.engine.politics

import fr.president.engine.effects.EffectSpec
import kotlinx.serialization.Serializable

/** Parcours du président avant son élection : compétences, tempérament, réseaux. */
@Serializable
data class CareerDef(
    val id: String,
    val label: String,
    val description: String,
    /** Bonus de compétence, gestion, expérience. */
    val skills: Map<String, Double> = emptyMap(),
    /** Décalage des traits de caractère. */
    val traits: Map<String, Double> = emptyMap(),
    /** Effets au début du mandat. */
    val effects: List<EffectSpec> = emptyList(),
)

@Serializable
data class CareersFile(val careers: List<CareerDef>)

/** Visage choisi par le joueur (indices dans les palettes du dessinateur de portraits). */
@Serializable
data class Appearance(
    val skin: Int = 0,
    val hair: Int = 0,
    /** 0 court, 1 long, 2 chauve, 3 attaché. */
    val hairStyle: Int = 0,
    val glasses: Boolean = false,
    val suit: Int = 0,
    val accent: Int = 0,
    val background: Int = 0,
    val grey: Boolean = false,
)
