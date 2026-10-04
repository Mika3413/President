package fr.president.engine.inbox

import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.time.WorldTime
import kotlinx.serialization.Serializable

@Serializable
enum class MessageOrigin { EVENT, PROPOSAL, DIPLOMATIC_RESPONSE, INFO, ALLIANCE_CALL, ULTIMATUM, CONVERSATION, CABINET, EU }

@Serializable
data class MessageOption(val id: String, val label: String, val hint: String = "")

/** Message procédural reçu par le président, avec éventuellement des choix de réponse. */
@Serializable
class InboxMessage(
    val id: String,
    val senderId: String?,
    val senderLabel: String,
    val subject: String,
    val body: String,
    val time: WorldTime,
    val category: NotificationCategory,
    val origin: MessageOrigin,
    val originId: String?,
    val options: List<MessageOption> = emptyList(),
    val deadline: WorldTime? = null,
    val defaultOptionId: String? = null,
    val focusId: String? = null,
    var chosenOptionId: String? = null,
    var read: Boolean = false,
    /** Vrai si la réponse a été donnée automatiquement faute de décision du joueur. */
    var answeredByDefault: Boolean = false,
    /** Mesures de crise qu'on peut lancer en plus de la réponse (confinement, évacuation...). */
    val measures: List<String> = emptyList(),
    /** Département visé par les mesures locales proposées. */
    val measureDepartment: String? = null,
) {
    val awaitingAnswer: Boolean get() = options.isNotEmpty() && chosenOptionId == null
}

@Serializable
class InboxState(val messages: MutableList<InboxMessage> = mutableListOf())
