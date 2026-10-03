package fr.president.engine.simulation

import fr.president.engine.time.WorldTime
import kotlinx.serialization.Serializable

/** Action planifiée à un instant précis du monde (réponse diplomatique, vote, élection...). */
@Serializable
sealed class ScheduledAction {
    abstract val at: WorldTime

    @Serializable
    data class ProposalResponse(override val at: WorldTime, val proposalId: String) : ScheduledAction()

    @Serializable
    data class PolicyVote(override val at: WorldTime, val proposalId: String) : ScheduledAction()

    @Serializable
    data class ElectionRound(override val at: WorldTime, val round: Int) : ScheduledAction()

    @Serializable
    data class EventReask(override val at: WorldTime, val eventInstanceId: String) : ScheduledAction()

    @Serializable
    data class EventDetails(override val at: WorldTime, val eventInstanceId: String) : ScheduledAction()

    /** Un événement tiré dans la journée survient à une heure précise (plus naturel qu'à minuit). */
    @Serializable
    data class EventLaunch(override val at: WorldTime, val definitionId: String, val scopeId: String?) : ScheduledAction()

    @Serializable
    data class ProjectCompletion(override val at: WorldTime, val projectId: String) : ScheduledAction()
}

@Serializable
class SchedulerState(val actions: MutableList<ScheduledAction> = mutableListOf())
