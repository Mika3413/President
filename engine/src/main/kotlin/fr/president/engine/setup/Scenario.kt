package fr.president.engine.setup

import fr.president.engine.effects.EffectSpec
import fr.president.engine.simulation.ScheduledAction
import fr.president.engine.simulation.SimulationContext
import kotlinx.serialization.Serializable

@Serializable
data class ScenarioEvent(val event: String, val day: Double = 1.0, val scope: String? = null)

@Serializable
data class ScenarioWar(val attacker: String, val defender: String)

/** Situation de départ : la France réelle, plus une crise qui éclate dès la prise de fonctions. */
@Serializable
data class ScenarioDef(
    val id: String,
    val label: String,
    val icon: String = "",
    val description: String,
    val effects: List<EffectSpec> = emptyList(),
    val events: List<ScenarioEvent> = emptyList(),
    val wars: List<ScenarioWar> = emptyList(),
    /** Soutien parlementaire imposé au départ (Assemblée hostile...). */
    val parliamentSupport: Double? = null,
)

@Serializable
data class ScenariosFile(val scenarios: List<ScenarioDef>)

object ScenarioService {
    fun apply(ctx: SimulationContext, def: ScenarioDef) {
        ctx.state.player.scenario = def.id
        def.effects.forEach { ctx.effects.trigger(it, null, emptyMap(), "scenario:${def.id}") }
        def.parliamentSupport?.let { ctx.state.government.parliamentSupport = it }
        def.wars.forEach { w -> fr.president.engine.military.WarService(ctx).declare(w.attacker, w.defender, def.label) }
        def.events.forEach { e -> ctx.scheduler.schedule(ScheduledAction.EventLaunch(ctx.now.plusDays(e.day), e.event, e.scope)) }
    }
}
