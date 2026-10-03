package fr.president.engine.government

import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import fr.president.engine.politics.CharacterRole
import fr.president.engine.simulation.SimulationContext

/** Départs de ministres (démission, limogeage) : le ministère passe en intérim. */
class GovernmentChanges(private val ctx: SimulationContext) {

    fun dismiss(characterId: String) {
        val gov = ctx.state.government
        val ministry = gov.ministers.entries.firstOrNull { it.value == characterId }?.key ?: return
        val c = ctx.state.characters[characterId] ?: return
        gov.ministers.remove(ministry)
        c.role = CharacterRole.FORMER
        c.active = false
        val title = ctx.playerData.government!!.ministries.first { it.id == ministry }.title
        ctx.notifications.post(NotificationCategory.GOVERNMENT, Urgency.IMPORTANT, "Départ : ${c.fullName}",
            "${c.fullName} quitte le poste de $title. Le ministère est géré par intérim : nommez un successeur.")
    }
}
