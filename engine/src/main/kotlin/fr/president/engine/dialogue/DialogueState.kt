package fr.president.engine.dialogue

import fr.president.engine.events.InteractionOutcome
import fr.president.engine.time.WorldTime
import kotlinx.serialization.Serializable

@Serializable
data class InteractionRecord(
    val topic: String,
    val outcome: InteractionOutcome,
    val time: WorldTime,
    /** Libellé lisible du sujet (« le financement du tramway »), cité dans les courriers suivants. */
    val label: String? = null,
)

@Serializable
class DialogueState(
    /** Signatures des messages déjà affichés dans cette sauvegarde. */
    val usedSignatures: MutableSet<Long> = mutableSetOf(),
    /** Mémoire des échanges par personnage. */
    val interactions: MutableMap<String, MutableList<InteractionRecord>> = mutableMapOf(),
    /** Nombre de messages composés (horloge de la mémoire des phrases). */
    var composedCount: Long = 0,
    /** Phrase (variante) -> numéro du dernier message qui l'a employée. */
    val phraseLastUse: MutableMap<Long, Long> = mutableMapOf(),
    /** Personnage -> phrases qu'il a déjà écrites : un interlocuteur ne se répète jamais. */
    val phrasesBySender: MutableMap<String, MutableSet<Long>> = mutableMapOf(),
    /** Personnage -> date de la dernière conversation à l'initiative du président. */
    val lastConversation: MutableMap<String, WorldTime> = mutableMapOf(),
)
