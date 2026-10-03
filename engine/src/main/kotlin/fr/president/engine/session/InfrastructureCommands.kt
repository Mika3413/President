package fr.president.engine.session

import fr.president.engine.effects.EffectSpec
import fr.president.engine.events.EventScope
import fr.president.engine.events.ScopeRef
import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import fr.president.engine.simulation.ScheduledAction
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.territory.ProjectState

/** Décisions sur les infrastructures, prises directement depuis la carte. */
class InfrastructureCommands(private val ctx: SimulationContext) {

    fun setMaintenance(id: String, level: Double) {
        ctx.state.infrastructure.getValue(id).maintenanceLevel = level.coerceIn(MIN_MAINTENANCE, MAX_MAINTENANCE)
    }

    fun renovationCost(id: String): Double {
        val def = ctx.catalog.item(id)!!
        val type = ctx.db.infrastructureTypes.getValue(def.type)
        return (type.renovationBaseCostMillions + type.renovationCostPerMW * def.capacityMW) / MILLIONS_PER_BILLION
    }

    fun renovate(id: String): Result<ProjectState> = runCatching {
        val infra = ctx.state.infrastructure.getValue(id)
        require(infra.renovationProjectId == null) { "Une rénovation est déjà en cours" }
        require(!infra.closed) { "Installation fermée" }
        val def = ctx.catalog.item(id)!!
        val type = ctx.db.infrastructureTypes.getValue(def.type)
        val cost = renovationCost(id)
        val project = ProjectState(
            id = ctx.state.newId("prj"),
            name = "Rénovation : ${def.name}",
            kind = "renovation",
            locationId = id,
            startedAt = ctx.now,
            completesAt = ctx.now.plusDays(type.renovationDays.toLong()),
            costBillions = cost,
            onCompletion = listOf(EffectSpec("infra.$id.condition", RENOVATION_GAIN)),
        )
        ctx.state.projects += project
        infra.renovationProjectId = project.id
        ctx.effects.trigger(EffectSpec("budget.oneOff", cost, days = type.renovationDays.toDouble()), null, emptyMap(), project.id)
        ctx.scheduler.schedule(ScheduledAction.ProjectCompletion(project.completesAt, project.id))
        ctx.notifications.post(NotificationCategory.PROJECTS, Urgency.INFO, project.name, "Durée prévue : ${type.renovationDays} jours.", id)
        project
    }

    /** Fermeture définitive : économies d'entretien mais pertes d'emplois et colère locale. */
    fun close(id: String): Result<Unit> = runCatching {
        val def = ctx.catalog.item(id)!!
        val type = ctx.db.infrastructureTypes.getValue(def.type)
        require(type.canClose) { "Ce type d'installation ne peut pas être fermé" }
        val infra = ctx.state.infrastructure.getValue(id)
        infra.closed = true
        val dept = ctx.state.territory.departments.getValue(def.department)
        val scope = ScopeRef(EventScope.INFRASTRUCTURE, id)
        ctx.effects.trigger(EffectSpec("scope.unemployment", def.employees * JOB_MULTIPLIER / (dept.population * ACTIVE_SHARE), days = CLOSURE_DAYS), scope, emptyMap(), id)
        ctx.effects.trigger(EffectSpec("scope.approval", CLOSURE_ANGER, days = CLOSURE_DAYS / 2), scope, emptyMap(), id)
        ctx.notifications.post(NotificationCategory.ENERGY, Urgency.IMPORTANT, "Fermeture : ${def.name}",
            "${def.employees} emplois directs sont concernés. La population locale proteste.", id)
    }

    private companion object {
        const val MIN_MAINTENANCE = 0.5
        const val MAX_MAINTENANCE = 2.0
        const val MILLIONS_PER_BILLION = 1000.0
        const val RENOVATION_GAIN = 0.35
        /** Emplois indirects induits par un emploi direct. */
        const val JOB_MULTIPLIER = 2.0
        const val ACTIVE_SHARE = 0.45
        const val CLOSURE_DAYS = 180.0
        const val CLOSURE_ANGER = -0.08
    }
}
