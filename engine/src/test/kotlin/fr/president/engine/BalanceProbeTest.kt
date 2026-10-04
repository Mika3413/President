package fr.president.engine

import fr.president.engine.dialogue.ConversationTopic
import fr.president.engine.events.EventOptionDef
import fr.president.engine.inbox.InboxMessage
import fr.president.engine.inbox.InboxSystem
import fr.president.engine.inbox.MessageOrigin
import fr.president.engine.politics.CharacterRole
import fr.president.engine.session.GameSession
import kotlin.test.Test

/**
 * Sonde d'équilibrage : plusieurs joueurs automatiques aux stratégies différentes jouent un mandat
 * complet sur plusieurs graines. Lancée seulement avec -Dbalance=true (longue) ; imprime un tableau.
 */
class BalanceProbeTest {
    enum class Strategy { PASSIVE, REASONABLE, SPENDER, AUSTERE }

    @Test
    fun probe() {
        if (System.getProperty("balance") != "true") return
        val seeds = (System.getProperty("balance.seeds") ?: "1,2,3,4,5,6").split(',').map { it.trim().toLong() }
        val leaning = (System.getProperty("balance.leaning") ?: "0.0").toDouble()
        val only = System.getProperty("balance.strategies")?.split(',')?.map { Strategy.valueOf(it.trim()) }
        for (strategy in Strategy.entries.filter { only == null || it in only }) {
            var reelected = 0
            val lines = mutableListOf<String>()
            for (seed in seeds) {
                val clock = TestData.FakeClock()
                val s = GameSession.newGame(TestData.db, fr.president.engine.setup.NewGameOptions("normal", seed, clock.now, economicLeaning = leaning), clock)
                play(s, clock, strategy)
                val e = s.state.playerCountry.economy
                val won = s.state.elections.results.firstOrNull()?.incumbentWon == true
                if (won) reelected++
                val r = s.state.elections.results.firstOrNull()
                lines += "  seed=$seed gagné=$won 2nd tour=%s opinion=%.2f chômage=%.3f croissance=%.3f dette=%.2f déficit=%.3f soutien=%.2f réformes=%d".format(
                    r?.secondRound?.shares?.get(s.state.player.presidentId)?.let { "%.3f".format(it) } ?: "éliminé",
                    s.state.opinion.nationalApproval, e.unemployment, e.realGrowth, e.debtRatio, e.deficitRatio,
                    s.state.government.parliamentSupport, s.state.policy.adoptedReforms.size) +
                    " T1: " + (r?.firstRound?.shares?.entries?.sortedByDescending { it.value }?.take(4)?.joinToString { e ->
                        val fam = s.state.elections.candidates.firstOrNull { it.characterId == e.key }?.familyId ?: e.key
                        (if (e.key == s.state.player.presidentId) "*" else "") + fam + "=" + "%.2f".format(e.value) } ?: "") +
                    " scandales=" + s.state.characters.getValue(s.state.player.presidentId).scandals + " momentum=" + s.state.elections.candidates.joinToString { "%.2f".format(it.momentum) }
            }
            println("BALANCE $strategy : réélu $reelected / ${seeds.size}")
            lines.forEach { println("BALANCE $it") }
        }
    }

    private fun play(s: GameSession, clock: TestData.FakeClock, strategy: Strategy) {
        val target = s.state.elections.nextElection.plusDays(20)
        var day = 0
        while (s.state.time < target && !s.isGameOver) {
            clock.advanceWorldDays(5.0); day += 5
            s.advanceToNow()
            if (strategy == Strategy.PASSIVE) continue
            s.state.inbox.messages.filter { it.awaitingAnswer }.forEach { m -> answer(s, m, strategy) }
            if (strategy == Strategy.REASONABLE) reasonableExtras(s, day)
            if (strategy == Strategy.AUSTERE && day == 30) {
                s.context.playerData.economy.budget!!.spending.forEach { s.policy.proposeSpending(it.id, 0.95) }
            }
            if (strategy == Strategy.SPENDER && day == 30) {
                listOf("health", "education", "police").forEach { s.policy.proposeSpending(it, 1.1) }
            }
        }
    }

