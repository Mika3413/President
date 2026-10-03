package fr.president.engine.military

import fr.president.engine.diplomacy.DiplomacyService
import fr.president.engine.diplomacy.RelationCalculator
import fr.president.engine.effects.EffectSpec
import fr.president.engine.economy.GrowthImpulse
import fr.president.engine.inbox.InboxMessage
import fr.president.engine.inbox.MessageOption
import fr.president.engine.inbox.MessageOrigin
import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import fr.president.engine.politics.Traits
import fr.president.engine.simulation.ScheduledAction
import fr.president.engine.simulation.SimulationContext

/**
 * Déclenchement, extension et fin des guerres : appels aux alliances, rupture des accords,
 * chocs économiques, mémoire diplomatique, cessez-le-feu et traités de paix.
 */
class WarService(private val ctx: SimulationContext) {
    private val geo = Geopolitics(ctx)
    private val player get() = ctx.state.player.countryId

    fun declare(attacker: String, defender: String, cause: String): War {
        geo.warBetween(attacker, defender)?.let { return it }
        val war = War(ctx.state.newId("war"), mutableListOf(attacker), mutableListOf(defender), ctx.now, cause)
        ctx.state.military.wars += war
        breakAgreements(attacker, defender)
        economicShock(attacker); economicShock(defender)
        // L'agressé et le reste du monde se souviennent de l'agression.
        ctx.state.countries.keys.filter { it != attacker }.forEach { observer ->
            val weight = if (observer == defender) AGGRESSION_VICTIM else AGGRESSION_WITNESS
            ctx.state.diplomacy.relation(observer, attacker).memories +=
                fr.president.engine.diplomacy.DiplomaticMemory("WAR", weight, ctx.now, "agression contre ${name(defender)}")
        }
        val involvesPlayer = attacker == player || defender == player
        if (attacker == player) ctx.state.player.warsThisTerm++
        ctx.notifications.post(NotificationCategory.MILITARY, Urgency.URGENT,
            "Guerre : ${name(attacker)} contre ${name(defender)}", cause, defender)
        ctx.notifications.news(NotificationCategory.MILITARY, "${name(attacker)} entre en guerre contre ${name(defender)}", defender)
        if (involvesPlayer) {
            val rally = if (defender == player) DEFENSIVE_RALLY else OFFENSIVE_COST
            ctx.state.opinion.groups.values.forEach { it.shock += rally }
            ctx.state.government.parliamentSupport = (ctx.state.government.parliamentSupport + rally).coerceIn(0.0, 1.0)
        }
        callAllies(war, defender)
        ctx.log("war", "Guerre ${war.id} : $attacker contre $defender ($cause)")
        return war
    }

    /** Les partenaires défensifs de l'agressé sont appelés à le rejoindre. */
    private fun callAllies(war: War, defender: String) {
        val attacker = war.attackers.first()
        for (partner in geo.defensivePartners(defender)) {
            if (partner in war.participants || !ctx.state.countries.containsKey(partner)) continue
            if (geo.allied(partner, attacker)) continue
            ctx.scheduler.schedule(ScheduledAction.AllianceCall(ctx.now.plusDays(CALL_DELAY_DAYS), war.id, partner))
        }
    }

    fun handleAllianceCall(warId: String, country: String) {
        val war = ctx.state.military.wars.firstOrNull { it.id == warId && it.status != WarStatus.ENDED } ?: return
        if (country in war.participants) return
        val defender = war.defenders.first()
        if (country == player) {
            askPlayer(war, defender)
            return
        }
        val leader = ctx.state.characters.getValue(ctx.state.countries.getValue(country).leaderId)
        val relation = RelationCalculator(ctx)
        val will = relation.score(country, defender) - relation.score(country, war.attackers.first()) +
            leader.trait(Traits.MILITARISM) * MILITARISM_WEIGHT - leader.trait(Traits.CAUTION) * CAUTION_WEIGHT
        ctx.log("ai.war", "$country appelé à défendre $defender : volonté %.2f".format(will))
        if (will > JOIN_THRESHOLD) join(war, country, defenderSide = true)
    }

    private fun askPlayer(war: War, defender: String) {
        val text = "${name(defender)} est attaqué par ${name(war.attackers.first())} et invoque nos engagements de défense mutuelle. " +
            "Entrer en guerre engagera nos forces ; refuser entamera durablement notre crédibilité auprès de nos alliés."
        ctx.state.inbox.messages += InboxMessage(
            id = ctx.state.newId("msg"), senderId = null, senderLabel = "Conseil de défense",
            subject = "Appel à la défense de ${name(defender)}", body = text, time = ctx.now,
            category = NotificationCategory.MILITARY, origin = MessageOrigin.ALLIANCE_CALL, originId = war.id,
            options = listOf(
                MessageOption(JOIN, "Entrer en guerre aux côtés de ${name(defender)}", "Nos forces pourront combattre"),
                MessageOption(SUPPORT, "Soutien sans combat", "Aide et sanctions, sans engagement de troupes"),
                MessageOption(DECLINE, "Rester à l'écart", "Notre parole sera mise en doute"),
            ),
            deadline = ctx.now.plusDays(RESPONSE_DAYS), defaultOptionId = SUPPORT, focusId = defender,
        )
        ctx.notifications.post(NotificationCategory.MILITARY, Urgency.URGENT, "Nos alliés appellent à l'aide", text, defender)
    }

