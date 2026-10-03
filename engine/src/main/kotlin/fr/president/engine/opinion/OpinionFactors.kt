package fr.president.engine.opinion

import fr.president.engine.data.OpinionFactorDef
import fr.president.engine.simulation.SimulationContext

/** Convertit l'état de la simulation en scores de facteurs d'opinion (-1 = très mauvais, +1 = très bon). */
class OpinionFactors(private val ctx: SimulationContext) {

    fun compute(factors: List<OpinionFactorDef>): Map<String, Double> = factors.associate { f ->
        f.id to score(f)
    }

    private fun score(f: OpinionFactorDef): Double {
        val value = ctx.variables.resolve(f.variable) ?: return 0.0
        val neutral = if (f.relativeToStart) ctx.state.opinion.startValues[f.variable] ?: f.neutral else f.neutral
        val raw = if (f.lowerIsBetter) (neutral - value) / f.scale else (value - neutral) / f.scale
        return raw.coerceIn(-1.0, 1.0)
    }
}
