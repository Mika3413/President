package fr.president.engine.simulation

import fr.president.engine.diplomacy.DiplomacyService
import fr.president.engine.elections.ElectionService
import fr.president.engine.events.EventFollowUps
import fr.president.engine.events.EventLauncher
import fr.president.engine.events.ScopeRef
import fr.president.engine.government.ParliamentService
import fr.president.engine.government.PolicyService
import fr.president.engine.military.ProductionService
import fr.president.engine.military.WarService
import fr.president.engine.territory.ProjectService

/** Exécute les actions planifiées en les confiant au service compétent. */
class ActionDispatcher(private val ctx: SimulationContext) {
    fun dispatch(action: ScheduledAction) {
        when (action) {
            is ScheduledAction.ProposalResponse -> DiplomacyService(ctx).respond(action.proposalId)
            is ScheduledAction.PolicyVote -> PolicyService(ctx).vote(action.proposalId)
            is ScheduledAction.ElectionRound -> ElectionService(ctx).runRound(action.round)
            is ScheduledAction.EventReask -> EventFollowUps(ctx).reask(action.eventInstanceId)
            is ScheduledAction.EventDetails -> EventFollowUps(ctx).details(action.eventInstanceId)
            is ScheduledAction.EventLaunch -> {
                val def = ctx.db.event(action.definitionId)
                EventLauncher(ctx).launch(def, ScopeRef(def.scope, action.scopeId))
            }
            is ScheduledAction.UnitDelivery -> ProductionService(ctx).deliver(action.orderId)
            is ScheduledAction.StockDelivery -> ProductionService(ctx).receiveStocks(action.ammunition, action.fuel)
            is ScheduledAction.MobilizationComplete -> ProductionService(ctx).completeMobilization(action.units)
            is ScheduledAction.AllianceCall -> WarService(ctx).handleAllianceCall(action.warId, action.country)
            is ScheduledAction.Tutorial -> fr.president.engine.setup.WelcomeMessage(ctx).tutorial(action.index)
            is ScheduledAction.LegislativeElection -> ParliamentService(ctx).runLegislative(action.at)
            is ScheduledAction.CensureVote -> ParliamentService(ctx).censureVote()
            is ScheduledAction.ReferendumVote -> ParliamentService(ctx).runReferendum(action.reformId)
            is ScheduledAction.SenateRenewal -> fr.president.engine.government.SenateService(ctx).renew(action.at)
            is ScheduledAction.EuropeanElection -> fr.president.engine.government.SenateService(ctx).runEuropean(action.at)
            is ScheduledAction.LocalElection -> fr.president.engine.elections.LocalElectionService(ctx).run(action.kindId, action.at)
            is ScheduledAction.ProjectCompletion -> ProjectService(ctx).complete(action.projectId)
        }
    }
}
