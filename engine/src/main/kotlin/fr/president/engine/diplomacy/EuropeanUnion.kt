package fr.president.engine.diplomacy

import fr.president.engine.effects.EffectSpec
import fr.president.engine.inbox.InboxMessage
import fr.president.engine.inbox.MessageOption
import fr.president.engine.inbox.MessageOrigin
import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import fr.president.engine.readout.Tone
import fr.president.engine.simulation.Cadence
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.simulation.SimulationSystem
import fr.president.engine.stats.JournalService
import fr.president.engine.time.WorldTime
import kotlinx.serialization.Serializable
import kotlin.math.exp

@Serializable
enum class EuRule(val label: String) {
    QMV("majorité qualifiée (55 % des États, 65 % de la population)"),
    UNANIMITY("unanimité (un seul veto suffit)"),
}

@Serializable
data class EuTextDef(
    val id: String,
    val title: String,
    val description: String,
    val rule: EuRule,
    /** Effets sur la France si le texte est adopté. */
    val effects: List<EffectSpec>,
    /** Position de départ de chaque État membre simulé (-1 contre, +1 pour). */
    val stances: Map<String, Double>,
    /** Position moyenne des États non simulés. */
    val others: Double = 0.0,
    /** Ce que le texte change pour la France, en une phrase. */
    val interest: String = "",
)

@Serializable
data class EuFile(
    val totalStates: Int = 27,
    val totalPopulation: Double = 449_000_000.0,
    val proposalIntervalDays: Double = 50.0,
    val voteDelayDays: Double = 30.0,
    val texts: List<EuTextDef>,
)

@Serializable
enum class EuVote(val label: String) { YES("Pour"), NO("Contre"), ABSTAIN("Abstention") }

@Serializable
class EuProcedure(
    val textId: String,
    val proposedAt: WorldTime,
    val voteAt: WorldTime,
    var francePosition: EuVote = EuVote.ABSTAIN,
    /** Pays rallié par la France (déplacement de sa position), une fois par pays. */
    val lobbied: MutableMap<String, Double> = mutableMapOf(),
    /** Amendement français : texte adouci, effets négatifs réduits. */
    var amended: Boolean = false,
)

@Serializable
data class EuResult(
    val textId: String,
    val time: WorldTime,
    val adopted: Boolean,
    val france: EuVote,
    val votes: Map<String, EuVote>,
    val amended: Boolean = false,
    val vetoBy: String? = null,
)

@Serializable
class EuState(
    var current: EuProcedure? = null,
    var nextProposal: WorldTime? = null,
    val used: MutableSet<String> = mutableSetOf(),
    val results: MutableList<EuResult> = mutableListOf(),
)

/**
 * L'Union européenne : la Commission propose régulièrement un texte, le Conseil le vote un mois
 * plus tard. La France prend position, rallie des partenaires, propose un amendement ou bloque
 * (unanimité). Chaque vote laisse des traces : ceux qui ont voté avec nous s'en souviennent,
 * ceux que nous avons bloqués aussi. Les États non simulés votent en bloc selon leur tendance.
 */
class EuSystem : SimulationSystem {
    override val name = "eu"
    override val cadence = Cadence.DAILY

    override fun run(ctx: SimulationContext) {
        val file = ctx.db.eu ?: return
        if (ctx.state.player.countryId !in EuService.members(ctx)) return
        val state = ctx.state.eu
        val service = EuService(ctx)
        val current = state.current
        when {
            current != null && ctx.now >= current.voteAt -> service.vote()
            current == null && ctx.now >= (state.nextProposal ?: ctx.now.plusDays(FIRST_PROPOSAL_DAYS).also { state.nextProposal = it }) -> service.propose(file)
        }
    }

    private companion object {
        const val FIRST_PROPOSAL_DAYS = 20.0
    }
}

class EuService(private val ctx: SimulationContext) {
    private val file get() = ctx.db.eu
    private val state get() = ctx.state.eu
    private val player get() = ctx.state.player.countryId
    private val relations = RelationCalculator(ctx)

    data class Forecast(val country: String, val name: String, val score: Double, val vote: EuVote, val lobbied: Boolean)
    data class Outlook(val forecasts: List<Forecast>, val othersYesShare: Double, val yesStates: Double, val yesPopulation: Double, val adopted: Boolean, val blockers: List<String>)

    fun text(id: String) = file?.texts?.firstOrNull { it.id == id }

    fun current(): Pair<EuProcedure, EuTextDef>? = state.current?.let { p -> text(p.textId)?.let { p to it } }