    private fun reasonableExtras(s: GameSession, day: Int) {
        // Réformes populaires et peu coûteuses, une à la fois.
        if (day % 120 == 0) {
            listOf("hospital_plan", "teachers_plan", "police_recruitment", "housing_plan", "renewables_plan")
                .firstOrNull { s.policy.reformBlocker(it) == null }?.let { s.policy.proposeReform(it) }
        }
        // Entretiens réguliers : partenaires et élus des grandes villes.
        if (day % 60 == 0) {
            s.state.countries.values.filter { it.id != s.state.player.countryId }.take(6).forEach { c ->
                if (s.conversations.blocker(c.leaderId) == null) s.conversations.talk(c.leaderId, ConversationTopic.STRENGTHEN)
            }
            s.state.characters.values.filter { it.role == CharacterRole.MAYOR && it.active }.shuffled(java.util.Random(day.toLong())).take(3).forEach { m ->
                if (s.conversations.blocker(m.id) == null) s.conversations.talk(m.id, ConversationTopic.VISIT)
            }
        }
        // Rejet au Parlement : passage en force seulement si la majorité est solide.
        s.state.policy.proposals.filter { it.status == fr.president.engine.government.PolicyStatus.REJECTED && it.supportAtVote != null && it.supportAtVote!! > 0.4 }
            .take(1).forEach { if (s.state.government.parliamentSupport > 0.55) s.policy.forcePass(it.id) }
    }

    private fun answer(s: GameSession, m: InboxMessage, strategy: Strategy) {
        val choice = when (m.origin) {
            MessageOrigin.EVENT -> eventChoice(s, m, strategy)
            MessageOrigin.PROPOSAL -> if (strategy == Strategy.AUSTERE) "refuse" else "accept"
            MessageOrigin.ALLIANCE_CALL -> "support"
            MessageOrigin.ULTIMATUM -> "back_down"
            else -> m.defaultOptionId
        } ?: m.defaultOptionId ?: return
        if (m.options.any { it.id == choice }) s.answer(m.id, choice) else m.defaultOptionId?.let { s.answer(m.id, it) }
    }

    private fun eventChoice(s: GameSession, m: InboxMessage, strategy: Strategy): String? {
        val instance = s.state.events.active.firstOrNull { it.id == m.originId } ?: return m.defaultOptionId
        val def = s.db.event(instance.definitionId)
        val options = def.message!!.options.filter { !it.requestDetails && it.reaskAfterDays == null }
        return when (strategy) {
            Strategy.SPENDER -> options.maxByOrNull { cost(it, instance.params) }?.id
            Strategy.AUSTERE -> options.minByOrNull { cost(it, instance.params) }?.id
            else -> options.maxByOrNull { score(it, instance.params) }?.id
        }
    }

    /** Coût budgétaire estimé (milliards). */
    private fun cost(o: EventOptionDef, params: Map<String, Double>): Double {
        val oneOff = o.effects.filter { it.target == "budget.oneOff" }.sumOf { (it.param?.let { p -> params[p] } ?: it.amount) * it.factor }
        val project = o.project?.let { (params[it.costParam] ?: 0.0) * it.costFactor } ?: 0.0
        val spending = o.effects.filter { it.target.startsWith("spending.") }.sumOf { it.amount * SPENDING_BILLIONS }
        return oneOff + project + spending
    }

    /** Heuristique d'un joueur raisonnable : bénéfice politique moins coût budgétaire. */
    private fun score(o: EventOptionDef, params: Map<String, Double>): Double {
        val opinion = o.effects.filter { it.target.startsWith("opinion.") || it.target.endsWith(".approval") }.sumOf { it.amount }
        val quality = o.effects.filter { it.target.startsWith("quality.") }.sumOf { it.amount } +
            (o.project?.onCompletion?.filter { it.target.startsWith("quality.") || it.target.endsWith("approval") }?.sumOf { it.amount } ?: 0.0)
        val relation = o.effects.filter { it.target.endsWith(".relation") }.sumOf { it.amount }
        return opinion * OPINION_WEIGHT + quality * QUALITY_WEIGHT + relation * RELATION_WEIGHT - cost(o, params) * COST_WEIGHT
    }

    private companion object {
        const val SPENDING_BILLIONS = 30.0
        const val OPINION_WEIGHT = 100.0
        const val QUALITY_WEIGHT = 200.0
        const val RELATION_WEIGHT = 5.0
        const val COST_WEIGHT = 2.5
    }
}
