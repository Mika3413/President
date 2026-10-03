package fr.president.engine.world

import fr.president.engine.diplomacy.DiplomacyState
import fr.president.engine.dialogue.DialogueState
import fr.president.engine.effects.ActiveEffect
import fr.president.engine.elections.ElectionState
import fr.president.engine.energy.EnergyState
import fr.president.engine.events.EventState
import fr.president.engine.government.GovernmentState
import fr.president.engine.government.PolicyState
import fr.president.engine.inbox.InboxState
import fr.president.engine.infrastructure.InfrastructureState
import fr.president.engine.military.MilitaryState
import fr.president.engine.notifications.NotificationState
import fr.president.engine.opinion.OpinionState
import fr.president.engine.politics.Character
import fr.president.engine.simulation.SchedulerState
import fr.president.engine.territory.ProjectState
import fr.president.engine.territory.TerritoryState
import fr.president.engine.time.WorldClock
import fr.president.engine.time.WorldTime
import fr.president.engine.util.GameRandom
import kotlinx.serialization.Serializable

@Serializable
data class WorldMeta(
    val snapshotId: String,
    val dataVersion: Int,
    val seed: Long,
    val clock: WorldClock,
    val createdAtRealUtc: Long,
    val startTime: WorldTime,
)

/**
 * État complet et sérialisable d'une partie. Aucune logique ici : les systèmes le font évoluer.
 * Une fois créé à partir d'un snapshot, il est totalement indépendant des données réelles.
 */
@Serializable
class WorldState(
    val meta: WorldMeta,
    var time: WorldTime,
    val rng: GameRandom,
    val player: PlayerState,
    val countries: MutableMap<String, CountryState> = mutableMapOf(),
    val characters: MutableMap<String, Character> = mutableMapOf(),
    val government: GovernmentState = GovernmentState(),
    val territory: TerritoryState = TerritoryState(),
    val infrastructure: MutableMap<String, InfrastructureState> = mutableMapOf(),
    val energy: EnergyState,
    val military: MilitaryState = MilitaryState(),
    val opinion: OpinionState = OpinionState(),
    val elections: ElectionState,
    val events: EventState = EventState(),
    val effects: MutableList<ActiveEffect> = mutableListOf(),
    val inbox: InboxState = InboxState(),
    val notifications: NotificationState = NotificationState(),
    val diplomacy: DiplomacyState = DiplomacyState(),
    val dialogue: DialogueState = DialogueState(),
    val scheduler: SchedulerState = SchedulerState(),
    val projects: MutableList<ProjectState> = mutableListOf(),
    val policy: PolicyState = PolicyState(),
    val parliament: fr.president.engine.government.ParliamentState = fr.president.engine.government.ParliamentState(),
    val localElections: fr.president.engine.elections.LocalElectionState = fr.president.engine.elections.LocalElectionState(),
    var nextId: Long = 1,
    val demography: fr.president.engine.territory.DemographyState = fr.president.engine.territory.DemographyState(),
) {
    val playerCountry: CountryState get() = countries.getValue(player.countryId)

    fun newId(prefix: String): String = "$prefix-${nextId++}"
}
