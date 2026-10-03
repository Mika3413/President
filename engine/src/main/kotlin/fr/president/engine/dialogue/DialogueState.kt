package fr.president.engine.dialogue

import fr.president.engine.events.InteractionOutcome
import fr.president.engine.time.WorldTime
import kotlinx.serialization.Serializable

@Serializable
data class InteractionRecord(
    val topic: String,
    val outcome: InteractionOutcome,
    val time: WorldTime,
)

@Serializable
class DialogueState(
    /** Signatures des messages déjà affichés dans cette sauvegarde. */
    val usedSignatures: MutableSet<Long> = mutableSetOf(),
    /** Mémoire des échanges par personnage. */
    val interactions: MutableMap<String, MutableList<InteractionRecord>> = mutableMapOf(),
)
