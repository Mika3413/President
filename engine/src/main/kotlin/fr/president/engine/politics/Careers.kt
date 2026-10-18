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
    /** Atout et faiblesse durables, en clair. */
    val perk: String = "",
    val flaw: String = "",
    /** Petits effets appliqués chaque mois (réseaux et méfiances). */
    val monthly: List<EffectSpec> = emptyList(),
    /** Ampleur des crises selon le parcours : < 1 atténuées, > 1 aggravées (identifiant d'événement). */
    val eventFactors: Map<String, Double> = emptyMap(),
)

/** Le parcours du président pèse tout au long du mandat. */
class CareerSystem : fr.president.engine.simulation.SimulationSystem {
    override val name = "career"
    override val cadence = fr.president.engine.simulation.Cadence.MONTHLY
    override fun run(ctx: fr.president.engine.simulation.SimulationContext) {
        val career = ctx.state.player.career?.let { id -> ctx.playerData.careers.firstOrNull { it.id == id } } ?: return
        career.monthly.forEach { ctx.effects.trigger(it, null, emptyMap(), "career:${career.id}") }
    }

    companion object {
        fun eventFactor(ctx: fr.president.engine.simulation.SimulationContext, eventId: String): Double =
            ctx.state.player.career?.let { id -> ctx.playerData.careers.firstOrNull { it.id == id } }?.eventFactors?.get(eventId) ?: 1.0
    }
}

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
