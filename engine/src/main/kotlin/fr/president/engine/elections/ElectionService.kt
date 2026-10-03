package fr.president.engine.elections

import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import fr.president.engine.politics.CharacterRole
import fr.president.engine.politics.CharacterSpec
import fr.president.engine.simulation.ScheduledAction
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.util.Formatting
import fr.president.engine.world.GameOver
import kotlin.math.abs

/** Organisation des scrutins présidentiels : candidats, sondages, tours, résultats. */
class ElectionService(private val ctx: SimulationContext) {
    private val def = ctx.playerData.elections!!

    /** Génère les candidats d'opposition pour le prochain scrutin. */
    fun prepareCandidates() {
        val state = ctx.state.elections
        state.candidates.clear()
        val president = ctx.state.characters.getValue(ctx.state.player.presidentId)
        val presidentFamily = def.families.minByOrNull { abs(it.economicPosition - president.economicLeaning) }!!
        state.candidates += Candidate(president.id, presidentFamily.id, true, president.economicLeaning, president.socialLeaning)
        val year = ctx.now.toDateTime().year
        for (family in def.families.filter { it.id != presidentFamily.id }) {
            val c = ctx.characters.generate(
                ctx.state.newId("chr"),
                CharacterSpec(ctx.state.player.countryId, CharacterRole.PRESIDENTIAL_CANDIDATE, family.id, year,
                    economicLeaning = family.economicPosition, leaningSpread = CANDIDATE_SPREAD),
                ctx.rng,
            )
            ctx.state.characters[c.id] = c
            state.candidates += Candidate(c.id, family.id, false, family.economicPosition, family.socialPosition)
        }
    }

    fun schedule() {
        ctx.scheduler.schedule(ScheduledAction.ElectionRound(ctx.state.elections.nextElection, FIRST_ROUND))
    }

    fun poll() {
        val state = ctx.state.elections
        val simulator = ElectionSimulator(ctx)
        val first = simulator.simulate(state.candidates, def.pollNoise)
        state.latestPoll = first
        val runoff = topTwo(first).let { pair -> state.candidates.filter { it.characterId in pair } }
        state.latestRunoffPoll = if (runoff.any { it.incumbent }) simulator.simulate(runoff, def.pollNoise) else null
        state.lastPollAt = ctx.now
    }

    fun runRound(round: Int) {
        val state = ctx.state.elections
        val simulator = ElectionSimulator(ctx)
        val incumbentId = ctx.state.player.presidentId
        if (round == FIRST_ROUND) {
            val result = simulator.simulate(state.candidates, def.pollNoise / 2)
            state.pendingFirstRound = result
            val qualified = topTwo(result)
            val share = Formatting.percent(result.shares[incumbentId] ?: 0.0)
            if (incumbentId !in qualified) {
                finish(result, null, qualified.first(), false)
                return
            }
            ctx.notifications.post(NotificationCategory.ELECTIONS, Urgency.URGENT, "Premier tour : qualification pour le second tour",
                "Vous obtenez $share des suffrages. Second tour dans ${def.secondRoundGapDays} jours.")
            ctx.scheduler.schedule(ScheduledAction.ElectionRound(ctx.now.plusDays(def.secondRoundGapDays.toLong()), SECOND_ROUND))
        } else {
            val first = state.pendingFirstRound ?: return
            val finalists = state.candidates.filter { it.characterId in topTwo(first) }
            val result = simulator.simulate(finalists, def.pollNoise / 2)
            val winner = result.shares.maxByOrNull { it.value }!!.key
            finish(first, result, winner, winner == incumbentId)
        }
    }

    private fun finish(first: RoundResult, second: RoundResult?, winnerId: String, incumbentWon: Boolean) {
        val state = ctx.state.elections
        val incumbentShare = second?.shares?.get(ctx.state.player.presidentId) ?: 0.0
        state.results += ElectionResult(
            ctx.now, first, second, winnerId, incumbentWon,
            ElectionSimulator(ctx).incumbentShareByDepartment(incumbentShare),
        )
        state.pendingFirstRound = null
        val winner = ctx.state.characters.getValue(winnerId)
        if (incumbentWon) {
            val player = ctx.state.player
            player.termNumber++
            player.termStart = ctx.now
            state.nextElection = ctx.now.plusYears(def.termYears)
            ctx.state.opinion.honeymoon = ctx.playerData.socialGroups!!.honeymoonBonus * REELECTION_HONEYMOON
            val president = ctx.state.characters.getValue(player.presidentId)
            ctx.notifications.post(NotificationCategory.ELECTIONS, Urgency.URGENT, if (president.female) "Vous êtes réélue !" else "Vous êtes réélu !",
                "Avec ${Formatting.percent(incumbentShare)} des voix, vous entamez votre mandat n°${player.termNumber}.")
            prepareCandidates()
            schedule()
        } else {
            ctx.state.player.gameOver = GameOver(ctx.now, "Battu par ${winner.fullName} à l'élection présidentielle.")
            ctx.notifications.post(NotificationCategory.ELECTIONS, Urgency.URGENT, "Défaite électorale",
                "${winner.fullName} remporte l'élection présidentielle. Votre présidence s'achève.")
        }
    }

    private fun topTwo(result: RoundResult): List<String> =
        result.shares.entries.sortedByDescending { it.value }.take(2).map { it.key }

    private companion object {
        const val FIRST_ROUND = 1
        const val SECOND_ROUND = 2
        const val CANDIDATE_SPREAD = 0.1
        const val REELECTION_HONEYMOON = 0.5
    }
}
