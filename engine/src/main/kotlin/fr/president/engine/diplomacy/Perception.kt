package fr.president.engine.diplomacy

import fr.president.engine.simulation.SimulationContext
import fr.president.engine.util.GameRandom
import fr.president.engine.util.Hashing

/**
 * Ce qu'un pays croit savoir d'un autre. L'IA ne lit jamais l'état réel directement :
 * elle reçoit une estimation bruitée, d'autant plus fausse que son renseignement est faible.
 * L'erreur est stable sur un mois pour éviter des revirements incohérents.
 */
class Perception(private val ctx: SimulationContext) {

    fun estimate(observer: String, subject: String, variable: String, trueValue: Double, scale: Double): Double {
        val quality = ctx.db.country(observer).definition.strategic.intelligenceQuality
        val seed = Hashing.fnv1a64("$observer|$subject|$variable|${ctx.now.monthIndex}|${ctx.state.meta.seed}")
        val error = GameRandom(seed).nextGaussian() * (1.0 - quality) * ctx.db.diplomacy.evaluation.perceptionNoise * scale
        return trueValue + error
    }

    /** Capacité d'exportation électrique du pays joueur telle que perçue par [observer] (TWh/an). */
    fun playerExportCapacity(observer: String): Double {
        val energy = ctx.state.energy
        val trueCapacity = energy.netExportTWh - energy.committedExportTWh
        return estimate(observer, ctx.state.player.countryId, "exportCapacity", trueCapacity, energy.demandTWh)
    }
}