    fun answerAllianceCall(message: InboxMessage, optionId: String) {
        message.chosenOptionId = optionId
        message.read = true
        val war = ctx.state.military.wars.firstOrNull { it.id == message.originId } ?: return
        val defender = war.defenders.first()
        val diplomacy = DiplomacyService(ctx)
        when (optionId) {
            JOIN -> join(war, player, defenderSide = true)
            SUPPORT -> diplomacy.remember(defender, "CRISIS_SOLIDARITY", "soutien face à l'agression", SUPPORT_WEIGHT)
            else -> war.defenders.forEach { diplomacy.remember(it, "AGREEMENT_BROKEN", "engagement de défense non tenu") }
        }
    }

    fun join(war: War, country: String, defenderSide: Boolean) {
        if (country in war.participants) return
        (if (defenderSide) war.defenders else war.attackers) += country
        val enemies = if (defenderSide) war.attackers else war.defenders
        enemies.forEach { breakAgreements(country, it) }
        economicShock(country)
        ctx.notifications.post(NotificationCategory.MILITARY, Urgency.URGENT,
            "${name(country)} entre en guerre",
            "${name(country)} rejoint ${if (defenderSide) "les défenseurs" else "les assaillants"} contre ${enemies.joinToString { name(it) }}.", country)
        if (country == player) {
            ctx.state.opinion.groups.values.forEach { it.shock += JOIN_COST }
            ctx.state.player.warsThisTerm++
        }
    }

    fun ceasefire(war: War, days: Int) {
        war.status = WarStatus.CEASEFIRE
        war.ceasefireUntil = ctx.now.plusDays(days.toLong())
        haltOffensives(war)
        ctx.notifications.post(NotificationCategory.MILITARY, Urgency.IMPORTANT, "Cessez-le-feu",
            "Les combats cessent pour $days jours entre ${war.attackers.joinToString { name(it) }} et ${war.defenders.joinToString { name(it) }}.")
    }

    /** Traité de paix : les zones occupées sont annexées ([keepOccupied]) ou restituées. */
    fun peace(war: War, keepOccupied: Boolean, outcome: String) {
        war.status = WarStatus.ENDED
        war.endedAt = ctx.now
        war.outcome = outcome
        val military = ctx.state.military
        val parties = war.participants.toSet()
        for ((zone, occupier) in military.occupied.toMap()) {
            val owner = geo.ownerOf(zone)
            if (owner !in parties || occupier !in parties) continue
            if (keepOccupied) military.annexed[zone] = occupier
            military.occupied.remove(zone)
        }
        haltOffensives(war, sendHome = true)
        ctx.notifications.post(NotificationCategory.MILITARY, Urgency.URGENT, "Paix signée", outcome)
        ctx.notifications.news(NotificationCategory.DIPLOMACY, "Fin de la guerre : $outcome")
    }

    private fun haltOffensives(war: War, sendHome: Boolean = false) {
        val orders = OrderService(ctx)
        ctx.state.military.units.values.filter { it.countryId in war.participants && !it.destroyed }.forEach { u ->
            val foreign = geo.ownerOf(u.zoneId).let { it != u.countryId && it != "" }
            if (sendHome && foreign) orders.issue(u.id, UnitOrder.RETREAT)
            else if (u.order == UnitOrder.ATTACK) orders.issue(u.id, UnitOrder.DEFEND)
        }
    }

    private fun breakAgreements(a: String, b: String) {
        ctx.state.diplomacy.agreements.filter { it.active && a in it.parties && b in it.parties }.forEach { it.active = false }
    }

    private fun economicShock(country: String) {
        val e = ctx.state.countries[country]?.economy ?: return
        e.businessConfidence = (e.businessConfidence - CONFIDENCE_SHOCK).coerceAtLeast(0.0)
        e.impulses += GrowthImpulse(GROWTH_SHOCK, SHOCK_MONTHS, "war")
        if (country == player) ctx.effects.trigger(EffectSpec("economy.inflation", INFLATION_SHOCK, days = SHOCK_DAYS), null, emptyMap(), "war")
    }

    private fun name(c: String) = ctx.db.country(c).definition.name

    companion object {
        const val JOIN = "join"
        const val SUPPORT = "support"
        const val DECLINE = "decline"
        private const val AGGRESSION_VICTIM = -0.5
        private const val AGGRESSION_WITNESS = -0.08
        private const val DEFENSIVE_RALLY = 0.05
        private const val OFFENSIVE_COST = -0.06
        private const val JOIN_COST = -0.02
        private const val CALL_DELAY_DAYS = 1L
        private const val RESPONSE_DAYS = 3.0
        private const val MILITARISM_WEIGHT = 0.3
        private const val CAUTION_WEIGHT = 0.25
        private const val JOIN_THRESHOLD = 0.05
        private const val SUPPORT_WEIGHT = 0.04
        private const val CONFIDENCE_SHOCK = 0.04
        private const val GROWTH_SHOCK = -0.008
        private const val SHOCK_MONTHS = 12
        private const val INFLATION_SHOCK = 0.004
        private const val SHOCK_DAYS = 90.0
    }
}