    fun propose(f: EuFile) {
        val pool = f.texts.filter { it.id !in state.used }.ifEmpty { state.used.clear(); f.texts }
        val t = ctx.rng.pick(pool)
        state.used += t.id
        val proc = EuProcedure(t.id, ctx.now, ctx.now.plusDays(f.voteDelayDays))
        state.current = proc
        val body = "La Commission européenne propose : ${t.title.lowercase()}.\n\n${t.description}\n\nPour la France : ${t.interest}\n\n" +
            "Règle de vote : ${t.rule.label}. Le Conseil votera dans ${f.voteDelayDays.toInt()} jours. Vous pouvez d'ici là rallier des partenaires ou proposer un amendement (panneau « Union européenne »)."
        val message = InboxMessage(
            id = ctx.state.newId("msg"), senderId = ctx.state.government.ministers["foreign"],
            senderLabel = "Représentation permanente à Bruxelles", subject = "Bruxelles : ${t.title}", body = body, time = ctx.now,
            category = NotificationCategory.DIPLOMACY, origin = MessageOrigin.EU, originId = t.id,
            options = listOf(
                MessageOption("YES", "La France soutiendra le texte", "Vous pourrez rallier des partenaires"),
                MessageOption("NO", "La France s'y opposera", if (t.rule == EuRule.UNANIMITY) "Un veto français suffit à le bloquer" else "Il faudra une minorité de blocage"),
                MessageOption("ABSTAIN", "La France s'abstiendra", "Pas d'engagement ; pas d'influence"),
            ),
            deadline = ctx.now.plusDays(POSITION_DAYS), defaultOptionId = "ABSTAIN",
        )
        ctx.state.inbox.messages += message
        ctx.notifications.post(NotificationCategory.DIPLOMACY, Urgency.IMPORTANT, "Bruxelles : ${t.title}", "Vote du Conseil dans ${f.voteDelayDays.toInt()} jours.", null, journal = false)
    }

    fun answer(message: InboxMessage, optionId: String, byDefault: Boolean) {
        message.chosenOptionId = optionId
        message.answeredByDefault = byDefault
        message.read = true
        val proc = state.current?.takeIf { it.textId == message.originId } ?: return
        proc.francePosition = EuVote.valueOf(optionId)
    }

    fun setPosition(vote: EuVote) {
        state.current?.francePosition = vote
        state.current?.let { p -> ctx.state.inbox.messages.firstOrNull { it.origin == MessageOrigin.EU && it.originId == p.textId && it.awaitingAnswer }?.let { it.chosenOptionId = vote.name; it.read = true } }
    }

    /** Raison pour laquelle on ne peut pas rallier ce pays, ou null. */
    fun lobbyBlocker(country: String): String? {
        val proc = state.current ?: return "Aucun texte en discussion."
        if (proc.francePosition == EuVote.ABSTAIN) return "Choisissez d'abord la position de la France."
        if (country in proc.lobbied) return "Déjà sollicité sur ce texte."
        return fr.president.engine.session.AgendaService(ctx).blocker(LOBBY_AGENDA)
    }

    /** Appeler un dirigeant pour le rallier : l'effet dépend de la relation avec lui. */
    fun lobby(country: String): Result<String> = runCatching {
        lobbyBlocker(country)?.let { error(it) }
        val proc = state.current!!
        val relation = relations.score(country, player)
        val shift = (LOBBY_BASE + LOBBY_RELATION * (relation - NEUTRAL)).coerceAtLeast(LOBBY_MIN)
        val sign = if (proc.francePosition == EuVote.YES) 1.0 else -1.0
        proc.lobbied[country] = shift * sign
        fr.president.engine.session.AgendaService(ctx).book("Appel : ${name(country)}", LOBBY_AGENDA)
        "${name(country)} : position infléchie" + if (relation < NEUTRAL) " (relation fraîche, effet limité)." else "."
    }

    fun amendBlocker(): String? {
        val proc = state.current ?: return "Aucun texte en discussion."
        if (proc.amended) return "La France a déjà amendé ce texte."
        if (ctx.now.daysUntil(proc.voteAt) < AMEND_MIN_DAYS) return "Trop tard pour amender."
        return null
    }

    /** Amendement : le texte est adouci (effets négatifs pour la France réduits), ses opposants s'en rapprochent. */
    fun amend(): Result<String> = runCatching {
        amendBlocker()?.let { error(it) }
        state.current!!.amended = true
        ctx.effects.trigger(EffectSpec("alliance.EU.NEGOTIATION_GOODWILL", AMEND_GOODWILL), null, emptyMap(), "eu")
        "Amendement français déposé : le texte est adouci, ses opposants s'en rapprochent."
    }

