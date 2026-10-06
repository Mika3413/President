package fr.president.engine.diplomacy

import fr.president.engine.data.theCountry
import fr.president.engine.data.ofCountry
import fr.president.engine.data.toCountry

import fr.president.engine.inbox.InboxMessage
import fr.president.engine.inbox.MessageOption
import fr.president.engine.inbox.MessageOrigin
import fr.president.engine.military.Geopolitics
import fr.president.engine.military.UnitOrder
import fr.president.engine.military.OrderService
import fr.president.engine.military.WarService
import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import fr.president.engine.politics.Traits
import fr.president.engine.simulation.SimulationContext

/** Exigence d'un ultimatum. */
enum class Demand(val label: String) {
    END_WAR("Mettre fin à sa guerre"),
    WITHDRAW("Retirer ses troupes des territoires occupés"),
    LIFT_SANCTIONS("Lever ses sanctions"),
    STOP_THREATS("Cesser ses menaces"),
}

/**
 * Ultimatums : le destinataire compare le rapport de forces perçu (alliés compris),
 * sa prudence et son nationalisme. Céder a un coût ; refuser expose à la guerre.
 */
class UltimatumService(private val ctx: SimulationContext) {
    private val geo = Geopolitics(ctx)
    private val player get() = ctx.state.player.countryId

    data class Result(val accepted: Boolean, val explanation: String)

    fun send(from: String, to: String, demand: Demand): Result {
        val leader = ctx.state.characters.getValue(ctx.state.countries.getValue(to).leaderId)
        val ours = geo.landPower(from) + (geo.defensivePartners(from) + geo.coBelligerents(from)).sumOf { geo.landPower(it) } * ALLY_SHARE
        val theirs = geo.landPower(to) + geo.coBelligerents(to).sumOf { geo.landPower(it) } * ALLY_SHARE
        val deterrence = if (geo.isNuclear(from) && !geo.isNuclear(to)) NUCLEAR_DETERRENCE else 0.0
        val ratio = (ours + 1) / (theirs + 1)
        val will = ratio * (1 + deterrence) * (0.5 + leader.trait(Traits.CAUTION)) - leader.trait(Traits.NATIONALISM) - leader.trait(Traits.EGO) * EGO_WEIGHT
        ctx.state.diplomacy.relation(to, from).memories += DiplomaticMemory("ULTIMATUM", ULTIMATUM_WEIGHT, ctx.now, demand.label.lowercase())
        ctx.log("ai.ultimatum", "$to reçoit un ultimatum de $from (${demand.name}) : rapport %.2f, volonté de céder %.2f".format(ratio, will))
        val accepted = will > ACCEPT_THRESHOLD
        if (accepted) comply(to, from, demand)
        val text = if (accepted) "${name(to)} cède à l'ultimatum : ${demand.label.lowercase()}."
        else "${name(to)} rejette l'ultimatum et dénonce une provocation."
        if (from == player) {
            ctx.notifications.post(NotificationCategory.DIPLOMACY, Urgency.URGENT, if (accepted) "Ultimatum accepté" else "Ultimatum rejeté", text, to)
            if (!accepted) askEscalation(to, demand)
        }
        return Result(accepted, text)
    }

    private fun comply(country: String, demander: String, demand: Demand) {
        when (demand) {
            Demand.END_WAR -> geo.ongoingWars().filter { country in it.participants }.forEach {
                WarService(ctx).peace(it, keepOccupied = false, outcome = "${name(country)} met fin à la guerre sous la pression ${ctx.db.ofCountry(demander)}.")
            }
            Demand.WITHDRAW -> {
                ctx.state.military.occupied.filter { it.value == country }.keys.forEach { ctx.state.military.occupied.remove(it) }
                val orders = OrderService(ctx)
                ctx.state.military.units.values.filter { it.countryId == country && !it.destroyed && geo.ownerOf(it.zoneId) != country }
                    .forEach { orders.issue(it.id, UnitOrder.RETREAT) }
            }
            Demand.LIFT_SANCTIONS -> SanctionsService(ctx).lift(country, demander)
            Demand.STOP_THREATS -> ctx.state.diplomacy.relation(demander, country).memories.removeAll { it.kind == "THREAT" }
        }
    }

    private fun askEscalation(country: String, demand: Demand) {
        ctx.state.inbox.messages += InboxMessage(
            id = ctx.state.newId("msg"), senderId = null, senderLabel = "Conseil de défense",
            subject = "${name(country)} rejette notre ultimatum",
            body = "L'exigence « ${demand.label.lowercase()} » a été rejetée. Mettre nos menaces à exécution signifie la guerre ; " +
                "y renoncer affaiblira durablement notre crédibilité.",
            time = ctx.now, category = NotificationCategory.DIPLOMACY, origin = MessageOrigin.ULTIMATUM, originId = country,
            options = listOf(MessageOption(WAR, "Déclarer la guerre", "Les opérations peuvent commencer"), MessageOption(BACK_DOWN, "Renoncer", "Perte de crédibilité")),
            deadline = ctx.now.plusDays(RESPONSE_DAYS), defaultOptionId = BACK_DOWN, focusId = country,
        )
    }

    fun answerPlayer(message: InboxMessage, optionId: String) {
        message.chosenOptionId = optionId
        message.read = true
        val country = message.originId ?: return
        if (optionId == WAR) {
            WarService(ctx).declare(player, country, "La France met ses menaces à exécution après le rejet de son ultimatum.")
        } else {
            ctx.state.countries.keys.filter { it != player }.forEach { DiplomacyService(ctx).remember(it, "BACKED_DOWN", "ultimatum sans suite") }
            ctx.state.opinion.groups.values.forEach { it.shock += BACK_DOWN_OPINION }
        }
    }

    private fun name(c: String) = ctx.db.country(c).definition.name

    companion object {
        const val WAR = "war"
        const val BACK_DOWN = "back_down"
        private const val ALLY_SHARE = 0.5
        private const val NUCLEAR_DETERRENCE = 0.5
        private const val EGO_WEIGHT = 0.5
        private const val ULTIMATUM_WEIGHT = -0.1
        private const val ACCEPT_THRESHOLD = 0.6
        private const val RESPONSE_DAYS = 3.0
        private const val BACK_DOWN_OPINION = -0.015
    }
}
