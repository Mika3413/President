package fr.president.engine.simulation

import fr.president.engine.ai.ForeignAiSystem
import fr.president.engine.diplomacy.AgreementSystem
import fr.president.engine.economy.EconomySystem
import fr.president.engine.economy.ServiceQualitySystem
import fr.president.engine.effects.EffectSystem
import fr.president.engine.elections.ElectionSystem
import fr.president.engine.energy.EnergySystem
import fr.president.engine.events.EventSystem
import fr.president.engine.government.GovernmentSystem
import fr.president.engine.inbox.InboxSystem
import fr.president.engine.infrastructure.InfrastructureSystem
import fr.president.engine.military.CombatSystem
import fr.president.engine.military.LogisticsSystem
import fr.president.engine.military.MilitaryAiSystem
import fr.president.engine.military.MilitarySystem
import fr.president.engine.military.MovementSystem
import fr.president.engine.military.WarSystem
import fr.president.engine.opinion.OpinionSystem
import fr.president.engine.territory.TerritorySystem

/** Liste ordonnée des systèmes : l'ordre définit les dépendances au sein d'un même pas. */
object Systems {
    fun default(): List<SimulationSystem> = listOf(
        InboxSystem(),
        MovementSystem(),
        CombatSystem(),
        LogisticsSystem(),
        WarSystem(),
        MilitaryAiSystem(),
        fr.president.engine.military.ForeignOperationsSystem(),
        fr.president.engine.military.CitiesSystem(),
        fr.president.engine.crisis.MeasureSystem(),
        EffectSystem(),
        InfrastructureSystem(),
        EventSystem(),
        OpinionSystem(),
        ElectionSystem(),
        ForeignAiSystem(),
        fr.president.engine.ai.WorldPoliticsSystem(),
        AgreementSystem(),
        fr.president.engine.economy.TradeSystem(),
        EnergySystem(),
        EconomySystem(),
        fr.president.engine.economy.SectorSystem(),
        fr.president.engine.economy.MonetarySystem(),
        ServiceQualitySystem(),
        TerritorySystem(),
        fr.president.engine.territory.DemographySystem(),
        GovernmentSystem(),
        fr.president.engine.government.CabinetSystem(),
        fr.president.engine.diplomacy.EuSystem(),
        fr.president.engine.diplomacy.UnSystem(),
        fr.president.engine.government.LawSystem(),
        fr.president.engine.legislation.LegislationSystem(),
        fr.president.engine.politics.ActorSystem(),
        fr.president.engine.politics.UnrestSystem(),
        fr.president.engine.consequences.ConsequenceSystem(),
        MilitarySystem(),
        fr.president.engine.military.DefenseSystem(),
        fr.president.engine.military.IntelSystem(),
        fr.president.engine.stats.StatsSystem(),
        fr.president.engine.media.MediaSystem(),
    )
}