    /** Pronostic du vote, d'après les positions connues (sans le hasard du jour du vote). */
    fun outlook(): Outlook? {
        val (proc, t) = current() ?: return null
        val f = file!!
        val forecasts = members(ctx).filter { it != player }.map { c ->
            val s = score(c, t, proc)
            Forecast(c, name(c), s, voteOf(s), c in proc.lobbied)
        }
        val othersShare = othersYes(t, proc)
        return tally(f, t, proc, forecasts.associate { it.country to it.vote }, othersShare).let { (adopted, yesStates, yesPop, blockers) ->
            Outlook(forecasts.sortedByDescending { it.score }, othersShare, yesStates, yesPop, adopted, blockers)
        }
    }

    fun vote() {
        val (proc, t) = current() ?: run { state.current = null; return }
        val f = file!!
        val votes = members(ctx).filter { it != player }.associateWith { c -> voteOf(score(c, t, proc) + ctx.rng.nextGaussian() * NOISE) }
        val othersShare = (othersYes(t, proc) + ctx.rng.nextGaussian() * NOISE / 2).coerceIn(0.0, 1.0)
        val (adopted, _, _, blockers) = tally(f, t, proc, votes, othersShare)
        val result = EuResult(t.id, ctx.now, adopted, proc.francePosition, votes, proc.amended, blockers.firstOrNull())
        state.results += result
        if (state.results.size > MAX_RESULTS) state.results.removeAt(0)
        state.current = null
        state.nextProposal = ctx.now.plusDays(f.proposalIntervalDays * ctx.rng.nextDouble(INTERVAL_MIN, INTERVAL_MAX))
        consequences(t, proc, result)
    }

    private fun consequences(t: EuTextDef, proc: EuProcedure, r: EuResult) {
        if (r.adopted) {
            t.effects.forEach { e ->
                // L'amendement français réduit de moitié ce que la France y perd.
                val amount = if (proc.amended && isLoss(e)) e.amount * AMEND_FACTOR else e.amount
                ctx.effects.trigger(e.copy(amount = amount), null, emptyMap(), "eu:${t.id}")
            }
        }
        if (r.france != EuVote.ABSTAIN) {
            val won = (r.france == EuVote.YES) == r.adopted
            r.votes.forEach { (c, v) ->
                when {
                    v == r.france -> remember(c, "TALK_CORDIAL", SAME_VOTE)
                    v != EuVote.ABSTAIN -> remember(c, "DISAGREEMENT", -OPPOSITE_VOTE)
                }
            }
            ctx.effects.trigger(EffectSpec("alliance.EU.NEGOTIATION_GOODWILL", if (won) WIN_GOODWILL else -WIN_GOODWILL / 2), null, emptyMap(), "eu")
            // Veto français contre une large majorité : les partenaires le retiennent.
            if (t.rule == EuRule.UNANIMITY && r.vetoBy == player) {
                ctx.effects.trigger(EffectSpec("alliance.EU.DISAGREEMENT", -VETO_COST), null, emptyMap(), "eu")
                ctx.effects.trigger(EffectSpec("opinion.national", VETO_POPULARITY), null, emptyMap(), "eu")
            }
        }
        val yes = r.votes.values.count { it == EuVote.YES } + if (r.france == EuVote.YES) 1 else 0
        val no = r.votes.values.count { it == EuVote.NO } + if (r.france == EuVote.NO) 1 else 0
        val veto = r.vetoBy?.let { " (veto : ${name(it)})" } ?: ""
        val title = "Conseil de l'UE : « ${t.title} » ${if (r.adopted) "adopté" else "rejeté"}$veto"
        val france = if (r.france == EuVote.ABSTAIN) "La France s'est abstenue." else "La France a voté ${r.france.label.lowercase()}" + if (proc.amended) ", après avoir fait adopter son amendement." else "."
        ctx.notifications.post(NotificationCategory.DIPLOMACY, Urgency.IMPORTANT, title, "$france Parmi les États simulés : $yes pour, $no contre.", null, journal = false)
        JournalService(ctx).add("Europe", title, if ((r.france == EuVote.YES) == r.adopted && r.france != EuVote.ABSTAIN) Tone.GOOD else Tone.NEUTRAL)
    }

    /** Alliés et adversaires au Conseil : part des votes identiques à ceux de la France. */
    fun alignment(): List<Pair<String, Double>> {
        val relevant = state.results.filter { it.france != EuVote.ABSTAIN }
        if (relevant.isEmpty()) return emptyList()
        return members(ctx).filter { it != player }.map { c ->
            val same = relevant.count { it.votes[c] == it.france }
            name(c) to same.toDouble() / relevant.size
        }.sortedByDescending { it.second }
    }

