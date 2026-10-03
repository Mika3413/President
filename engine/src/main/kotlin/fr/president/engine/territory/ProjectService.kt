package fr.president.engine.territory

import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import fr.president.engine.simulation.SimulationContext

/** Achèvement des grands projets et application de leurs effets. */
class ProjectService(private val ctx: SimulationContext) {

    fun complete(projectId: String) {
        val project = ctx.state.projects.firstOrNull { it.id == projectId } ?: return
        if (project.status != ProjectStatus.IN_PROGRESS) return
        project.status = ProjectStatus.COMPLETED
        project.onCompletion.forEach { ctx.effects.trigger(it, null, emptyMap(), project.id) }
        ctx.state.infrastructure.values.filter { it.renovationProjectId == project.id }.forEach { it.renovationProjectId = null }
        ctx.notifications.post(NotificationCategory.PROJECTS, Urgency.IMPORTANT, "Projet terminé : ${project.name}",
            "Les travaux sont achevés.", project.locationId)
    }
}
