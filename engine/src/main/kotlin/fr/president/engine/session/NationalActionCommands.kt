package fr.president.engine.session

import fr.president.engine.effects.EffectSpec
import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import fr.president.engine.simulation.ScheduledAction
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.territory.ActionCategory
import fr.president.engine.territory.LocalActionDef
import fr.president.engine.territory.ProjectState
import fr.president.engine.territory.ProjectStatus

/**
 * Décisions nationales prises directement par le président : décrets, plans, annonces,
 * déplacements à l'étranger. Chacune a un coût, une durée, un délai avant de pouvoir
 * recommencer et des effets (souvent contrastés : certains groupes y gagnent, d'autres non).
 */
class NationalActionCommands(private val ctx: SimulationContext) {
    private val presenter = ActionPresenter(ctx)

    val categories: List<ActionCategory> get() = ctx.playerData.nationalActions?.categories.orEmpty()
    val definitions: List<LocalActionDef> get() = ctx.playerData.nationalActions?.actions.orEmpty()

    fun actions(category: String? = null): List<ActionPresenter.ActionView> =
        definitions.filter { category == null || it.category == category }.map { presenter.view(it, blocker(it)) }

    /** Nombre de décisions possibles tout de suite, par rubrique (pastilles de l'interface). */
    fun availableCount(category: String): Int = definitions.count { it.category == category && blocker(it) == null }

    /** Décisions en cours d'application (plans, chantiers nationaux). */
    fun running(): List<ProjectState> = ctx.state.projects.filter { it.kind.startsWith(KIND) && it.status == ProjectStatus.IN_PROGRESS }

    fun perform(actionId: String): Result<String> = runCatching {
        val def = definitions.first { it.id == actionId }
        blocker(def)?.let { error(it) }
        val source = KIND + def.id
        def.immediate.forEach { ctx.effects.trigger(it, null, emptyMap(), source) }
        if (def.costBillions > 0) {
            ctx.effects.trigger(EffectSpec("budget.oneOff", def.costBillions, days = def.durationDays.coerceAtLeast(1).toDouble()), null, emptyMap(), source)
        }
        ctx.state.localActions[key(def.id)] = ctx.now
        if (def.durationDays > 0) {
            val project = ProjectState(
                id = ctx.state.newId("prj"),
                name = def.label,
                kind = source,
                locationId = "",
                startedAt = ctx.now,
                completesAt = ctx.now.plusDays(def.durationDays.toLong()),
                costBillions = def.costBillions,
                onCompletion = def.onCompletion,
            )
            ctx.state.projects += project
            ctx.scheduler.schedule(ScheduledAction.ProjectCompletion(project.completesAt, project.id))
            ctx.notifications.post(NotificationCategory.PROJECTS, Urgency.INFO, def.label,
                "Décision prise : plein effet dans ${presenter.durationText(def)}.", "")
            "${def.label} : décision prise, plein effet dans ${presenter.durationText(def)}."
        } else {
            def.onCompletion.forEach { ctx.effects.trigger(it, null, emptyMap(), source) }
            "${def.label} : c'est fait."
        }
    }

    private fun blocker(def: LocalActionDef): String? {
        if (ctx.state.player.gameOver != null) return "La partie est terminée."
        if (ctx.state.projects.any { it.kind == KIND + def.id && it.status == ProjectStatus.IN_PROGRESS }) return "Déjà en cours."
        presenter.wait(key(def.id), def.cooldownDays)?.let { return "Possible à nouveau dans $it." }
        return null
    }

    private companion object {
        const val KIND = "national:"
        fun key(id: String) = "national|$id"
    }
}
