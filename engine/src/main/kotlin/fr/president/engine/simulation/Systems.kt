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
import fr.president.engine.military.MilitarySystem
import fr.president.engine.opinion.OpinionSystem
import fr.president.engine.territory.TerritorySystem

/** Liste ordonnée des systèmes : l'ordre définit les dépendances au sein d'un même pas. */
object Systems {
    fun default(): List<SimulationSystem> = listOf(
        InboxSystem(),
        EffectSystem(),
        InfrastructureSystem(),
        EventSystem(),
        OpinionSystem(),
        ElectionSystem(),
        ForeignAiSystem(),
        AgreementSystem(),
        EnergySystem(),
        EconomySystem(),
        ServiceQualitySystem(),
        TerritorySystem(),
        GovernmentSystem(),
        MilitarySystem(),
    )
}
