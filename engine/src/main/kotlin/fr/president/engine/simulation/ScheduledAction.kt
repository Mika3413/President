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
    data class UnitDelivery(override val at: WorldTime, val orderId: String) : ScheduledAction()

    @Serializable
    data class StockDelivery(override val at: WorldTime, val ammunition: Double, val fuel: Double) : ScheduledAction()

    @Serializable
    data class MobilizationComplete(override val at: WorldTime, val units: Int) : ScheduledAction()

    @Serializable
    data class AllianceCall(override val at: WorldTime, val warId: String, val country: String) : ScheduledAction()

    @Serializable
    data class Tutorial(override val at: WorldTime, val index: Int) : ScheduledAction()

    @Serializable
    data class LegislativeElection(override val at: WorldTime) : ScheduledAction()

    /** Motion de censure : [proposalId] est le texte passé en force qui l'a provoquée, s'il y en a un. */
    @Serializable
    data class CensureVote(override val at: WorldTime, val proposalId: String?) : ScheduledAction()

    @Serializable
    data class ReferendumVote(override val at: WorldTime, val reformId: String) : ScheduledAction()

    @Serializable
    data class ProjectCompletion(override val at: WorldTime, val projectId: String) : ScheduledAction()
}

@Serializable
class SchedulerState(val actions: MutableList<ScheduledAction> = mutableListOf())
