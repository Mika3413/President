package fr.president.engine.effects

import fr.president.engine.simulation.Cadence
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.simulation.SimulationSystem

/** Applique progressivement les effets différés et étalés dans le temps. */
class EffectSystem : SimulationSystem {
    override val name = "effects"
    override val cadence = Cadence.DAILY

    override fun run(ctx: SimulationContext) {
        val now = ctx.now
        val finished = mutableListOf<ActiveEffect>()
        for (effect in ctx.state.effects.toList()) {
            if (now < effect.startsAt) continue
            val span = (effect.endsAt.seconds - effect.startsAt.seconds).toDouble()
            val progress = if (span <= 0) 1.0 else ((now.seconds - effect.startsAt.seconds) / span).coerceIn(0.0, 1.0)
            val delta = effect.total * progress - effect.applied
            if (delta != 0.0) {
                ctx.effects.apply(effect.target, delta)
                effect.applied += delta
            }
            if (progress >= 1.0) finished += effect
        }
        ctx.state.effects.removeAll(finished)
    }
}