    private fun score(c: String, t: EuTextDef, proc: EuProcedure): Double {
        val base = t.stances[c] ?: 0.0
        // Une France influente entraîne ses amis ; une relation tendue pousse à voter contre elle.
        val pull = when (proc.francePosition) {
            EuVote.YES -> 1.0
            EuVote.NO -> -1.0
            EuVote.ABSTAIN -> 0.0
        } * INFLUENCE * (relations.score(c, player) - NEUTRAL) * 2
        val amend = if (proc.amended && base < 0) AMEND_SHIFT else 0.0
        return base + pull + (proc.lobbied[c] ?: 0.0) + amend
    }

    private fun othersYes(t: EuTextDef, proc: EuProcedure): Double {
        val s = t.others + (if (proc.amended && t.others < 0) AMEND_SHIFT else 0.0) +
            (if (proc.francePosition == EuVote.YES) OTHERS_PULL else if (proc.francePosition == EuVote.NO) -OTHERS_PULL else 0.0)
        return 1 / (1 + exp(-s * SIGMOID))
    }

    private data class Tally(val adopted: Boolean, val yesStates: Double, val yesPopulation: Double, val blockers: List<String>)

    private fun tally(f: EuFile, t: EuTextDef, proc: EuProcedure, votes: Map<String, EuVote>, othersShare: Double): Tally {
        val all = votes + (player to proc.francePosition)
        val pop = { c: String -> ctx.db.countries[c]?.definition?.population?.toDouble() ?: 0.0 }
        val simulatedPop = all.keys.sumOf(pop)
        val othersStates = (f.totalStates - all.size).coerceAtLeast(0)
        val othersPop = (f.totalPopulation - simulatedPop).coerceAtLeast(0.0)
        val yesStates = all.values.count { it == EuVote.YES } + othersStates * othersShare
        val yesPop = all.filterValues { it == EuVote.YES }.keys.sumOf(pop) + othersPop * othersShare
        return when (t.rule) {
            EuRule.QMV -> Tally(yesStates >= f.totalStates * QMV_STATES && yesPop >= f.totalPopulation * QMV_POPULATION,
                yesStates / f.totalStates, yesPop / f.totalPopulation, emptyList())
            EuRule.UNANIMITY -> {
                // Le veto français est cité en premier : c'est lui que nos partenaires retiendront.
                val blockers = all.filterValues { it == EuVote.NO }.keys.sortedBy { if (it == player) 0 else 1 }.toMutableList()
                if (othersShare < OTHERS_VETO) blockers += OTHERS
                Tally(blockers.isEmpty(), yesStates / f.totalStates, yesPop / f.totalPopulation, blockers)
            }
        }
    }

    private fun voteOf(score: Double) = when {
        score > THRESHOLD -> EuVote.YES
        score < -THRESHOLD -> EuVote.NO
        else -> EuVote.ABSTAIN
    }

    /** Ce que la France perd dans un texte : une dépense, de l'inflation, un recul. */
    private fun isLoss(e: EffectSpec): Boolean = when {
        e.target == "budget.oneOff" || e.target == "economy.inflation" -> e.amount > 0
        e.target == "demography.immigration" -> false
        else -> e.amount < 0
    }

    private fun remember(country: String, kind: String, weight: Double) =
        ctx.effects.trigger(EffectSpec("memory.$country.$kind", weight), null, emptyMap(), "eu")

    fun name(c: String) = if (c == OTHERS) "les autres États membres" else ctx.db.countries[c]?.definition?.name ?: c

    companion object {
        const val OTHERS = "OTHERS"
        private const val POSITION_DAYS = 10.0
        private const val NEUTRAL = 0.5
        private const val INFLUENCE = 0.35
        private const val LOBBY_BASE = 0.25
        private const val LOBBY_RELATION = 0.6
        private const val LOBBY_MIN = 0.05
        private const val THRESHOLD = 0.15
        private const val NOISE = 0.12
        private const val SIGMOID = 3.0
        private const val OTHERS_PULL = 0.15
        private const val OTHERS_VETO = 0.25
        private const val QMV_STATES = 0.55
        private const val QMV_POPULATION = 0.65
        private const val AMEND_SHIFT = 0.2
        private const val AMEND_FACTOR = 0.5
        private const val AMEND_GOODWILL = 0.003
        private const val AMEND_MIN_DAYS = 3.0
        private const val SAME_VOTE = 0.015
        private const val OPPOSITE_VOTE = 0.01
        private const val WIN_GOODWILL = 0.006
        private const val VETO_COST = 0.02
        private const val VETO_POPULARITY = 0.002
        private const val MAX_RESULTS = 40
        private const val INTERVAL_MIN = 0.7
        private const val INTERVAL_MAX = 1.3
        private val LOBBY_AGENDA = fr.president.engine.session.AgendaCost(0.25)

        fun members(ctx: SimulationContext): List<String> = ctx.db.alliances.firstOrNull { it.id == "EU" }?.members.orEmpty()
    }
}
