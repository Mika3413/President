package fr.president.engine.session

import fr.president.engine.effects.EffectSpec
import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import fr.president.engine.simulation.ScheduledAction
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.territory.LocalActionDef
import fr.president.engine.territory.ProjectState
import fr.president.engine.territory.ProjectStatus

/** Actions directes du président sur un département (chantiers, visites, plans locaux). */
class LocalActionCommands(private val ctx: SimulationContext) {

    private val presenter = ActionPresenter(ctx)

    val definitions: List<LocalActionDef> get() = ctx.playerData.localActions?.actions.orEmpty()
    val categories: List<fr.president.engine.territory.ActionCategory> get() = ctx.playerData.localActions?.categories.orEmpty()

    fun actionsFor(departmentCode: String): List<ActionPresenter.ActionView> = definitions.map { def ->
        presenter.view(def, blocker(departmentCode, def))
    }

    fun perform(departmentCode: String, actionId: String): Result<String> = runCatching {
        val def = definitions.first { it.id == actionId }
        blocker(departmentCode, def)?.let { error(it) }
        val dept = ctx.state.territory.departments.getValue(departmentCode)
        val place = ctx.playerData.territory!!.departments.first { it.code == dept.code }.name
        def.immediate.forEach { ctx.effects.trigger(resolve(it, departmentCode), null, emptyMap(), "local:${def.id}") }
        if (def.costBillions > 0) {
            ctx.effects.trigger(EffectSpec("budget.oneOff", def.costBillions, days = def.durationDays.coerceAtLeast(1).toDouble()), null, emptyMap(), "local:${def.id}")
        }
        ctx.state.localActions[key(departmentCode, def.id)] = ctx.now
        if (def.globalCooldownDays > 0) ctx.state.localActions[key(ANY, def.id)] = ctx.now
        if (def.durationDays > 0) {
            val project = ProjectState(
                id = ctx.state.newId("prj"),
                name = "${def.label} — $place",
                kind = kind(def.id),
                locationId = departmentCode,
                startedAt = ctx.now,
                completesAt = ctx.now.plusDays(def.durationDays.toLong()),
                costBillions = def.costBillions,
                onCompletion = def.onCompletion.map { resolve(it, departmentCode) },
            )
            ctx.state.projects += project
            ctx.scheduler.schedule(ScheduledAction.ProjectCompletion(project.completesAt, project.id))
            ctx.notifications.post(NotificationCategory.PROJECTS, Urgency.INFO, project.name,
                "Chantier lancé : fin prévue dans ${presenter.durationText(def)}.", departmentCode)
            "Chantier lancé : fin prévue dans ${presenter.durationText(def)}."
        } else {
            def.onCompletion.forEach { ctx.effects.trigger(resolve(it, departmentCode), null, emptyMap(), "local:${def.id}") }
            "${def.label} : c'est fait."
        }
    }

    private fun blocker(code: String, def: LocalActionDef): String? {
        if (ctx.state.player.gameOver != null) return "La partie est terminée."
        val running = ctx.state.projects.any { it.locationId == code && it.kind == kind(def.id) && it.status == ProjectStatus.IN_PROGRESS }
        if (running) return "Chantier déjà en cours ici."
        presenter.wait(key(code, def.id), def.cooldownDays)?.let { return "Possible à nouveau ici dans $it." }
        presenter.wait(key(ANY, def.id), def.globalCooldownDays)?.let { return "Possible à nouveau dans $it." }
        return null
    }

    private fun resolve(spec: EffectSpec, code: String): EffectSpec =
        if (spec.target.startsWith(LOCAL)) spec.copy(target = "dept.$code." + spec.target.removePrefix(LOCAL)) else spec

    private companion object {
        const val LOCAL = "local."
        const val ANY = "*"
        fun key(code: String, id: String) = "$code|$id"
        fun kind(id: String) = "local:$id"
    }
}
